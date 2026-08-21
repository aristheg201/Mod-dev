package vn.svframe.lively.event;

import vn.svframe.lively.actor.ActorId;
import vn.svframe.lively.api.LivelyApi;
import vn.svframe.lively.crime.CrimeEngine;
import vn.svframe.lively.quest.QuestRuntime;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Bounded causal story loop. Listener ownership is scoped to one MinecraftServer session. */
public final class LivingWorldDirectorService implements AutoCloseable {
    private static final ActorId SYSTEM = new ActorId(new UUID(0L, 1L), ActorId.Kind.SYSTEM);
    private final WorldEventEngine.Listener listener = new WorldEventEngine.Listener() {
        @Override public void onStarted(WorldEventEngine.WorldEvent event) { onEventStarted(event); }
    };
    private long lastPulse;

    public LivingWorldDirectorService() {
        installDefaultSeeds();
        LivelyApi.events().addListener(listener);
    }

    public void tick(long tick) {
        if (tick - lastPulse < 1200L) return;
        lastPulse = tick;
        Map<String, Double> signals = signals();
        Set<ActorId> actors = LivelyApi.actors().snapshot().actors().keySet();
        Set<ActorId> npcs = actors.stream().filter(actor -> actor.kind() == ActorId.Kind.NPC).limit(32).collect(Collectors.toSet());
        for (WorldEventEngine.EventProposal proposal : LivelyApi.storySeeds().propose(signals, null, npcs, 2)) {
            if (LivelyApi.events().activeEvents().stream().noneMatch(event -> event.seed().equals(proposal.seed()))) {
                LivelyApi.events().start(proposal);
            }
        }
        emergeAntagonist(signals, actors);
    }

    private void onEventStarted(WorldEventEngine.WorldEvent event) {
        StoryArcEngine.Arc arc = LivelyApi.storyArcs().active().stream().filter(value -> value.seed().equals(event.seed())).findFirst()
                .orElseGet(() -> LivelyApi.storyArcs().start(event.seed(), title(event), 5, Map.of("category", event.category().name())));
        LivelyApi.storyArcs().attachEvent(arc.id(), event.id(), event.intensity() * .25D);
        if (event.intensity() >= .42D && LivelyApi.quests().snapshot().quests().values().stream()
                .noneMatch(quest -> event.id().toString().equals(quest.facts().get("event")))) createQuest(event);
    }

    private void createQuest(WorldEventEngine.WorldEvent event) {
        QuestRuntime.ObjectiveType type = switch (event.category()) {
            case CRIME, MYSTERY -> QuestRuntime.ObjectiveType.INVESTIGATION;
            case MIGRATION, DISCOVERY -> QuestRuntime.ObjectiveType.EXPLORATION;
            case FACTION_CONFLICT, FESTIVAL, SOCIAL, POLITICAL -> QuestRuntime.ObjectiveType.SOCIAL;
            case ECONOMIC -> QuestRuntime.ObjectiveType.DELIVERY;
            case DISASTER -> QuestRuntime.ObjectiveType.ESCORT;
        };
        String target = event.structureId() != null ? event.structureId() : event.seed();
        List<QuestRuntime.Objective> objectives = new ArrayList<>();
        objectives.add(new QuestRuntime.Objective("main", type, target, 1, false, false, Map.of("event", event.id().toString())));
        if (event.intensity() > .72D) objectives.add(new QuestRuntime.Objective("optional_context", QuestRuntime.ObjectiveType.INVESTIGATION,
                event.seed(), 1, true, true, Map.of("discover", "cause")));
        ActorId issuer = event.participants().stream().filter(actor -> actor.kind() == ActorId.Kind.NPC).findFirst().orElse(SYSTEM);
        Duration ttl = Duration.between(Instant.now(), event.expiresAt());
        if (ttl.isNegative() || ttl.isZero()) ttl = Duration.ofMinutes(10);
        long reward = Math.max(100L, Math.min(100000L, Math.round(500D + event.intensity() * 4500D)));
        LivelyApi.quests().create(issuer, null, questTitle(event), objectives, ttl,
                Map.of("event", event.id().toString(), "seed", event.seed(), "reward_budget", Long.toString(reward), "public", "true"));
    }

    private void emergeAntagonist(Map<String, Double> signals, Set<ActorId> actors) {
        if (LivelyApi.events().activeEvents().stream().anyMatch(event -> "villain_emergence".equals(event.facts().get("kind")))) return;
        var candidates = LivelyApi.storySeeds().antagonistCandidates(LivelyApi.actors(),
                actors.stream().filter(actor -> actor.kind() == ActorId.Kind.NPC).collect(Collectors.toSet()), signals);
        if (candidates.isEmpty() || candidates.getFirst().score() < .68D) return;
        var top = candidates.getFirst();
        LivelyApi.events().start(new WorldEventEngine.EventProposal(WorldEventEngine.Category.FACTION_CONFLICT, "villain_emergence", null,
                Set.of(top.actor()), top.score(), Duration.ofHours(6),
                Map.of("kind", "villain_emergence", "candidate", top.actor().uuid().toString(), "reason", top.reason())));
    }

    /**
     * Signals are derived from independent state sources so the world can create new tensions rather than
     * requiring a pre-existing event of the same category. This avoids the old crime-needs-crime feedback loop.
     */
    private Map<String, Double> signals() {
        var crimeSnapshot = LivelyApi.crime().snapshot();
        double openCrime = crimeSnapshot.crimes().values().stream()
                .filter(value -> value.status() == CrimeEngine.Status.OPEN || value.status() == CrimeEngine.Status.INVESTIGATING).count();

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
            String type = structure.type().toLowerCase(Locale.ROOT);
            return type.contains("ancient") || type.contains("ruin") || type.contains("temple")
                    || type.contains("shrine") || type.contains("archaeolog") || type.contains("fossil");
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
