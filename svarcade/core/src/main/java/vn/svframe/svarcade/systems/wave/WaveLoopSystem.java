package vn.svframe.svarcade.systems.wave;

import java.util.*;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.runtime.*;

/** Generic FSM/wave coordinator. State names and events are definition data. */
public final class WaveLoopSystem implements SessionSystem {
    public static final Id ID = Id.of("svarcade:wave_loop");

    public record Config(String waveState, String clearState, String acknowledgeState, String clearedEvent, String completeEvent) {
        public Config {
            for (String value : List.of(waveState, clearState, acknowledgeState, clearedEvent, completeEvent))
                if (value == null || !value.matches("[A-Za-z0-9_-]{1,80}")) throw new ConfigException("Invalid wave-loop state/event name");
            if (waveState.equals(clearState) || waveState.equals(acknowledgeState)) throw new ConfigException("Wave-loop states must be distinct");
        }
        public static Config parse(Node n) {
            n.only("wave_state", "clear_state", "acknowledge_state", "cleared_event", "complete_event");
            return new Config(n.string("wave_state"), n.string("clear_state"), n.string("acknowledge_state"), n.string("cleared_event"), n.string("complete_event"));
        }
    }

    public static final class Plan implements SystemSchema, SystemFactory {
        @Override public void validate(Node config) { Config.parse(config); }
        @Override public Set<Id> dependencies() { return Set.of(WaveSystem.ID, StateMachineSystem.ID); }
        @Override public Set<SessionServices.Key<?>> requires() { return Set.of(WaveAccess.ACCESS, StateMachineAccess.ACCESS); }
        @Override public SessionSystem create(GenericSession session, Node config) {
            return new WaveLoopSystem(Config.parse(config), session.services().require(WaveAccess.ACCESS), session.services().require(StateMachineAccess.ACCESS), session);
        }
    }

    private final Config config;
    private final WaveAccess waves;
    private final StateMachineAccess fsm;
    private final GenericSession session;
    private final ThreadGuard thread;
    private boolean active, closed, completionSignaled;
    private long revision;

    private WaveLoopSystem(Config config, WaveAccess waves, StateMachineAccess fsm, GenericSession session) {
        this.config = config; this.waves = waves; this.fsm = fsm; this.session = session; thread = session.thread();
    }
    private void requireActive() { thread.check(); if (!active || closed) throw new IllegalStateException("Wave loop inactive"); }
    @Override public void start() { thread.check(); if (active || closed) throw new IllegalStateException("Wave loop already initialized"); active = true; }
    @Override public void tick(long tick) {
        requireActive(); boolean changed = false; String state = fsm.state();
        if (state.equals(config.waveState()) && waves.activeWaveIndex().isEmpty() && waves.clearEvent().isEmpty() && !waves.complete()) {
            waves.prepareStartNext().apply(); changed = true;
        }
        if (state.equals(config.waveState()) && waves.clearEvent().isPresent()) {
            if (!fsm.event(config.clearedEvent()) || !fsm.state().equals(config.clearState())) throw new IllegalStateException("FSM rejected configured wave-cleared transition");
            changed = true; state = fsm.state();
        }
        if (state.equals(config.acknowledgeState()) && waves.clearEvent().isPresent()) {
            waves.prepareClearAcknowledged().apply(); changed = true;
        }
        if (waves.complete() && !completionSignaled) {
            if (!fsm.event(config.completeEvent())) throw new IllegalStateException("FSM rejected configured waves-complete event"); completionSignaled = true; changed = true;
        }
        if (changed) { revision = Math.incrementExact(revision); session.markDirty(); }
    }
    @Override public int stateSchema() { return 1; }
    @Override public Map<String, Object> snapshot() { requireActive(); return Map.of("revision", revision, "completion_signaled", completionSignaled); }
    @Override public void restore(int schema, Map<String, Object> saved) {
        thread.check(); if (active || closed || schema != 1) throw new IllegalArgumentException("Invalid wave-loop restore");
        Node n = new Node(saved, "wave-loop-state"); n.only("revision", "completion_signaled"); revision = n.integer("revision", 0, Long.MAX_VALUE - 1); completionSignaled = n.bool("completion_signaled", false); active = true;
        if (completionSignaled && !waves.complete()) throw new ConfigException("Wave-loop completion flag without complete waves");
    }
    @Override public void close() { thread.check(); active = false; closed = true; }
}
