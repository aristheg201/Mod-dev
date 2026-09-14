package vn.svframe.svarcade.runtime;

import java.util.*;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.persistence.SessionSnapshot;

/** Authoritative session directory. Failed cleanup remains reachable for retries. */
public final class GenericGameRuntime {
    @FunctionalInterface public interface SessionInitializer { void initialize(GenericSession session); }
    public record Failure(UUID session, String operation, String detail) { }
    private final ThreadGuard thread;
    private final DefinitionRegistry definitions;
    private final Registry<SystemFactory> systems;
    private final SessionInitializer initializer;
    private final ArenaRuntime arenas = new ArenaRuntime();
    private final Map<UUID, GenericSession> sessions = new LinkedHashMap<>();
    private final Map<UUID, UUID> membership = new HashMap<>();
    private final Deque<Failure> failures = new ArrayDeque<>();
    private final int capacity;
    private boolean iterating;
    private long lastTickNanos;

    public GenericGameRuntime(ThreadGuard thread, DefinitionRegistry definitions, Registry<SystemFactory> systems, int capacity) {
        this(thread, definitions, systems, capacity, session -> { });
    }
    public GenericGameRuntime(ThreadGuard thread, DefinitionRegistry definitions, Registry<SystemFactory> systems,
                              int capacity, SessionInitializer initializer) {
        if (capacity < 1) throw new IllegalArgumentException("Session capacity");
        this.thread = Objects.requireNonNull(thread); this.definitions = Objects.requireNonNull(definitions);
        this.systems = Objects.requireNonNull(systems); this.initializer = Objects.requireNonNull(initializer); this.capacity = capacity;
    }
    public GenericSession open(Id definition, String arena, List<Participant> participants) {
        return initialize(UUID.randomUUID(), definitions.snapshot().requireAvailable(definition), arena, participants, null);
    }
    public GenericSession recover(SessionSnapshot saved) {
        thread.check(); Definition definition = definitions.snapshot().requireAvailable(saved.definition()); saved.validateAgainst(definition);
        return initialize(saved.id(), definition, saved.arena(), saved.participants(), saved);
    }
    private GenericSession initialize(UUID id, Definition definition, String arena, List<Participant> participants, SessionSnapshot saved) {
        thread.check(); if (iterating) throw new IllegalStateException("Cannot open sessions during runtime iteration");
        if (sessions.size() >= capacity || sessions.containsKey(id)) throw new IllegalStateException("Session capacity or duplicate identity");
        for (Participant p : participants) if (sessionFor(p.id()).isPresent()) throw new IllegalStateException("Participant already owns a session");
        GenericSession session = new GenericSession(id, definition, arena, participants, arenas, thread);
        sessions.put(id, session); participants.forEach(p -> membership.put(p.id(), id));
        try {
            if (saved == null) { session.start(systems); initializer.initialize(session); }
            else { session.restoreRevision(saved.revision()); session.restore(systems, saved.systems()); }
            return session;
        } catch (RuntimeException e) {
            failure(id, "initialize", e.toString());
            for (ResourceTracker.Failure problem : session.close()) failure(id, "cleanup", problem.resource() + ": " + problem.cause());
            reap(session); throw e;
        }
    }
    public void tick(long tick) {
        thread.check(); if (iterating) throw new IllegalStateException("Reentrant runtime tick");
        long started = System.nanoTime(); iterating = true;
        try {
            Iterator<GenericSession> iterator = sessions.values().iterator();
            while (iterator.hasNext()) {
                GenericSession session = iterator.next();
                try {
                    if (session.status() == GenericSession.Status.CLOSING) {
                        for (var problem : session.close()) failure(session.id(), "cleanup", problem.resource() + ": " + problem.cause());
                    } else session.tick(tick);
                } catch (RuntimeException e) { failure(session.id(), "tick", e.toString()); }
                if (session.status() == GenericSession.Status.CLOSED) { releaseMembership(session); iterator.remove(); }
            }
        } finally { iterating = false; lastTickNanos = System.nanoTime() - started; }
    }
    public List<ResourceTracker.Failure> close(UUID id) {
        thread.check(); GenericSession session = sessions.get(id); if (session == null) return List.of();
        var result = session.close(); for (var f : result) failure(id, "cleanup", f.resource() + ": " + f.cause());
        if (!iterating) reap(session); return result;
    }
    /** Server shutdown path. Cleanup remains retryable for resources that report failures. */
    public List<ResourceTracker.Failure> closeAll() {
        thread.check(); if (iterating) throw new IllegalStateException("Cannot close all during runtime iteration");
        List<ResourceTracker.Failure> result = new ArrayList<>();
        for (GenericSession session : new ArrayList<>(sessions.values())) result.addAll(close(session.id()));
        for (GenericSession session : new ArrayList<>(sessions.values())) if (session.status() == GenericSession.Status.CLOSING) result.addAll(close(session.id()));
        return List.copyOf(result);
    }
    private void reap(GenericSession session) {
        if (session.status() == GenericSession.Status.CLOSED) { sessions.remove(session.id()); releaseMembership(session); }
    }
    private void releaseMembership(GenericSession session) { session.participants().keySet().forEach(p -> membership.remove(p, session.id())); }
    private void failure(UUID id, String operation, String detail) {
        if (failures.size() == 128) failures.removeFirst();
        failures.addLast(new Failure(id, operation, detail.length() <= 2048 ? detail : detail.substring(0, 2048)));
    }
    /** Dynamic spectator membership is reconciled lazily against authoritative session rosters. */
    public Optional<GenericSession> sessionFor(UUID participant) {
        thread.check(); Objects.requireNonNull(participant);
        UUID known = membership.get(participant);
        if (known != null) {
            GenericSession session = sessions.get(known);
            if (session != null && session.participants().containsKey(participant)) return Optional.of(session);
            membership.remove(participant, known);
        }
        GenericSession found = null;
        for (GenericSession session : sessions.values()) if (session.participants().containsKey(participant)) {
            if (found != null) throw new IllegalStateException("Participant belongs to multiple sessions: " + participant);
            found = session;
        }
        if (found != null) membership.put(participant, found.id());
        return Optional.ofNullable(found);
    }
    public Map<UUID, GenericSession> sessions() { thread.check(); return Map.copyOf(sessions); }
    public List<Failure> failures() { thread.check(); return List.copyOf(failures); }
    public ArenaRuntime arenas() { return arenas; }
    public long lastTickNanos() { thread.check(); return lastTickNanos; }
}
