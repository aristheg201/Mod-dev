package vn.svframe.lively.actor;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Thread-safe source of immutable actor snapshots. Mutable platform entities never leave integrations. */
public final class ActorRegistry {
    private final ConcurrentHashMap<ActorId, ActorSnapshot> actors = new ConcurrentHashMap<>();
    private final AtomicLong revision = new AtomicLong();

    public ActorSnapshot upsert(ActorId id, String displayName, Map<String, Double> socialStats,
                                Map<String, String> facts, Set<String> tags) {
        Objects.requireNonNull(id);
        long next = revision.incrementAndGet();
        ActorSnapshot snapshot = new ActorSnapshot(id, next, Instant.now(), displayName, socialStats, facts, tags);
        actors.put(id, snapshot);
        return snapshot;
    }

    public Optional<ActorSnapshot> get(ActorId id) { return Optional.ofNullable(actors.get(id)); }
    public boolean remove(ActorId id) { boolean removed = actors.remove(id) != null; if (removed) revision.incrementAndGet(); return removed; }
    public Snapshot snapshot() { return new Snapshot(revision.get(), Map.copyOf(actors)); }

    public record Snapshot(long revision, Map<ActorId, ActorSnapshot> actors) {
        public Snapshot { actors = Map.copyOf(actors); }
    }
}
