package vn.svframe.lively.ai;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import vn.svframe.lively.actor.ActorId;
import vn.svframe.lively.api.LivelyApi;
import vn.svframe.lively.config.RuntimeConfigService;
import vn.svframe.lively.dialogue.DialogueService;
import vn.svframe.lively.economy.BusinessAccessPolicy;
import vn.svframe.lively.economy.EconomyEngine;
import vn.svframe.lively.event.WorldEventEngine;
import vn.svframe.lively.model.NpcSnapshot;
import vn.svframe.lively.model.NpcState;
import vn.svframe.lively.model.WorldSnapshot;
import vn.svframe.lively.navigation.WorldNavigationService;
import vn.svframe.lively.npc.NpcDefinition;
import vn.svframe.lively.npc.NpcRuntime;
import vn.svframe.lively.persistence.NpcStateRegistry;
import vn.svframe.lively.schedule.ScheduleEngine;
import vn.svframe.lively.social.SocialEngine;
import vn.svframe.lively.world.SemanticStructureRegistry;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Connects cognition/schedules/social state to physical NPCs while keeping worker decisions immutable.
 * Large populations are processed in bounded staggered batches instead of full-registry scans every tick.
 */
public final class NpcAutonomyService implements AutoCloseable {
    private static final int ACTOR_SYNC_PER_PULSE = 256;
    private static final int NEEDS_PER_PULSE = 256;
    private static final int SCHEDULES_PER_PULSE = 128;
    private static final int SOCIAL_CANDIDATES_PER_PULSE = 128;
    private static final long DEFINITION_REFRESH_TICKS = 100L;
    private static final int SOCIAL_CELL = 8;

    private final NpcRuntime npcs;
    private final NpcStateRegistry states;
    private final WorldNavigationService navigation;
    private final LivelyAiEngine engine = new LivelyAiEngine();
    private final ConcurrentHashMap<UUID, Long> socialCooldown = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> lastNeedTick = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> nextDecisionTick = new ConcurrentHashMap<>();

    private AiScheduler scheduler;
    private int schedulerMaxPending;
    private List<NpcDefinition> definitions = List.of();
    private long definitionsAt = Long.MIN_VALUE;
    private int decisionCursor;
    private int actorCursor;
    private int needCursor;
    private int scheduleCursor;
    private int socialCursor;

    public NpcAutonomyService(NpcRuntime npcs, NpcStateRegistry states, WorldNavigationService navigation) {
        this.npcs = npcs;
        this.states = states;
        this.navigation = navigation;
    }

    public void tick(MinecraftServer server, long tick) {
        refreshDefinitions(tick);
        if (definitions.isEmpty()) return;

        if (tick % 20L == 0L) {
            syncActors();
            applySchedules(server);
            simulateNeeds(tick);
            runDecisions(server, tick);
        }
        if (tick % 80L == 0L) socialPulse(tick);
    }

    private void refreshDefinitions(long tick) {
        if (tick - definitionsAt < DEFINITION_REFRESH_TICKS && !definitions.isEmpty()) return;
        definitions = npcs.snapshot().values().stream()
                .sorted(Comparator.comparing(d -> d.id().toString()))
                .toList();
        definitionsAt = tick;
        actorCursor = normalize(actorCursor, definitions.size());
        needCursor = normalize(needCursor, definitions.size());
        scheduleCursor = normalize(scheduleCursor, definitions.size());
        socialCursor = normalize(socialCursor, definitions.size());
        decisionCursor = normalize(decisionCursor, Math.max(1, activeDefinitions().size()));
        if (nextDecisionTick.size() > definitions.size() * 2 + 128) {
            Set<UUID> live = definitions.stream().map(NpcDefinition::id).collect(java.util.stream.Collectors.toSet());
            nextDecisionTick.keySet().removeIf(id -> !live.contains(id));
        }
    }

    private void syncActors() {
        int count = Math.min(ACTOR_SYNC_PER_PULSE, definitions.size());
        for (int i = 0; i < count; i++) {
            NpcDefinition d = definitions.get((actorCursor + i) % definitions.size());
            NpcState state = states.get(d.id()).orElse(null);
            if (state == null) continue;
            NpcSnapshot snapshot = state.snapshot(1);
            Map<String, Double> social = new HashMap<>(snapshot.traits());
            social.putAll(snapshot.needs());
            LivelyApi.actors().upsert(new ActorId(d.id(), ActorId.Kind.NPC), d.name(), social,
                    Map.of("role", d.role(), "world", d.world()),
                    Set.of("npc", d.bodyType().name().toLowerCase(Locale.ROOT)));
        }
        actorCursor = advance(actorCursor, count, definitions.size());
    }

    private void applySchedules(MinecraftServer server) {
        int processed = 0;
        int examined = 0;
        while (processed < SCHEDULES_PER_PULSE && examined < definitions.size()) {
            NpcDefinition d = definitions.get((scheduleCursor + examined) % definitions.size());
            examined++;
            if (!d.spawned() || !d.aiEnabled()) continue;
            processed++;
            String worldKey = npcs.worldKey(d.id()).orElse(d.world());
            ServerWorld world = world(server, worldKey);
            if (world == null) continue;
            int minute = minuteOfDay(world.getTimeOfDay());
            ActorId actor = new ActorId(d.id(), ActorId.Kind.NPC);
            ScheduleEngine.ScheduleEntry entry = LivelyApi.schedules().current(actor, minute).orElse(null);
            if (entry == null || entry.semanticLocation() == null || entry.semanticLocation().isBlank()) continue;
            if (navigation.status(d.id()).isEmpty()) {
                navigation.goToStructure(d.id(), entry.semanticLocation());
                states.get(d.id()).ifPresent(state -> state.remember("schedule_activity",
                        Map.of("activity", entry.activity(), "location", entry.semanticLocation()), .18D, 1D));
            }
        }
        scheduleCursor = advance(scheduleCursor, examined, definitions.size());
    }

    private void simulateNeeds(long tick) {
        int count = Math.min(NEEDS_PER_PULSE, definitions.size());
        for (int i = 0; i < count; i++) {
            NpcDefinition d = definitions.get((needCursor + i) % definitions.size());
            NpcState state = states.get(d.id()).orElse(null);
            if (state == null) continue;
            long previous = lastNeedTick.getOrDefault(d.id(), tick - 40L);
            long elapsed = Math.max(1L, Math.min(2400L, tick - previous));
            lastNeedTick.put(d.id(), tick);
            double scale = elapsed / 40D;
            NpcSnapshot snapshot = state.snapshot(1);
            state.setNeed("hunger", clamp01(snapshot.need("hunger") + .0025D * scale));
            state.setNeed("fatigue", clamp01(snapshot.need("fatigue") + .0015D * scale));
            state.setNeed("social", clamp01(snapshot.need("social") + .001D * scale));
            state.setNeed("money", clamp01(snapshot.need("money") + .00035D * scale));
        }
        needCursor = advance(needCursor, count, definitions.size());
    }

    private void runDecisions(MinecraftServer server, long tick) {
        ensureScheduler(server);
        List<NpcDefinition> active = activeDefinitions();
        if (active.isEmpty()) return;
        int decisionBudget = config().aiDecisionsPerPulse();
        int attempts = Math.min(active.size(), decisionBudget * 4);
        int submitted = 0;
        for (int i = 0; i < attempts && submitted < decisionBudget; i++) {
            NpcDefinition d = active.get((decisionCursor + i) % active.size());
            if (nextDecisionTick.getOrDefault(d.id(), 0L) > tick) continue;
            NpcState state = states.get(d.id()).orElse(null);
            if (state == null) continue;
            NpcSnapshot npc = state.snapshot(32);
            WorldSnapshot world = captureWorld(server, d, npc);
            AiScheduler.Submission submission = scheduler.submit(new AiScheduler.TaskKey(d.id(), "cognition"), AiScheduler.Priority.NORMAL,
                    npc.revision(), state::revision,
                    () -> engine.decide(npc, world).orElse(null),
                    decision -> {
                        if (decision == null) return;
                        Instant startedAt = Instant.now();
                        boolean success = applyDecision(server, d.id(), decision);
                        long nowTick = server.getTicks();
                        nextDecisionTick.merge(d.id(), nowTick + actionCooldown(decision.action().type()), Math::max);
                        recordActionOutcome(d.id(), decision, success, startedAt);
                    });
            if (submission.accepted()) {
                nextDecisionTick.put(d.id(), tick + 20L);
                submitted++;
            }
        }
        decisionCursor = advance(decisionCursor, Math.max(1, attempts), active.size());
    }

    /** Uses a small spatial hash so routine social contact is O(n) per sparse pulse rather than O(n²). */
    private void socialPulse(long tick) {
        List<NpcDefinition> active = activeDefinitions();
        if (active.size() < 2) return;
        Map<Cell, List<SpatialNpc>> cells = new HashMap<>();
        for (NpcDefinition d : active) {
            Vec3d pos = npcs.position(d.id()).orElse(null);
            String world = npcs.worldKey(d.id()).orElse(d.world());
            if (pos == null || world == null) continue;
            cells.computeIfAbsent(Cell.of(world, pos), ignored -> new ArrayList<>()).add(new SpatialNpc(d, pos, world));
        }

        int examined = 0;
        int interactions = 0;
        int interactionBudget = config().socialInteractionsPerPulse();
        while (examined < Math.min(SOCIAL_CANDIDATES_PER_PULSE, active.size()) && interactions < interactionBudget) {
            NpcDefinition a = active.get((socialCursor + examined) % active.size());
            examined++;
            if (socialCooldown.getOrDefault(a.id(), 0L) > tick) continue;
            Vec3d pa = npcs.position(a.id()).orElse(null);
            String world = npcs.worldKey(a.id()).orElse(a.world());
            if (pa == null || world == null) continue;
            NpcDefinition b = nearestSocialNeighbor(a, pa, world, cells, tick);
            if (b == null) continue;
            NpcSnapshot sa = states.snapshot(a.id()).orElse(null);
            NpcSnapshot sb = states.snapshot(b.id()).orElse(null);
            if (sa == null || sb == null) continue;
            double friendliness = (sa.trait("friendly") + sb.trait("friendly")) / 2D;
            ActorId aa = new ActorId(a.id(), ActorId.Kind.NPC);
            ActorId bb = new ActorId(b.id(), ActorId.Kind.NPC);
            LivelyApi.social().apply(aa, bb, new SocialEngine.SocialDelta(
                    .006D + .006D * friendliness, .004D + .008D * friendliness, .002D,
                    0D, .001D, 0D, .015D, "routine_social_contact", Map.of()));
            states.get(a.id()).ifPresent(state -> {
                state.setNeed("social", Math.max(0D, state.snapshot(1).need("social") - .08D));
                state.remember("npc_socialized", Map.of("with", b.id().toString()), .16D, 1D);
            });
            states.get(b.id()).ifPresent(state -> state.remember("npc_socialized", Map.of("with", a.id().toString()), .16D, 1D));
            socialCooldown.put(a.id(), tick + 200L);
            socialCooldown.put(b.id(), tick + 200L);
            interactions++;
        }
        socialCursor = advance(socialCursor, examined, active.size());
    }

    private NpcDefinition nearestSocialNeighbor(NpcDefinition a, Vec3d pa, String world,
                                                Map<Cell, List<SpatialNpc>> cells, long tick) {
        Cell center = Cell.of(world, pa);
        NpcDefinition best = null;
        double bestDistance = 16.0001D;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (SpatialNpc candidate : cells.getOrDefault(new Cell(world, center.x() + dx, center.z() + dz), List.of())) {
                    NpcDefinition other = candidate.definition();
                    if (other.id().equals(a.id()) || socialCooldown.getOrDefault(other.id(), 0L) > tick) continue;
                    double distance = candidate.position().squaredDistanceTo(pa);
                    if (distance <= 16D && distance < bestDistance) {
                        bestDistance = distance;
                        best = other;
                    }
                }
            }
        }
        return best;
    }

    private WorldSnapshot captureWorld(MinecraftServer server, NpcDefinition d, NpcSnapshot npc) {
        Vec3d position = npcs.position(d.id()).orElse(new Vec3d(d.x(), d.y(), d.z()));
        String worldKey = npcs.worldKey(d.id()).orElse(d.world());
        ActorId self = new ActorId(d.id(), ActorId.Kind.NPC);
        List<WorldSnapshot.ObservedEntity> entities = new ArrayList<>();
        int maxObserved = config().maxObservedEntities();

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (entities.size() >= maxObserved) break;
            if (!player.getServerWorld().getRegistryKey().getValue().toString().equals(worldKey)
                    || player.getPos().squaredDistanceTo(position) > 32D * 32D) continue;
            ActorId observed = new ActorId(player.getUuid(), ActorId.Kind.PLAYER);
            entities.add(new WorldSnapshot.ObservedEntity(player.getUuid(), "player", perceivedThreat(npc, self, observed)));
        }
        for (NpcDefinition other : definitions) {
            if (entities.size() >= maxObserved) break;
            if (other.id().equals(d.id()) || !other.spawned()) continue;
            String otherWorld = npcs.worldKey(other.id()).orElse(other.world());
            if (!worldKey.equals(otherWorld)) continue;
            if (npcs.position(other.id()).map(q -> q.squaredDistanceTo(position) <= 24D * 24D).orElse(false)) {
                ActorId observed = new ActorId(other.id(), ActorId.Kind.NPC);
                entities.add(new WorldSnapshot.ObservedEntity(other.id(), "npc", perceivedThreat(npc, self, observed)));
            }
        }

        double environmentThreat = environmentThreat(d, worldKey, position);
        return new WorldSnapshot(System.nanoTime(), worldKey, server.getTicks(), entities,
                Map.of("environment_threat", environmentThreat));
    }

    private double perceivedThreat(NpcSnapshot npc, ActorId self, ActorId observed) {
        NpcSnapshot.RelationshipView local = npc.relationship(observed.uuid());
        SocialEngine.Relationship social = LivelyApi.social().findRelationship(self, observed).orElse(null);
        double reputation = LivelyApi.social().reputation(observed, SocialEngine.ReputationScope.GLOBAL, "");
        double hostility = social == null ? 0D : social.hostility();
        double typeBonus = social == null ? 0D : switch (social.type()) {
            case ENEMY -> .18D;
            case RIVAL -> .08D;
            default -> 0D;
        };
        return clamp01(local.fear() * .34D + local.suspicion() * .18D + Math.max(0D, -local.trust()) * .12D
                + hostility * .24D + Math.max(0D, -reputation) * .08D + typeBonus);
    }

    private double environmentThreat(NpcDefinition d, String worldKey, Vec3d position) {
        double threat = 0D;
        for (WorldEventEngine.WorldEvent event : LivelyApi.events().activeEvents()) {
            double category = switch (event.category()) {
                case DISASTER -> 1D;
                case CRIME, FACTION_CONFLICT -> .78D;
                case POLITICAL -> .48D;
                case MYSTERY -> .36D;
                default -> .20D;
            };
            boolean relevant = event.participants().contains(new ActorId(d.id(), ActorId.Kind.NPC));
            if (!relevant && event.structureId() != null) {
                SemanticStructureRegistry.Structure structure = LivelyApi.structures().get(event.structureId()).orElse(null);
                relevant = structure != null && structure.bounds().world().equals(worldKey)
                        && inside(structure.bounds(), position);
            }
            if (relevant) threat = Math.max(threat, clamp01(event.intensity() * category));
        }
        return threat;
    }

    private boolean applyDecision(MinecraftServer server, UUID npcId, Decision decision) {
        NpcDefinition d = npcs.get(npcId).orElse(null);
        if (d == null) return false;
        return switch (decision.action().type()) {
            case "travel_home" -> goToMetadataStructure(d, "home.structure");
            case "perform_occupation" -> performOccupation(d);
            case "seek_food" -> seekFood(d);
            case "start_dialogue" -> startNearbyDialogue(server, d);
            case "consume_food" -> consumeFood(d);
            case "flee" -> flee(server, d);
            case "defend" -> defend(server, d);
            case "offer_trade" -> offerTrade(server, d);
            default -> {
                states.get(npcId).ifPresent(state -> state.remember("ai_decision",
                        Map.of("action", decision.action().type()), .08D, 1D));
                yield false;
            }
        };
    }

    private boolean performOccupation(NpcDefinition d) {
        String work = d.metadata().get("work.structure");
        if (work == null || work.isBlank()) return false;
        boolean accepted = navigation.goToStructure(d.id(), work);
        if (accepted) {
            states.get(d.id()).ifPresent(state -> state.setNeed("money",
                    Math.max(0D, state.snapshot(1).need("money") - .015D)));
        }
        return accepted;
    }

    private boolean goToMetadataStructure(NpcDefinition d, String key) {
        String structure = d.metadata().get(key);
        return structure != null && !structure.isBlank() && navigation.goToStructure(d.id(), structure);
    }

    private boolean startNearbyDialogue(MinecraftServer server, NpcDefinition d) {
        DialogueService dialogues = LivelyApi.dialogues();
        if (dialogues == null) return false;
        Vec3d p = npcs.position(d.id()).orElse(null);
        String worldKey = npcs.worldKey(d.id()).orElse(d.world());
        if (p == null) return false;
        ServerPlayerEntity player = server.getPlayerManager().getPlayerList().stream()
                .filter(candidate -> candidate.getServerWorld().getRegistryKey().getValue().toString().equals(worldKey)
                        && candidate.getPos().squaredDistanceTo(p) <= 16D
                        && dialogues.session(candidate.getUuid()).isEmpty())
                .findFirst().orElse(null);
        if (player == null) return false;
        dialogues.start(player, d.id(), d.name(), d.role());
        states.get(d.id()).ifPresent(state -> state.remember("dialogue_started",
                Map.of("player", player.getUuid().toString()), .14D, 1D));
        return true;
    }

    private boolean consumeFood(NpcDefinition d) {
        Vec3d position = npcs.position(d.id()).orElse(null);
        String worldKey = npcs.worldKey(d.id()).orElse(d.world());
        if (position == null) return false;
        boolean foodAvailable = LivelyApi.structures().at(worldKey, position.x, position.y, position.z).stream().anyMatch(structure -> {
            String type = structure.type().toLowerCase(Locale.ROOT);
            return Set.of("restaurant", "shop", "market", "inn", "home").contains(type)
                    || structure.capabilities().contains("cook") || structure.capabilities().contains("trade");
        });
        if (!foodAvailable) {
            seekFood(d);
            return false;
        }
        NpcState state = states.get(d.id()).orElse(null);
        if (state == null) return false;
        double before = state.snapshot(1).need("hunger");
        double after = Math.max(0D, before - .20D);
        state.setNeed("hunger", after);
        state.remember("semantic_meal", Map.of("world", worldKey), .12D, 1D);
        return after < before;
    }

    private boolean seekFood(NpcDefinition d) {
        Optional<String> structure = nearestStructure(d, "restaurant", "shop", "market", "inn");
        return structure.filter(id -> navigation.goToStructure(d.id(), id)).isPresent();
    }

    private boolean flee(MinecraftServer server, NpcDefinition d) {
        ThreatObservation threat = highestThreat(server, d).orElse(null);
        Vec3d origin = threat == null ? dangerousEventOrigin(d).orElse(null) : threat.position();
        String worldKey = npcs.worldKey(d.id()).orElse(d.world());
        if (origin == null) {
            String home = d.metadata().get("home.structure");
            return home != null && navigation.goToStructure(d.id(), home);
        }
        double strength = threat == null ? environmentThreat(d, worldKey, npcs.position(d.id()).orElse(origin)) : threat.threat();
        double distance = 10D + strength * 14D;
        boolean accepted = navigation.flee(d.id(), worldKey, origin, distance);
        if (accepted) {
            states.get(d.id()).ifPresent(state -> state.remember("fled_from_threat",
                    Map.of("source", threat == null ? "world_event" : threat.actor().uuid().toString(),
                            "threat", Double.toString(strength)), .48D, 1D));
        }
        return accepted;
    }

    private boolean defend(MinecraftServer server, NpcDefinition d) {
        ThreatObservation threat = highestThreat(server, d).orElse(null);
        if (threat == null) return false;
        navigation.stop(d.id());
        boolean facing = npcs.lookAt(server, d.id(), threat.position());
        if (facing) {
            states.get(d.id()).ifPresent(state -> state.remember("defensive_stance",
                    Map.of("against", threat.actor().uuid().toString(), "threat", Double.toString(threat.threat())), .42D, 1D));
        }
        return facing;
    }

    private boolean offerTrade(MinecraftServer server, NpcDefinition d) {
        ActorId owner = new ActorId(d.id(), ActorId.Kind.NPC);
        List<EconomyEngine.Business> businesses = LivelyApi.economy().businessesByOwner(owner).stream()
                .filter(EconomyEngine.Business::open).toList();
        if (businesses.isEmpty()) return false;
        Vec3d position = npcs.position(d.id()).orElse(null);
        String worldKey = npcs.worldKey(d.id()).orElse(d.world());
        DialogueService dialogues = LivelyApi.dialogues();
        NpcState state = states.get(d.id()).orElse(null);
        if (position == null || dialogues == null || state == null) return false;

        ServerPlayerEntity player = server.getPlayerManager().getPlayerList().stream()
                .filter(candidate -> candidate.getServerWorld().getRegistryKey().getValue().toString().equals(worldKey))
                .filter(candidate -> candidate.getPos().squaredDistanceTo(position) <= 25D)
                .filter(candidate -> dialogues.session(candidate.getUuid()).isEmpty())
                .filter(candidate -> businesses.stream().anyMatch(business -> canOfferBusiness(state, candidate, business)))
                .findFirst().orElse(null);
        if (player == null) return false;
        dialogues.start(player, d.id(), d.name(), d.role());
        state.setNeed("money", Math.max(0D, state.snapshot(1).need("money") - .06D));
        state.remember("trade_offered", Map.of("player", player.getUuid().toString()), .16D, 1D);
        return true;
    }

    private boolean canOfferBusiness(NpcState state, ServerPlayerEntity player, EconomyEngine.Business business) {
        double trust = state.snapshot(1).relationship(player.getUuid()).trust();
        ActorId playerActor = new ActorId(player.getUuid(), ActorId.Kind.PLAYER);
        double reputation = LivelyApi.social().reputation(playerActor, SocialEngine.ReputationScope.GLOBAL, "");
        return BusinessAccessPolicy.evaluate(business, trust, reputation).allowed();
    }

    private void recordActionOutcome(UUID npcId, Decision decision, boolean success, Instant startedAt) {
        states.get(npcId).ifPresent(state -> state.remember("action_outcome",
                Map.of("action", decision.action().type(),
                        "goal", decision.goal().type(),
                        "success", Boolean.toString(success),
                        "score", Double.toString(decision.score()),
                        "started_at", startedAt.toString()),
                success ? .20D : .30D, 1D));
    }

    private static long actionCooldown(String action) {
        return switch (action) {
            case "flee", "defend" -> 20L;
            case "travel_home", "perform_occupation", "seek_food" -> 40L;
            case "consume_food" -> 60L;
            case "start_dialogue", "offer_trade" -> 100L;
            default -> 40L;
        };
    }

    private Optional<ThreatObservation> highestThreat(MinecraftServer server, NpcDefinition d) {
        NpcSnapshot npc = states.snapshot(d.id()).orElse(null);
        Vec3d position = npcs.position(d.id()).orElse(null);
        String worldKey = npcs.worldKey(d.id()).orElse(d.world());
        if (npc == null || position == null) return Optional.empty();
        ActorId self = new ActorId(d.id(), ActorId.Kind.NPC);
        ThreatObservation best = null;

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (!player.getServerWorld().getRegistryKey().getValue().toString().equals(worldKey)
                    || player.getPos().squaredDistanceTo(position) > 32D * 32D) continue;
            ActorId actor = new ActorId(player.getUuid(), ActorId.Kind.PLAYER);
            double threat = perceivedThreat(npc, self, actor);
            if (best == null || threat > best.threat()) best = new ThreatObservation(actor, player.getPos(), threat);
        }
        for (NpcDefinition other : definitions) {
            if (other.id().equals(d.id()) || !other.spawned()) continue;
            String otherWorld = npcs.worldKey(other.id()).orElse(other.world());
            Vec3d otherPos = npcs.position(other.id()).orElse(null);
            if (!worldKey.equals(otherWorld) || otherPos == null || otherPos.squaredDistanceTo(position) > 24D * 24D) continue;
            ActorId actor = new ActorId(other.id(), ActorId.Kind.NPC);
            double threat = perceivedThreat(npc, self, actor);
            if (best == null || threat > best.threat()) best = new ThreatObservation(actor, otherPos, threat);
        }
        return best == null || best.threat() < .35D ? Optional.empty() : Optional.of(best);
    }

    private Optional<Vec3d> dangerousEventOrigin(NpcDefinition d) {
        Vec3d position = npcs.position(d.id()).orElse(null);
        String worldKey = npcs.worldKey(d.id()).orElse(d.world());
        if (position == null) return Optional.empty();
        return LivelyApi.events().activeEvents().stream()
                .filter(event -> event.intensity() >= .45D && event.structureId() != null)
                .map(event -> LivelyApi.structures().get(event.structureId()).orElse(null))
                .filter(java.util.Objects::nonNull)
                .filter(structure -> structure.bounds().world().equals(worldKey) && inside(structure.bounds(), position))
                .min(Comparator.comparingDouble(structure -> center(structure.bounds()).squaredDistanceTo(position)))
                .map(structure -> center(structure.bounds()));
    }

    private Optional<String> nearestStructure(NpcDefinition d, String... types) {
        Vec3d p = npcs.position(d.id()).orElse(new Vec3d(d.x(), d.y(), d.z()));
        Set<String> wanted = Set.of(types);
        String worldKey = npcs.worldKey(d.id()).orElse(d.world());
        return LivelyApi.structures().snapshot().structures().values().stream()
                .filter(structure -> structure.bounds().world().equals(worldKey)
                        && wanted.contains(structure.type().toLowerCase(Locale.ROOT)))
                .min(Comparator.comparingDouble(structure -> center(structure.bounds()).squaredDistanceTo(p)))
                .map(SemanticStructureRegistry.Structure::id);
    }

    private List<NpcDefinition> activeDefinitions() {
        return definitions.stream().filter(NpcDefinition::spawned).filter(NpcDefinition::aiEnabled).toList();
    }

    private static Vec3d center(SemanticStructureRegistry.Bounds bounds) {
        return new Vec3d((bounds.minX() + bounds.maxX() + 1D) / 2D, bounds.minY() + 1D,
                (bounds.minZ() + bounds.maxZ() + 1D) / 2D);
    }

    private static boolean inside(SemanticStructureRegistry.Bounds bounds, Vec3d position) {
        return position.x >= bounds.minX() && position.x <= bounds.maxX() + 1D
                && position.y >= bounds.minY() && position.y <= bounds.maxY() + 1D
                && position.z >= bounds.minZ() && position.z <= bounds.maxZ() + 1D;
    }

    private static int minuteOfDay(long time) {
        long ticks = Math.floorMod(time + 6000L, 24000L);
        return (int) (ticks * 1440L / 24000L);
    }

    private static ServerWorld world(MinecraftServer server, String key) {
        net.minecraft.util.Identifier id = net.minecraft.util.Identifier.tryParse(key);
        return id == null ? null : server.getWorld(net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.WORLD, id));
    }

    private void ensureScheduler(MinecraftServer server) {
        int desiredMaxPending = config().aiMaxPending();
        if (scheduler != null && schedulerMaxPending != desiredMaxPending && scheduler.pendingCount() == 0) {
            scheduler.close();
            scheduler = null;
        }
        if (scheduler == null) {
            scheduler = new AiScheduler(Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() / 2)),
                    desiredMaxPending, server::execute);
            schedulerMaxPending = desiredMaxPending;
        }
    }

    private static RuntimeConfigService.Config config() {
        RuntimeConfigService service = LivelyApi.runtimeConfig();
        return service == null ? RuntimeConfigService.defaults() : service.current();
    }

    private static int advance(int cursor, int amount, int size) {
        return size <= 0 ? 0 : Math.floorMod(cursor + Math.max(0, amount), size);
    }

    private static int normalize(int cursor, int size) { return size <= 0 ? 0 : Math.floorMod(cursor, size); }
    private static double clamp01(double value) { return Math.max(0D, Math.min(1D, value)); }

    private record Cell(String world, int x, int z) {
        static Cell of(String world, Vec3d position) {
            return new Cell(world, Math.floorDiv((int) Math.floor(position.x), SOCIAL_CELL),
                    Math.floorDiv((int) Math.floor(position.z), SOCIAL_CELL));
        }
    }
    private record SpatialNpc(NpcDefinition definition, Vec3d position, String world) {}
    private record ThreatObservation(ActorId actor, Vec3d position, double threat) {}

    @Override
    public void close() {
        if (scheduler != null) scheduler.close();
        scheduler = null;
        schedulerMaxPending = 0;
        socialCooldown.clear();
        lastNeedTick.clear();
        nextDecisionTick.clear();
        definitions = List.of();
    }
}
