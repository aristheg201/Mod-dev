package vn.svframe.svarcade.runtime;

import java.util.*;
import vn.svframe.svarcade.config.*;

/** Owns runtime systems and a pinned definition. Does not know game identity. */
public final class GenericSession {
    public enum Status { CREATED, RUNNING, CLOSING, CLOSED }
    public record SystemState(int schema, Map<String, Object> data) {
        public SystemState { if (schema < 1) throw new IllegalArgumentException("State schema"); data = Values.map(data); }
    }
    private final UUID id;
    private final Definition definition;
    private final Map<UUID, Participant> participants;
    private final ThreadGuard thread;
    private final ResourceTracker resources;
    private final SessionServices services;
    private final ArenaRuntime arenas;
    private final ArenaRuntime.Lease lease;
    private final Map<Id, SessionSystem> systems = new LinkedHashMap<>();
    private Status status = Status.CREATED;
    private long revision;
    private long dirtyVersion;
    private boolean cleaning;
    private long lastTick = -1;

    public GenericSession(UUID id, Definition definition, String arena, List<Participant> participants,
                          ArenaRuntime arenas, ThreadGuard thread) {
        this.id = Objects.requireNonNull(id); this.definition = Objects.requireNonNull(definition);
        this.thread = Objects.requireNonNull(thread); thread.check(); this.arenas = Objects.requireNonNull(arenas);
        if (!definition.arenas().containsKey(arena)) throw new IllegalArgumentException("Unknown arena");
        Map<UUID, Participant> copy = new LinkedHashMap<>();
        for (Participant p : participants) if (copy.putIfAbsent(p.id(), p) != null) throw new IllegalArgumentException("Duplicate participant");
        long players = copy.values().stream().filter(p -> p.kind() != Participant.Kind.SPECTATOR).count();
        if (players < definition.minPlayers() || players > definition.maxPlayers()) throw new IllegalArgumentException("Participant limits");
        this.participants = Map.copyOf(copy);
        resources = new ResourceTracker(thread);
        services = new SessionServices(thread);
        lease = arenas.acquire(new ArenaRuntime.Key(definition.id(), arena), id);
    }
    void restoreRevision(long revision) {
        thread.check(); if (status != Status.CREATED || revision < 0 || revision == Long.MAX_VALUE) throw new IllegalArgumentException("Invalid restored revision");
        this.revision = revision;
    }
    public void start(Registry<SystemFactory> factories) { initialize(factories, null); }
    public void restore(Registry<SystemFactory> factories, Map<Id, SystemState> states) { initialize(factories, Map.copyOf(states)); }
    private void initialize(Registry<SystemFactory> factories, Map<Id, SystemState> states) {
        thread.check(); if (status != Status.CREATED) throw new IllegalStateException("Already initialized");
        try {
            Set<Id> expected = new HashSet<>(); definition.systems().forEach(s -> expected.add(s.id()));
            if (states != null && !expected.equals(states.keySet())) throw new IllegalArgumentException("Recovery system set mismatch");
            services.validatePlan(definition.systems(), factories);
            for (Definition.SystemSpec spec : definition.systems()) {
                SystemFactory factory = factories.require(spec.id());
                services.begin(spec.id(), factory);
                SessionSystem system = Objects.requireNonNull(factory.create(this, spec.config()));
                systems.put(spec.id(), system);
                resources.own("system/" + spec.id(), system::close);
                services.finish();
                if (states == null) system.start();
                else { SystemState state = states.get(spec.id()); system.restore(state.schema(), state.data()); }
            }
            if (status != Status.CREATED) throw new IllegalStateException("Session closed during initialization");
            status = Status.RUNNING; changed();
        } catch (RuntimeException e) { services.stopPublication(); close(); throw e; }
    }
    public void tick(long tick) {
        thread.check(); if (status != Status.RUNNING) return;
        if (tick < 0 || tick <= lastTick) throw new IllegalArgumentException("Non-increasing session tick");
        lastTick = tick;
        try {
            for (SessionSystem system : systems.values()) {
                if (status != Status.RUNNING) break;
                system.tick(tick);
            }
        }
        catch (RuntimeException e) { close(); throw e; }
    }
    public Map<Id, SystemState> snapshot() {
        thread.check(); if (status != Status.RUNNING) throw new IllegalStateException("Session not running");
        Map<Id, SystemState> result = new LinkedHashMap<>();
        systems.forEach((key, system) -> result.put(key, new SystemState(system.stateSchema(), system.snapshot())));
        return Map.copyOf(result);
    }
    public List<ResourceTracker.Failure> close() {
        thread.check(); if (status == Status.CLOSED || cleaning) return List.of();
        if (status != Status.CLOSING) {
            status = Status.CLOSING;
            if (revision < Long.MAX_VALUE) revision++;
        }
        services.stopPublication(); cleaning = true;
        try {
            List<ResourceTracker.Failure> failures = resources.cleanup();
            if (resources.size() == 0) { services.clear(); arenas.release(lease); status = Status.CLOSED; }
            return failures;
        } finally { cleaning = false; }
    }
    public void changed() {
        thread.check();
        if (status == Status.CLOSING || status == Status.CLOSED) throw new IllegalStateException("Session is closing");
        long nextRevision = Math.incrementExact(revision), nextDirty = Math.incrementExact(dirtyVersion);
        revision = nextRevision; dirtyVersion = nextDirty;
    }
    /** Persistence-only temporal progress does not invalidate otherwise legal pending intents. */
    public void markDirty() {
        thread.check(); if (status == Status.CLOSING || status == Status.CLOSED) throw new IllegalStateException("Session is closing");
        dirtyVersion = Math.incrementExact(dirtyVersion);
    }
    public long dirtyVersion() { thread.check(); return dirtyVersion; }
    public UUID id() { return id; }
    public Definition definition() { return definition; }
    public Map<UUID, Participant> participants() { return participants; }
    public ResourceTracker resources() { return resources; }
    public SessionServices services() { return services; }
    public ArenaRuntime.Lease lease() { return lease; }
    public ThreadGuard thread() { return thread; }
    public Status status() { thread.check(); return status; }
    public long revision() { thread.check(); return revision; }
}
