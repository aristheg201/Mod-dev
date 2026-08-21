package vn.svframe.lively.event;

import vn.svframe.lively.actor.ActorId;
import vn.svframe.lively.world.SemanticStructureRegistry;
import vn.svframe.lively.world.WorldMutationPolicy;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** Persistent causal event state. It changes semantic world state, never terrain directly. */
public final class WorldEventEngine {
    public enum Category { CRIME, SOCIAL, ECONOMIC, FACTION_CONFLICT, FESTIVAL, DISASTER, MYSTERY, MIGRATION, POLITICAL, DISCOVERY }
    public enum Phase { PROPOSED, ACTIVE, PAUSED, RESOLVING, FINISHED, CANCELLED }

    public interface Listener {
        default void onStarted(WorldEvent event) {}
        default void onFinished(WorldEvent event) {}
        default void onCancelled(WorldEvent event) {}
    }

    public record EventProposal(Category category, String seed, String structureId, Set<ActorId> participants,
                                double intensity, Duration duration, Map<String, String> facts) {
        public EventProposal {
            Objects.requireNonNull(category); Objects.requireNonNull(seed); Objects.requireNonNull(duration);
            participants = Set.copyOf(participants); facts = Map.copyOf(facts);
            intensity = Math.max(0D, Math.min(1D, intensity));
        }
    }

    public record WorldEvent(UUID id, Category category, String seed, String structureId, Set<ActorId> participants,
                             double intensity, Instant startedAt, Instant expiresAt, Phase phase, Map<String, String> facts) {
        public WorldEvent {
            Objects.requireNonNull(id); Objects.requireNonNull(category); Objects.requireNonNull(seed);
            Objects.requireNonNull(startedAt); Objects.requireNonNull(expiresAt); Objects.requireNonNull(phase);
            participants = Set.copyOf(participants); facts = Map.copyOf(facts);
        }
        public boolean expired(Instant now) { return !expiresAt.isAfter(now); }
        public WorldEvent withPhase(Phase next) {
            return new WorldEvent(id, category, seed, structureId, participants, intensity, startedAt, expiresAt, next, facts);
        }
    }

    private final ConcurrentHashMap<UUID, WorldEvent> active = new ConcurrentHashMap<>();
    private final List<WorldEvent> history = Collections.synchronizedList(new ArrayList<>());
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private final SemanticStructureRegistry structures;
    private final WorldMutationPolicy policy;
    private final int maxActive;

    public WorldEventEngine(SemanticStructureRegistry structures, WorldMutationPolicy policy, int maxActive) {
        this.structures = Objects.requireNonNull(structures);
        this.policy = Objects.requireNonNull(policy);
        this.maxActive = Math.max(1, Math.min(10_000, maxActive));
    }

    public void addListener(Listener listener) { listeners.addIfAbsent(Objects.requireNonNull(listener)); }
    public boolean removeListener(Listener listener) { return listener != null && listeners.remove(listener); }

    public Validation validate(EventProposal proposal) {
        Objects.requireNonNull(proposal);
        if (active.size() >= maxActive) return Validation.deny("active_event_limit");
        if (proposal.seed().isBlank() || proposal.seed().length() > 128) return Validation.deny("invalid_seed");
        if (proposal.duration().isNegative() || proposal.duration().isZero() || proposal.duration().compareTo(Duration.ofDays(30)) > 0) return Validation.deny("invalid_duration");
        if (proposal.participants().size() > 256 || proposal.facts().size() > 128) return Validation.deny("too_large");
        if (proposal.structureId() != null && structures.get(proposal.structureId()).isEmpty()) return Validation.deny("unknown_structure");
        return Validation.allow();
    }

    public Optional<WorldEvent> start(EventProposal proposal) {
        if (!validate(proposal).allowed()) return Optional.empty();
        if (!policy.evaluate(new WorldMutationPolicy.Proposal(null, WorldMutationPolicy.Source.SYSTEM,
                WorldMutationPolicy.MutationClass.SEMANTIC, WorldMutationPolicy.ActionKind.EVENT_STATE,
                proposal.structureId(), null, Map.of("seed", proposal.seed()))).allowed()) return Optional.empty();
        Instant now = Instant.now();
        WorldEvent event = new WorldEvent(UUID.randomUUID(), proposal.category(), proposal.seed(), proposal.structureId(),
                proposal.participants(), proposal.intensity(), now, now.plus(proposal.duration()), Phase.ACTIVE, proposal.facts());
        active.put(event.id(), event);
        listeners.forEach(listener -> safe(() -> listener.onStarted(event)));
        return Optional.of(event);
    }

    public Optional<WorldEvent> pause(UUID id) {
        return Optional.ofNullable(active.computeIfPresent(id, (key, event) -> event.phase() == Phase.ACTIVE ? event.withPhase(Phase.PAUSED) : event))
                .filter(event -> event.phase() == Phase.PAUSED);
    }

    public Optional<WorldEvent> resume(UUID id) {
        return Optional.ofNullable(active.computeIfPresent(id, (key, event) -> event.phase() == Phase.PAUSED ? event.withPhase(Phase.ACTIVE) : event))
                .filter(event -> event.phase() == Phase.ACTIVE);
    }

    public Optional<WorldEvent> finish(UUID id) {
        WorldEvent event = active.remove(id);
        if (event == null) return Optional.empty();
        WorldEvent done = event.withPhase(Phase.FINISHED);
        appendHistory(done);
        listeners.forEach(listener -> safe(() -> listener.onFinished(done)));
        return Optional.of(done);
    }

    public Optional<WorldEvent> cancel(UUID id) {
        WorldEvent event = active.remove(id);
        if (event == null) return Optional.empty();
        WorldEvent cancelled = event.withPhase(Phase.CANCELLED);
        appendHistory(cancelled);
        listeners.forEach(listener -> safe(() -> listener.onCancelled(cancelled)));
        return Optional.of(cancelled);
    }

    public int advance(Instant now) {
        int finished = 0;
        for (WorldEvent event : List.copyOf(active.values())) {
            if (event.phase() == Phase.PAUSED || !event.expired(now)) continue;
            WorldEvent done = event.withPhase(Phase.FINISHED);
            if (active.remove(event.id(), event)) {
                appendHistory(done); finished++;
                listeners.forEach(listener -> safe(() -> listener.onFinished(done)));
            }
        }
        return finished;
    }

    public List<WorldEvent> activeEvents() { return List.copyOf(active.values()); }
    public List<WorldEvent> history() { synchronized (history) { return List.copyOf(history); } }

    public Snapshot snapshot() { synchronized (history) { return new Snapshot(Map.copyOf(active), List.copyOf(history)); } }
    public void restore(Snapshot snapshot) {
        active.clear();
        for (var entry : snapshot.active().entrySet()) {
            if (Set.of(Phase.ACTIVE, Phase.PAUSED, Phase.RESOLVING).contains(entry.getValue().phase())) active.put(entry.getKey(), entry.getValue());
        }
        synchronized (history) {
            history.clear();
            List<WorldEvent> restored = snapshot.history();
            history.addAll(restored.stream().skip(Math.max(0, restored.size() - 100_000L)).toList());
        }
    }

    public record Snapshot(Map<UUID, WorldEvent> active, List<WorldEvent> history) {
        public Snapshot { active = Map.copyOf(active); history = List.copyOf(history); }
    }

    private void appendHistory(WorldEvent event) {
        synchronized (history) {
            history.add(event);
            if (history.size() > 100_000) history.subList(0, history.size() - 100_000).clear();
        }
    }
    private static void safe(Runnable task) { try { task.run(); } catch (RuntimeException ignored) { } }

    public record Validation(boolean allowed, String reason) {
        public static Validation allow() { return new Validation(true, "accepted"); }
        public static Validation deny(String reason) { return new Validation(false, reason); }
    }
}
