package vn.svframe.svarcade.systems.board;

import java.util.*;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.runtime.*;
import vn.svframe.svarcade.systems.board.MovementRules.*;

/** Stateless compiled rule capability selected by game.yml like any other generic system. */
public final class MovementSystem implements SessionSystem, MovementAccess {
    public static final Id ID = Id.of("svarcade:movement");
    public static final SessionServices.Key<MovementAccess> ACCESS = new SessionServices.Key<>(ID, MovementAccess.class);
    public static final class Plan implements SystemSchema, SystemFactory {
        @Override public void validate(Node config) { MovementDefinition.parse(config); }
        @Override public Set<SessionServices.Key<?>> provides() { return Set.of(ACCESS); }
        @Override public SessionSystem create(GenericSession session, Node config) {
            MovementSystem system = new MovementSystem(MovementDefinition.parse(config), session.thread());
            session.services().provide(ACCESS, system); return system;
        }
    }
    private final MovementRules rules;
    private final ThreadGuard thread;
    private boolean active, closed;
    public MovementSystem(MovementDefinition definition, ThreadGuard thread) { rules = new MovementRules(definition); this.thread = Objects.requireNonNull(thread); }
    private void requireActive() { thread.check(); if (!active) throw new IllegalStateException("Movement system inactive"); }
    @Override public MovementDefinition definition() { requireActive(); return rules.definition(); }
    @Override public void validate(GridPosition position) { requireActive(); rules.validate(position); }
    @Override public List<Successor> successors(GridPosition position) { requireActive(); return rules.successors(position); }
    @Override public GridPosition apply(GridPosition position, Move move) { requireActive(); return rules.apply(position, move); }
    @Override public boolean threatened(GridPosition position, String team) { requireActive(); return rules.threatened(position, team); }
    @Override public String repetitionKey(GridPosition position) { requireActive(); return rules.repetitionKey(position); }
    @Override public void start() { thread.check(); if (active || closed) throw new IllegalStateException("Movement already started/closed"); active = true; }
    @Override public void tick(long tick) { requireActive(); }
    @Override public int stateSchema() { return 1; }
    @Override public Map<String, Object> snapshot() { requireActive(); return Map.of(); }
    @Override public void restore(int schema, Map<String, Object> state) {
        thread.check(); if (schema != 1 || !state.isEmpty()) throw new IllegalArgumentException("Invalid movement state"); start();
    }
    @Override public void close() { thread.check(); active = false; closed = true; }
}
