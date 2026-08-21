package vn.svframe.lively.event;

import vn.svframe.lively.actor.ActorId;
import vn.svframe.lively.world.SemanticStructureRegistry;
import vn.svframe.lively.world.WorldMutationPolicy;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Persistent causal event state. It changes semantic world state, never terrain directly. */
public final class WorldEventEngine {
    public enum Category { CRIME, SOCIAL, ECONOMIC, FACTION_CONFLICT, FESTIVAL, DISASTER, MYSTERY, MIGRATION, POLITICAL, DISCOVERY }
    public enum Phase { PROPOSED, ACTIVE, RESOLVING, FINISHED, CANCELLED }

    public record EventProposal(
            Category category, String seed, String structureId, Set<ActorId> participants,
            double intensity, Duration duration, Map<String, String> facts
    ) {
        public EventProposal {
            Objects.requireNonNull(category); Objects.requireNonNull(seed); Objects.requireNonNull(duration);
            participants = Set.copyOf(participants); facts = Map.copyOf(facts);
            intensity = Math.max(0D, Math.min(1D, intensity));
        }
    }

    public record WorldEvent(
            UUID id, Category category, String seed, String structureId, Set<ActorId> participants,
            double intensity, Instant startedAt, Instant expiresAt, Phase phase, Map<String, String> facts
    ) {
        public WorldEvent {
            Objects.requireNonNull(id); Objects.requireNonNull(category); Objects.requireNonNull(seed);
            Objects.requireNonNull(startedAt); Objects.requireNonNull(expiresAt); Objects.requireNonNull(phase);
            participants = Set.copyOf(participants); facts = Map.copyOf(facts);
        }
        public boolean expired(Instant now) { return !expiresAt.isAfter(now); }
    }

    private final ConcurrentHashMap<UUID, WorldEvent> active = new ConcurrentHashMap<>();
    private final List<WorldEvent> history = java.util.Collections.synchronizedList(new ArrayList<>());
    private final SemanticStructureRegistry structures;
    private final WorldMutationPolicy mutationPolicy;
    private final int maxActive;

    public WorldEventEngine(SemanticStructureRegistry structures, WorldMutationPolicy mutationPolicy, int maxActive) {
        this.structures = Objects.requireNonNull(structures);
        this.mutationPolicy = Objects.requireNonNull(mutationPolicy);
        this.maxActive = Math.max(1, Math.min(10_000, maxActive));
    }

    public Validation validate(EventProposal proposal) {
        Objects.requireNonNull(proposal);
        if (active.size() >= maxActive) return Validation.deny("active_event_limit");
        if (proposal.seed().isBlank() || proposal.seed().length() > 128) return Validation.deny("invalid_seed");
        if (proposal.duration().isNegative() || proposal.duration().isZero() || proposal.duration().compareTo(Duration.ofDays(30)) > 0) return Validation.deny("invalid_duration");
        if (proposal.participants().size() > 256) return Validation.deny("too_many_participants");
        if (proposal.facts().size() > 128) return Validation.deny("too_many_facts");
        if (proposal.structureId() != null && structures.get(proposal.structureId()).isEmpty()) return Validation.deny("unknown_structure");
        return Validation.allow();
    }

    public Optional<WorldEvent> start(EventProposal proposal) {
        Validation validation = validate(proposal);
        if (!validation.allowed()) return Optional.empty();
        WorldMutationPolicy.Decision mutation = mutationPolicy.evaluate(new WorldMutationPolicy.Proposal(
                null, WorldMutationPolicy.Source.SYSTEM, WorldMutationPolicy.MutationClass.SEMANTIC,
                WorldMutationPolicy.ActionKind.EVENT_STATE, proposal.structureId(), null, Map.of("seed", proposal.seed())));
        if (!mutation.allowed()) return Optional.empty();
        Instant now = Instant.now();
        WorldEvent event = new WorldEvent(UUID.randomUUID(), proposal.category(), proposal.seed(), proposal.structureId(), proposal.participants(),
                proposal.intensity(), now, now.plus(proposal.duration()), Phase.ACTIVE, proposal.facts());
        active.put(event.id(), event);
        return Optional.of(event);
    }

    public int advance(Instant now) {
        int finished = 0;
        for (WorldEvent event : List.copyOf(active.values())) {
            if (!event.expired(now)) continue;
            WorldEvent completed = new WorldEvent(event.id(), event.category(), event.seed(), event.structureId(), event.participants(),
                    event.intensity(), event.startedAt(), event.expiresAt(), Phase.FINISHED, event.facts());
            if (active.remove(event.id(), event)) { history.add(completed); finished++; }
        }
        return finished;
    }

    public List<WorldEvent> activeEvents() { return List.copyOf(active.values()); }
    public List<WorldEvent> history() { synchronized (history) { return List.copyOf(history); } }

    public record Validation(boolean allowed, String reason) {
        public static Validation allow() { return new Validation(true, "accepted"); }
        public static Validation deny(String reason) { return new Validation(false, reason); }
    }
}
