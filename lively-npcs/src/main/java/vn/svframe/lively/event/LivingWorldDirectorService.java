package vn.svframe.lively.event;

import vn.svframe.lively.actor.ActorId;
import vn.svframe.lively.api.LivelyApi;
import vn.svframe.lively.config.RuntimeConfigService;
import vn.svframe.lively.crime.CrimeEngine;
import vn.svframe.lively.npc.NpcDefinition;
import vn.svframe.lively.quest.QuestRuntime;
import vn.svframe.lively.world.SemanticStructureRegistry;

import java.time.Duration;
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
import java.util.stream.Collectors;

/** Bounded causal story loop. Listener ownership is scoped to one MinecraftServer session. */
public final class LivingWorldDirectorService implements AutoCloseable {
    private static final ActorId SYSTEM = new ActorId(new UUID(0L, 1L), ActorId.Kind.SYSTEM);
    private final WorldEventEngine.Listener listener = new WorldEventEngine.Listener() {
        @Override public void onStarted(WorldEventEngine.WorldEvent event) { onEventStarted(event); }
        @Override public void onFinished(WorldEventEngine.WorldEvent event) { onEventFinished(event); }
        @Override public void onCancelled(WorldEventEngine.WorldEvent event) { onEventCancelled(event); }
    };
    private long lastPulse;

    public LivingWorldDirectorService() {
        installDefaultSeeds();
        LivelyApi.events().addListener(listener);
    }

    public void tick(long tick) {
        RuntimeConfigService.Config config = config();
        if (tick - lastPulse < config.storyPulseTicks()) return;
        lastPulse = tick;
        List<WorldEventEngine.WorldEvent> active = LivelyApi.events().activeEvents();
        if (active.size() >= config.storyMaxActiveEvents()) return;

        Map<String, Double> signals = signals();
        Set<ActorId> actors = LivelyApi.actors().snapshot().actors().keySet();
        Set<ActorId> npcs = actors.stream().filter(actor -> actor.kind() == ActorId.Kind.NPC).limit(32).collect(Collectors.toSet());
        int slots = Math.max(0, Math.min(config.storyMaxNewEventsPerPulse(), config.storyMaxActiveEvents() - active.size()));
        if (slots <= 0) return;

        int started = 0;
        List<WorldEventEngine.EventProposal> proposals = LivelyApi.storySeeds().propose(signals, null, npcs, 16).stream()
                .filter(proposal -> config.storyCategoryEnabled(proposal.category()))
                .map(proposal -> applyTone(config.storyTone(), proposal))
                .filter(proposal -> proposal.intensity() >= .28D)
                .sorted(Comparator.comparingDouble(WorldEventEngine.EventProposal::intensity).reversed())
                .toList();
        for (WorldEventEngine.EventProposal proposal : proposals) {
            if (LivelyApi.events().activeEvents().stream().anyMatch(event -> event.seed().equals(proposal.seed()))) continue;
            if (LivelyApi.events().start(proposal).isPresent() && ++started >= slots) break;
        }
        if (started < slots && config.storyCategoryEnabled(WorldEventEngine.Category.FACTION_CONFLICT)
                && LivelyApi.events().activeEvents().size() < config.storyMaxActiveEvents()) {
            emergeAntagonist(signals, actors, config.storyTone());
        }
    }

    static WorldEventEngine.EventProposal applyTone(String tone, WorldEventEngine.EventProposal proposal) {
        double intensity = clamp01(proposal.intensity() * toneMultiplier(tone, proposal.category()));
        Map<String, String> facts = new HashMap<>(proposal.facts());
        facts.put("story_tone", tone);
        return new WorldEventEngine.EventProposal(proposal.category(), proposal.seed(), proposal.structureId(),
                proposal.participants(), intensity, proposal.duration(), facts);
    }

    static double toneMultiplier(String tone, WorldEventEngine.Category category) {
        return switch (tone) {
            case "peaceful" -> switch (category) {
                case FESTIVAL, SOCIAL, DISCOVERY, MIGRATION -> 1.28D;
                case CRIME, FACTION_CONFLICT, DISASTER, POLITICAL -> .58D;
                default -> .86D;
            };
            case "adventure" -> switch (category) {
                case DISCOVERY, MYSTERY, MIGRATION, FACTION_CONFLICT -> 1.24D;
                case FESTIVAL, SOCIAL -> .92D;
                default -> 1.02D;
            };
            case "dramatic" -> switch (category) {
                case CRIME, FACTION_CONFLICT, POLITICAL, MYSTERY, DISASTER -> 1.28D;
                default -> .92D;
            };
            case "dark" -> switch (category) {
                case CRIME, FACTION_CONFLICT, MYSTERY, DISASTER -> 1.38D;
                case FESTIVAL, SOCIAL -> .60D;
                case DISCOVERY -> .82D;
                default -> 1.08D;
            };
            default -> 1D;
        };
    }

    private static RuntimeConfigService.Config config() {
        RuntimeConfigService service = LivelyApi.runtimeConfig();
        return service == null ? RuntimeConfigService.defaults() : service.current();
    }

    private void onEventStarted(WorldEventEngine.WorldEvent event) {
        StoryArcEngine.Arc arc = LivelyApi.storyArcs().active().stream().filter(value -> value.seed().equals(event.seed())).findFirst()
                .orElseGet(() -> LivelyApi.storyArcs().start(event.seed(), title(event), 5,
                        Map.of("category", event.category().name(), "started_by", event.id().toString(),
                                "tone", event.facts().getOrDefault("story_tone", "balanced"))));
        LivelyApi.storyArcs().attachEvent(arc.id(), event.id(), event.intensity() * .25D);
        if (event.intensity() >= .42D && LivelyApi.quests().snapshot().quests().values().stream()
                .noneMatch(quest -> event.id().toString().equals(quest.facts().get("event")))) createQuest(event);
    }

    private void onEventFinished(WorldEventEngine.WorldEvent event) {
        arcForEvent(event).ifPresent(arc -> {
            double tensionDelta = event.intensity() >= .72D ? .08D : event.intensity() >= .45D ? .02D : -.04D;
            LivelyApi.storyArcs().advance(arc.id(), tensionDelta);
        });
    }

    private void onEventCancelled(WorldEventEngine.WorldEvent event) {
        arcForEvent(event).ifPresent(arc -> LivelyApi.storyArcs().state(arc.id(), StoryArcEngine.State.ABANDONED));
    }

    private Optional<StoryArcEngine.Arc> arcForEvent(WorldEventEngine.WorldEvent event) {
        return LivelyApi.storyArcs().snapshot().values().stream()
                .filter(arc -> arc.events().contains(event.id()))
                .max(Comparator.comparing(StoryArcEngine.Arc::updatedAt));
    }

    private void createQuest(WorldEventEngine.WorldEvent event) {
        ActorId issuer = event.participants().stream().filter(actor -> actor.kind() == ActorId.Kind.NPC)
                .sorted(Comparator.comparing(actor -> actor.uuid().toString())).findFirst().orElse(SYSTEM);
        QuestRuntime.ObjectiveType type = switch (event.category()) {
            case CRIME, MYSTERY -> QuestRuntime.ObjectiveType.INVESTIGATION;
            case MIGRATION, DISCOVERY -> QuestRuntime.ObjectiveType.EXPLORATION;
            case FACTION_CONFLICT, FESTIVAL, SOCIAL, POLITICAL -> QuestRuntime.ObjectiveType.SOCIAL;
            case ECONOMIC -> QuestRuntime.ObjectiveType.DELIVERY;
            case DISASTER -> QuestRuntime.ObjectiveType.ESCORT;
        };

        Map<String, String> objectiveFacts = new HashMap<>();
        objectiveFacts.put("event", event.id().toString());
        objectiveFacts.putAll(destinationFacts(event, issuer));
        String target = switch (type) {
            case INVESTIGATION -> event.id().toString();
            case SOCIAL -> {
                if (issuer.kind() == ActorId.Kind.NPC) {
                    objectiveFacts.put("actor", issuer.uuid().toString());
                    objectiveFacts.put("npc", issuer.uuid().toString());
                    yield issuer.uuid().toString();
                }
                yield event.id().toString();
            }
            case DELIVERY -> {
                objectiveFacts.put("semantic_delivery", "true");
                yield objectiveFacts.getOrDefault("structure", event.id().toString());
            }
            case EXPLORATION, ESCORT -> objectiveFacts.getOrDefault("structure", event.id().toString());
            default -> event.id().toString();
        };

        List<QuestRuntime.Objective> objectives = new ArrayList<>();
        objectives.add(new QuestRuntime.Objective("main", type, target, 1, false, false, objectiveFacts));
        if (event.intensity() > .72D) {
            objectives.add(new QuestRuntime.Objective("optional_context", QuestRuntime.ObjectiveType.INVESTIGATION,
                    event.id().toString(), 1, true, true,
                    Map.of("event", event.id().toString(), "discover", "cause")));
        }

        Duration ttl = Duration.between(Instant.now(), event.expiresAt());
        if (ttl.isNegative() || ttl.isZero()) ttl = Duration.ofMinutes(10);
        long reward = Math.max(100L, Math.min(100000L, Math.round(500D + event.intensity() * 4500D)));
        LivelyApi.quests().create(issuer, null, questTitle(event), objectives, ttl,
                Map.of("event", event.id().toString(), "seed", event.seed(), "reward_budget", Long.toString(reward),
                        "story_tone", event.facts().getOrDefault("story_tone", "balanced"), "public", "true"));
    }

    private Map<String, String> destinationFacts(WorldEventEngine.WorldEvent event, ActorId issuer) {
        Map<String, String> facts = new HashMap<>();
        if (event.structureId() != null && LivelyApi.structures().get(event.structureId()).isPresent()) {
            facts.put("structure", event.structureId());
            return facts;
        }
        copyCoordinateFacts(event.facts(), facts);
        if (facts.containsKey("world") && facts.containsKey("x") && facts.containsKey("y") && facts.containsKey("z")) return facts;

        if (issuer.kind() == ActorId.Kind.NPC && LivelyApi.npcs() != null) {
            NpcDefinition definition = LivelyApi.npcs().get(issuer.uuid()).orElse(null);
            if (definition != null) {
                String authored = firstExistingStructure(definition.metadata().get("work.structure"), definition.metadata().get("home.structure"));
                if (authored != null) {
                    facts.put("structure", authored);
                    return facts;
                }
                var position = LivelyApi.npcs().position(issuer.uuid()).orElse(null);
                String world = LivelyApi.npcs().worldKey(issuer.uuid()).orElse(definition.world());
                if (position != null && world != null) {
                    facts.put("world", world);
                    facts.put("x", Double.toString(position.x));
                    facts.put("y", Double.toString(position.y));
                    facts.put("z", Double.toString(position.z));
                    facts.put("radius", "6");
                }
            }
        }
        return facts;
    }

    private String firstExistingStructure(String... ids) {
        for (String id : ids) if (id != null && !id.isBlank() && LivelyApi.structures().get(id).isPresent()) return id;
        return null;
    }

    private static void copyCoordinateFacts(Map<String, String> source, Map<String, String> target) {
        for (String key : List.of("world", "x", "y", "z", "radius")) {
            String value = source.get(key);
            if (value != null && !value.isBlank()) target.put(key, value);
        }
    }

    private void emergeAntagonist(Map<String, Double> signals, Set<ActorId> actors, String tone) {
        if ("peaceful".equals(tone)) return;
        if (LivelyApi.events().activeEvents().stream().anyMatch(event -> "villain_emergence".equals(event.facts().get("kind")))) return;
        var candidates = LivelyApi.storySeeds().antagonistCandidates(LivelyApi.actors(),
                actors.stream().filter(actor -> actor.kind() == ActorId.Kind.NPC).collect(Collectors.toSet()), signals);
        double threshold = "dark".equals(tone) ? .58D : "dramatic".equals(tone) ? .63D : .68D;
        if (candidates.isEmpty() || candidates.getFirst().score() < threshold) return;
        var top = candidates.getFirst();
        LivelyApi.events().start(new WorldEventEngine.EventProposal(WorldEventEngine.Category.FACTION_CONFLICT, "villain_emergence", null,
                Set.of(top.actor()), clamp01(top.score() * toneMultiplier(tone, WorldEventEngine.Category.FACTION_CONFLICT)), Duration.ofHours(6),
                Map.of("kind", "villain_emergence", "candidate", top.actor().uuid().toString(), "reason", top.reason(), "story_tone", tone)));
    }

    private Map<String, Double> signals() {
        var crimeSnapshot = LivelyApi.crime().snapshot();
        double openCrime = crimeSnapshot.crimes().values().stream()
                .filter(value -> value.status() == CrimeEngine.Status.OPEN || value.status() == CrimeEngine.Status.INVESTIGATING
                        || value.status() == CrimeEngine.Status.CHARGED).count();

        var economy = LivelyApi.economy().snapshot();
        var stocks = economy.stocks().values();
        double scarcity = stocks.isEmpty() ? 0D : stocks.stream()
                .mapToDouble(stock -> Math.max(0D, 1D - stock.quantity() / (double) Math.max(1L, stock.targetQuantity())))
                .average().orElse(0D);
        double illegalBusiness = Math.min(1D, economy.businesses().values().stream()
                .filter(business -> Boolean.parseBoolean(business.facts().getOrDefault("illegal", "false"))).count() / 4D);

        var factionRelations = LivelyApi.factions().snapshot().relations().values();
        double factionConflict = factionRelations.isEmpty() ? 0D : factionRelations.stream()
                .mapToDouble(value -> value.hostility()).average().orElse(0D);

        var social = LivelyApi.social().snapshot();
        double rumors = Math.min(1D, social.rumors().size() / 20D);
        double underworldRumors = Math.min(1D, social.rumors().values().stream()
                .filter(rumor -> rumor.topic().toLowerCase(Locale.ROOT).contains("underworld")
                        || rumor.topic().toLowerCase(Locale.ROOT).startsWith("crime:"))
                .count() / 8D);
        double socialHostility = social.relationships().isEmpty() ? 0D : social.relationships().values().stream()
                .mapToDouble(value -> value.hostility()).average().orElse(0D);

        var actorSnapshot = LivelyApi.actors().snapshot();
        double creatureDensity = Math.min(1D, actorSnapshot.actors().keySet().stream()
                .filter(actor -> actor.kind() == ActorId.Kind.CREATURE).count() / 24D);

        var structures = LivelyApi.structures().snapshot().structures().values();
        double ancientSites = Math.min(1D, structures.stream().filter(structure -> {
            String structureType = structure.type().toLowerCase(Locale.ROOT);
            return structureType.contains("ancient") || structureType.contains("ruin") || structureType.contains("temple")
                    || structureType.contains("shrine") || structureType.contains("archaeolog") || structureType.contains("fossil");
        }).count() / 3D);

        double crimeSignal = Math.min(1D, openCrime / 8D);
        double underworld = clamp01(openCrime / 12D + illegalBusiness * .46D + underworldRumors * .28D
                + socialHostility * .16D + scarcity * .10D);
        double mystery = clamp01(openCrime / 16D + rumors * .20D + ancientSites * .48D + underworldRumors * .12D);
        double political = clamp01(factionConflict * .62D + socialHostility * .18D + rumors * .20D);
        double migration = clamp01(creatureDensity * .78D + scarcity * .08D + rumors * .06D);

        return Map.of(
                "crime", crimeSignal,
                "criminal_underworld", underworld,
                "mystery", mystery,
                "ancient_mystery", clamp01(mystery * .72D + ancientSites * .28D),
                "economic_crisis", scarcity,
                "economic", scarcity,
                "faction_conflict", factionConflict,
                "political_tension", political,
                "pokemon_migration", migration,
                "migration", migration);
    }

    private void installDefaultSeeds() {
        if (!LivelyApi.storySeeds().snapshot().isEmpty()) return;
        LivelyApi.storySeeds().register(new StorySeedEngine.Seed("criminal_underworld", WorldEventEngine.Category.CRIME, .65D, true, Map.of()));
        LivelyApi.storySeeds().register(new StorySeedEngine.Seed("mystery", WorldEventEngine.Category.MYSTERY, .55D, true, Map.of()));
        LivelyApi.storySeeds().register(new StorySeedEngine.Seed("ancient_mystery", WorldEventEngine.Category.MYSTERY, .48D, true, Map.of("source", "ancient_site")));
        LivelyApi.storySeeds().register(new StorySeedEngine.Seed("pokemon_migration", WorldEventEngine.Category.MIGRATION, .50D, true, Map.of("semantic_only", "true")));
        LivelyApi.storySeeds().register(new StorySeedEngine.Seed("economic_crisis", WorldEventEngine.Category.ECONOMIC, .50D, true, Map.of()));
        LivelyApi.storySeeds().register(new StorySeedEngine.Seed("faction_conflict", WorldEventEngine.Category.FACTION_CONFLICT, .60D, true, Map.of()));
        LivelyApi.storySeeds().register(new StorySeedEngine.Seed("political_tension", WorldEventEngine.Category.POLITICAL, .45D, true, Map.of()));
    }

    @Override public void close() { LivelyApi.events().removeListener(listener); }
    private static double clamp01(double value) { return Math.max(0D, Math.min(1D, value)); }
    private static String title(WorldEventEngine.WorldEvent event) { return "World arc: " + event.seed(); }
    private static String questTitle(WorldEventEngine.WorldEvent event) {
        return switch (event.category()) {
            case CRIME -> "Điều tra: " + event.seed();
            case MYSTERY -> "Làm rõ bí ẩn: " + event.seed();
            case ECONOMIC -> "Nguồn cung bất ổn: " + event.seed();
            case FACTION_CONFLICT -> "Xung đột: " + event.seed();
            case MIGRATION -> "Theo dấu di cư: " + event.seed();
            case DISASTER -> "Ứng phó: " + event.seed();
            default -> "Tìm hiểu: " + event.seed();
        };
    }
}
