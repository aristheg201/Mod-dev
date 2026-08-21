package vn.svframe.lively.ai;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import vn.svframe.lively.actor.ActorId;
import vn.svframe.lively.api.LivelyApi;
import vn.svframe.lively.dialogue.DialogueService;
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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Connects cognition/schedules/social state to physical NPCs while keeping worker decisions immutable.
 * Large populations are processed in bounded staggered batches instead of full-registry scans every tick.
 */
public final class NpcAutonomyService implements AutoCloseable {
    private static final int DECISIONS_PER_PULSE = 10;
    private static final int ACTOR_SYNC_PER_PULSE = 256;
    private static final int NEEDS_PER_PULSE = 256;
    private static final int SCHEDULES_PER_PULSE = 128;
    private static final int SOCIAL_CANDIDATES_PER_PULSE = 128;
    private static final int SOCIAL_INTERACTIONS_PER_PULSE = 32;
    private static final int MAX_OBSERVED_ENTITIES = 64;
    private static final long DEFINITION_REFRESH_TICKS = 100L;
    private static final int SOCIAL_CELL = 8;

    private final NpcRuntime npcs;
    private final NpcStateRegistry states;
    private final WorldNavigationService navigation;
    private final LivelyAiEngine engine = new LivelyAiEngine();
    private final ConcurrentHashMap<UUID, Long> socialCooldown = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> lastNeedTick = new ConcurrentHashMap<>();

    private AiScheduler scheduler;
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
            runDecisions(server);
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
                    Set.of("npc", d.bodyType().name().toLowerCase(java.util.Locale.ROOT)));
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
        }
        needCursor = advance(needCursor, count, definitions.size());
    }

    private void runDecisions(MinecraftServer server) {
        ensureScheduler(server);
        List<NpcDefinition> active = activeDefinitions();
        if (active.isEmpty()) return;
        int attempts = Math.min(active.size(), DECISIONS_PER_PULSE * 3);
        int submitted = 0;
        for (int i = 0; i < attempts && submitted < DECISIONS_PER_PULSE; i++) {
            NpcDefinition d = active.get((decisionCursor + i) % active.size());
            NpcState state = states.get(d.id()).orElse(null);
            if (state == null) continue;
            NpcSnapshot npc = state.snapshot(32);
            WorldSnapshot world = captureWorld(server, d);
            AiScheduler.Submission submission = scheduler.submit(new AiScheduler.TaskKey(d.id(), "cognition"), AiScheduler.Priority.NORMAL,
                    npc.revision(), state::revision,
                    () -> engine.decide(npc, world).orElse(null),
                    decision -> { if (decision != null) applyDecision(server, d.id(), decision.action()); });
            if (submission.accepted()) submitted++;
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
        while (examined < Math.min(SOCIAL_CANDIDATES_PER_PULSE, active.size()) && interactions < SOCIAL_INTERACTIONS_PER_PULSE) {
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

    private WorldSnapshot captureWorld(MinecraftServer server, NpcDefinition d) {
        Vec3d p = npcs.position(d.id()).orElse(new Vec3d(d.x(), d.y(), d.z()));
        String worldKey = npcs.worldKey(d.id()).orElse(d.world());
        List<WorldSnapshot.ObservedEntity> entities = new ArrayList<>();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (entities.size() >= MAX_OBSERVED_ENTITIES) break;
            if (player.getServerWorld().getRegistryKey().getValue().toString().equals(worldKey)
                    && player.getPos().squaredDistanceTo(p) <= 32D * 32D) {
                entities.add(new WorldSnapshot.ObservedEntity(player.getUuid(), "player", 0D));
            }
        }
        for (NpcDefinition other : definitions) {
            if (entities.size() >= MAX_OBSERVED_ENTITIES) break;
            if (other.id().equals(d.id()) || !other.spawned()) continue;
            String otherWorld = npcs.worldKey(other.id()).orElse(other.world());
            if (!worldKey.equals(otherWorld)) continue;
            if (npcs.position(other.id()).map(q -> q.squaredDistanceTo(p) <= 24D * 24D).orElse(false)) {
                entities.add(new WorldSnapshot.ObservedEntity(other.id(), "npc", 0D));
            }
        }
        return new WorldSnapshot(System.nanoTime(), worldKey, server.getTicks(), entities, Map.of());
    }

    private void applyDecision(MinecraftServer server, UUID npcId, AiAction action) {
        NpcDefinition d = npcs.get(npcId).orElse(null);
        if (d == null) return;
        switch (action.type()) {
            case "travel_home" -> {
                String home = d.metadata().get("home.structure");
                if (home != null) navigation.goToStructure(npcId, home);
            }
            case "perform_occupation" -> {
                String work = d.metadata().get("work.structure");
                if (work != null) navigation.goToStructure(npcId, work);
            }
            case "seek_food" -> nearestStructure(d, "restaurant", "shop", "market")
                    .ifPresent(id -> navigation.goToStructure(npcId, id));
            case "start_dialogue" -> startNearbyDialogue(server, d);
            case "consume_food" -> states.get(npcId).ifPresent(state ->
                    state.setNeed("hunger", Math.max(0D, state.snapshot(1).need("hunger") - .20D)));
            default -> states.get(npcId).ifPresent(state ->
                    state.remember("ai_decision", Map.of("action", action.type()), .08D, 1D));
        }
    }

    private java.util.Optional<String> nearestStructure(NpcDefinition d, String... types) {
        Vec3d p = npcs.position(d.id()).orElse(new Vec3d(d.x(), d.y(), d.z()));
        Set<String> wanted = Set.of(types);
        String worldKey = npcs.worldKey(d.id()).orElse(d.world());
        return LivelyApi.structures().snapshot().structures().values().stream()
                .filter(structure -> structure.bounds().world().equals(worldKey)
                        && wanted.contains(structure.type().toLowerCase(java.util.Locale.ROOT)))
                .min(Comparator.comparingDouble(structure -> center(structure.bounds()).squaredDistanceTo(p)))
                .map(SemanticStructureRegistry.Structure::id);
    }

    private void startNearbyDialogue(MinecraftServer server, NpcDefinition d) {
        if (!Boolean.parseBoolean(d.metadata().getOrDefault("dialogue.auto", "false"))) return;
        DialogueService dialogues = LivelyApi.dialogues();
        if (dialogues == null) return;
        Vec3d p = npcs.position(d.id()).orElse(null);
        String worldKey = npcs.worldKey(d.id()).orElse(d.world());
        if (p == null) return;
        server.getPlayerManager().getPlayerList().stream()
                .filter(player -> player.getServerWorld().getRegistryKey().getValue().toString().equals(worldKey)
                        && player.getPos().squaredDistanceTo(p) <= 16D
                        && dialogues.session(player.getUuid()).isEmpty())
                .findFirst().ifPresent(player -> dialogues.start(player, d.id(), d.name(), d.role()));
    }

    private List<NpcDefinition> activeDefinitions() {
        return definitions.stream().filter(NpcDefinition::spawned).filter(NpcDefinition::aiEnabled).toList();
    }

    private static Vec3d center(SemanticStructureRegistry.Bounds bounds) {
        return new Vec3d((bounds.minX() + bounds.maxX() + 1) / 2D, bounds.minY() + 1D,
                (bounds.minZ() + bounds.maxZ() + 1) / 2D);
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
        if (scheduler == null) scheduler = new AiScheduler(
                Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() / 2)), 1024, server::execute);
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

    @Override
    public void close() {
        if (scheduler != null) scheduler.close();
        socialCooldown.clear();
        lastNeedTick.clear();
        definitions = List.of();
    }
}
