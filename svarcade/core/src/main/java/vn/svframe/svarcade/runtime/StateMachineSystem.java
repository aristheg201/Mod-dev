package vn.svframe.svarcade.runtime;

import java.util.*;
import vn.svframe.svarcade.config.*;

/** Session-system adapter for the generic data-defined state-machine runtime. */
public final class StateMachineSystem implements SessionSystem, StateMachineAccess {
    public static final Id ID = Id.of("svarcade:state_machine");
    public static final class Plan implements SystemSchema, SystemFactory {
        private final StateMachineRuntime.Plan delegate;
        public Plan(Registry<StateMachineRuntime.Action> actions, Registry<StateMachineRuntime.Condition> conditions) {
            delegate = new StateMachineRuntime.Plan(Objects.requireNonNull(actions), Objects.requireNonNull(conditions));
        }
        @Override public void validate(Node config) { delegate.validate(config); }
        @Override public Set<SessionServices.Key<?>> provides() { return Set.of(StateMachineAccess.ACCESS); }
        @Override public SessionSystem create(GenericSession session, Node config) {
            StateMachineSystem system = new StateMachineSystem(delegate.create(config, session.thread(), session::changed));
            session.services().provide(StateMachineAccess.ACCESS, system); return system;
        }
    }
    private final StateMachineRuntime delegate;
    private StateMachineSystem(StateMachineRuntime delegate) { this.delegate = Objects.requireNonNull(delegate); }
    @Override public void start() { delegate.start(); }
    @Override public void tick(long tick) { delegate.tick(tick); }
    @Override public int stateSchema() { return delegate.stateSchema(); }
    @Override public Map<String, Object> snapshot() { return delegate.snapshot(); }
    @Override public void restore(int schema, Map<String, Object> state) { delegate.restore(schema, state); }
    @Override public void close() { delegate.close(); }
    @Override public boolean event(String event) { return delegate.event(event); }
    @Override public String state() { return delegate.state(); }
    @Override public Map<String, Object> data() { return delegate.data(); }
    @Override public boolean terminal() { return delegate.terminal(); }
}
