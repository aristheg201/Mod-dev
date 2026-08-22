package vn.svframe.lively.event;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Persistent semantic story arcs. Phase changes are explicit consequences of event lifecycle, never block edits. */
public final class StoryArcEngine {
    public enum State { ACTIVE, PAUSED, RESOLVED, ABANDONED }

    public record Arc(UUID id, String seed, String title, int phase, int maxPhase, double tension, State state,
                      List<UUID> events, Map<String, String> facts, Instant updatedAt) {
        public Arc {
            Objects.requireNonNull(id); Objects.requireNonNull(seed); Objects.requireNonNull(title);
            Objects.requireNonNull(state); Objects.requireNonNull(updatedAt);
            maxPhase = Math.max(1, maxPhase);
            phase = Math.max(1, Math.min(maxPhase, phase));
            events = List.copyOf(events == null ? List.of() : events);
            facts = Map.copyOf(facts == null ? Map.of() : facts);
            tension = clamp01(tension);
        }
    }

    private final ConcurrentHashMap<UUID, Arc> arcs = new ConcurrentHashMap<>();

    public Arc start(String seed, String title, int maxPhase, Map<String, String> facts) {
        if (seed == null || seed.isBlank() || seed.length() > 128) throw new IllegalArgumentException("invalid story seed");
        if (title == null || title.isBlank() || title.length() > 256) throw new IllegalArgumentException("invalid story title");
        Arc arc = new Arc(UUID.randomUUID(), seed, title, 1, Math.max(1, Math.min(64, maxPhase)), .2D,
                State.ACTIVE, List.of(), facts, Instant.now());
        arcs.put(arc.id(), arc);
        return arc;
    }

    /** Attaching an event records causality and tension, but does not silently advance the story phase. */
    public Optional<Arc> attachEvent(UUID arcId, UUID eventId, double tensionDelta) {
        if (arcId == null || eventId == null) return Optional.empty();
        return Optional.ofNullable(arcs.computeIfPresent(arcId, (key, old) -> {
            if (old.state() != State.ACTIVE && old.state() != State.PAUSED) return old;
            ArrayList<UUID> events = new ArrayList<>(old.events());
            if (!events.contains(eventId)) events.add(eventId);
            if (events.size() > 256) events.subList(0, events.size() - 256).clear();
            return new Arc(old.id(), old.seed(), old.title(), old.phase(), old.maxPhase(),
                    clamp01(old.tension() + tensionDelta), old.state(), events, old.facts(), Instant.now());
        }));
    }

    /** One completed causal event advances one phase. Reaching maxPhase resolves the arc. */
    public Optional<Arc> advance(UUID id, double tensionDelta) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(arcs.computeIfPresent(id, (key, old) -> {
            if (old.state() != State.ACTIVE) return old;
            int nextPhase = Math.min(old.maxPhase(), old.phase() + 1);
            State nextState = nextPhase >= old.maxPhase() ? State.RESOLVED : State.ACTIVE;
            return new Arc(old.id(), old.seed(), old.title(), nextPhase, old.maxPhase(),
                    clamp01(old.tension() + tensionDelta), nextState, old.events(), old.facts(), Instant.now());
        }));
    }

    public Optional<Arc> tension(UUID id, double delta) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(arcs.computeIfPresent(id, (key, old) -> new Arc(old.id(), old.seed(), old.title(),
                old.phase(), old.maxPhase(), clamp01(old.tension() + delta), old.state(), old.events(), old.facts(), Instant.now())));
    }

    public Optional<Arc> state(UUID id, State state) {
        if (id == null || state == null) return Optional.empty();
        return Optional.ofNullable(arcs.computeIfPresent(id, (key, old) -> new Arc(old.id(), old.seed(), old.title(),
                old.phase(), old.maxPhase(), old.tension(), state, old.events(), old.facts(), Instant.now())));
    }

    public Optional<Arc> get(UUID id) { return Optional.ofNullable(arcs.get(id)); }

    public List<Arc> active() {
        return arcs.values().stream().filter(arc -> arc.state() == State.ACTIVE)
                .sorted(Comparator.comparing(Arc::updatedAt).reversed().thenComparing(arc -> arc.id().toString())).toList();
    }

    public Map<UUID, Arc> snapshot() { return Map.copyOf(arcs); }

    public void restore(Map<UUID, Arc> snapshot) {
        arcs.clear();
        if (snapshot == null) return;
        snapshot.values().stream().limit(10_000).forEach(arc -> arcs.put(arc.id(), arc));
    }

    private static double clamp01(double value) { return Math.max(0D, Math.min(1D, value)); }
}
