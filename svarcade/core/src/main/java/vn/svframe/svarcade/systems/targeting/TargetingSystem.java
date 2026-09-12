package vn.svframe.svarcade.systems.targeting;

import java.util.*;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.runtime.*;
import vn.svframe.svarcade.systems.targeting.TargetingAccess.*;

/** Derived index: authoritative actor-owning systems republish targets on restore. */
public final class TargetingSystem implements SessionSystem, TargetingAccess {
    public static final Id ID = Id.of("svarcade:targeting");
    public static final SessionServices.Key<TargetingAccess> ACCESS = new SessionServices.Key<>(ID, TargetingAccess.class);
    public static final class Plan implements SystemSchema, SystemFactory {
        private SpatialTargetIndex compile(Node n, ThreadGuard thread) {
            n.only("cell_size", "max_range", "capacity", "cache_capacity", "max_query_cells", "max_candidates", "modes");
            Set<Mode> modes = new LinkedHashSet<>(); for (String name : n.strings("modes")) modes.add(Mode.valueOf(name));
            return new SpatialTargetIndex(thread, Numbers.decimal(n, "cell_size", Double.MIN_NORMAL, 1_000_000),
                    Numbers.decimal(n, "max_range", 0, 1_000_000), (int) n.integer("capacity", 1, 100_000),
                    (int) n.integer("cache_capacity", 1, 16_384), (int) n.integer("max_query_cells", 1, 65_536),
                    (int) n.integer("max_candidates", 1, 100_000), modes);
        }
        @Override public void validate(Node config) { compile(config, new ThreadGuard()); }
        @Override public Set<SessionServices.Key<?>> provides() { return Set.of(ACCESS); }
        @Override public SessionSystem create(GenericSession session, Node config) {
            TargetingSystem system = new TargetingSystem(compile(config, session.thread()), session.thread());
            session.services().provide(ACCESS, system); return system;
        }
    }
    private final SpatialTargetIndex index;
    private final ThreadGuard thread;
    private boolean active, closed;
    private TargetingSystem(SpatialTargetIndex index, ThreadGuard thread) { this.index = index; this.thread = thread; }
    private void requireActive() { thread.check(); if (!active) throw new IllegalStateException("Targeting inactive"); }
    @Override public void start() { thread.check(); if (active || closed) throw new IllegalStateException("Targeting already started/closed"); active = true; }
    @Override public void tick(long tick) { requireActive(); }
    @Override public int stateSchema() { return 1; }
    @Override public Map<String, Object> snapshot() { requireActive(); return Map.of(); }
    @Override public void restore(int schema, Map<String, Object> state) {
        thread.check(); if (active || closed || schema != 1 || !state.isEmpty()) throw new IllegalArgumentException("Invalid derived targeting state");
        index.clear(); active = true;
    }
    @Override public void upsert(Target target) { requireActive(); index.upsert(target); }
    @Override public boolean remove(UUID id) { requireActive(); return index.remove(id); }
    @Override public Optional<Target> select(UUID requester, Query query) { requireActive(); return index.select(requester, query); }
    @Override public int size() { thread.check(); return index.size(); }
    @Override public Map<String, Long> metrics() { return index.metrics(); }
    @Override public void close() { thread.check(); closed = true; active = false; index.clear(); }
}
