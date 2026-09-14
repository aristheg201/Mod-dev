package vn.svframe.svarcade.persistence;

import java.util.*;
import java.util.concurrent.*;
import vn.svframe.svarcade.runtime.*;

/** Dirty, bounded, non-blocking persistence coordinator. Runtime DTO capture occurs on owner thread; disk I/O does not. */
public final class PersistenceManager implements AutoCloseable {
    public record Failure(UUID session, String operation, String detail) { }
    private record Completion(UUID session, long dirtyVersion, Throwable failure) { }

    private final ThreadGuard thread;
    private final AtomicStore store;
    private final int maxInFlight;
    private final Map<UUID, Long> persisted = new HashMap<>();
    private final Set<UUID> inFlight = new HashSet<>();
    private final Set<UUID> known = new HashSet<>();
    private final ConcurrentLinkedQueue<Completion> completions = new ConcurrentLinkedQueue<>();
    private final ArrayDeque<Failure> failures = new ArrayDeque<>();
    private boolean closed;

    public PersistenceManager(ThreadGuard thread, AtomicStore store, int maxInFlight) {
        this.thread = Objects.requireNonNull(thread); this.store = Objects.requireNonNull(store);
        if (maxInFlight < 1 || maxInFlight > 4096) throw new IllegalArgumentException("Persistence in-flight limit"); this.maxInFlight = maxInFlight;
    }

    /** Call from owner tick. At most maxWrites new writes/deletes are submitted per call. */
    public int tick(Collection<GenericSession> sessions, int maxWrites) {
        thread.check(); requireOpen(); if (maxWrites < 1 || maxWrites > 4096) throw new IllegalArgumentException("Persistence write budget");
        drain(); Map<UUID, GenericSession> live = new LinkedHashMap<>();
        for (GenericSession session : sessions) if (session.status() == GenericSession.Status.RUNNING) live.put(session.id(), session);
        int submitted = 0;
        for (GenericSession session : live.values()) {
            known.add(session.id());
            if (submitted >= maxWrites || inFlight.size() >= maxInFlight || inFlight.contains(session.id())) continue;
            long dirty = session.dirtyVersion(); if (persisted.getOrDefault(session.id(), -1L) >= dirty) continue;
            SessionSnapshot snapshot = SessionSnapshot.capture(session); UUID id = session.id(); inFlight.add(id); submitted++;
            store.write(key(id), snapshot.encode()).whenComplete((ignored, failure) -> completions.add(new Completion(id, dirty, unwrap(failure))));
        }
        for (UUID stale : new ArrayList<>(known)) {
            if (submitted >= maxWrites || inFlight.size() >= maxInFlight) break;
            if (live.containsKey(stale) || inFlight.contains(stale)) continue;
            inFlight.add(stale); submitted++;
            store.delete(key(stale)).whenComplete((ignored, failure) -> completions.add(new Completion(stale, -1, unwrap(failure))));
        }
        return submitted;
    }

    /** Marks recovered state as already durable, preventing an immediate redundant write. */
    public void recovered(GenericSession session) {
        thread.check(); requireOpen(); known.add(session.id()); persisted.put(session.id(), session.dirtyVersion());
    }

    public void drain() {
        thread.check(); Completion completion;
        while ((completion = completions.poll()) != null) {
            inFlight.remove(completion.session());
            if (completion.failure() != null) { record(completion.session(), completion.dirtyVersion() < 0 ? "delete" : "write", completion.failure()); continue; }
            if (completion.dirtyVersion() < 0) { known.remove(completion.session()); persisted.remove(completion.session()); }
            else persisted.merge(completion.session(), completion.dirtyVersion(), Math::max);
        }
    }

    public int inFlight() { thread.check(); drain(); return inFlight.size(); }
    public List<Failure> failures() { thread.check(); drain(); return List.copyOf(failures); }
    private void record(UUID session, String operation, Throwable failure) {
        if (failures.size() == 128) failures.removeFirst(); String text = String.valueOf(failure);
        failures.addLast(new Failure(session, operation, text.length() <= 2048 ? text : text.substring(0, 2048)));
    }
    private static String key(UUID id) { return "session_" + id.toString().replace("-", "_"); }
    static UUID sessionId(String key) {
        if (!key.startsWith("session_")) throw new IllegalArgumentException("Not a session state key");
        return UUID.fromString(key.substring(8).replace('_', '-'));
    }
    private static Throwable unwrap(Throwable failure) {
        if (failure instanceof CompletionException e && e.getCause() != null) return e.getCause(); return failure;
    }
    private void requireOpen() { if (closed) throw new IllegalStateException("Persistence manager closed"); }
    @Override public void close() { thread.check(); if (!closed) { drain(); closed = true; store.close(); } }
}
