package vn.svframe.svarcade.systems.turn;

import java.util.*;
import java.util.function.*;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.runtime.*;

/** Monotonic turn clocks with data-defined time banks and post-commit increment. */
public final class TurnSystem implements SessionSystem, TurnAccess {
    public static final Id ID = Id.of("svarcade:turns");
    public static final SessionServices.Key<TurnAccess> ACCESS = new SessionServices.Key<>(ID, TurnAccess.class);
    private static final long MAX_NANOS = 31_536_000_000_000_000L;
    public record Bank(long initial, long increment, long maximum) {
        public Bank { if (initial < 0 || increment < 0 || maximum < initial || maximum > MAX_NANOS || increment > maximum) throw new ConfigException("Invalid clock bank"); }
    }
    public record Config(Map<String, Bank> banks, String initialTeam, boolean enabled, boolean startsPaused, long persistenceInterval) {
        public Config {
            banks = Map.copyOf(banks);
            if (banks.isEmpty() || banks.size() > 1024 || !banks.containsKey(initialTeam) || persistenceInterval < 1 || persistenceInterval > MAX_NANOS) throw new ConfigException("Invalid turn clock config");
            for (var entry : banks.entrySet()) if (entry.getKey().isBlank() || entry.getKey().length() > 160 || enabled && entry.getValue().initial() == 0) throw new ConfigException("Invalid clock team or initial time");
        }
        public static Config parse(Node n) {
            n.only("banks", "initial_team", "enabled", "starts_paused", "persistence_interval_ms"); Map<String, Bank> banks = new LinkedHashMap<>();
            Node values = n.node("banks");
            for (String team : values.values().keySet()) {
                Node bank = values.node(team); bank.only("initial_ms", "increment_ms", "maximum_ms");
                banks.put(team, new Bank(nanos(bank, "initial_ms"), nanos(bank, "increment_ms"), nanos(bank, "maximum_ms")));
            }
            return new Config(banks, n.string("initial_team"), n.bool("enabled", true), n.bool("starts_paused", true), nanos(n, "persistence_interval_ms"));
        }
        private static long nanos(Node n, String key) { return Math.multiplyExact(n.integer(key, 0, MAX_NANOS / 1_000_000), 1_000_000L); }
    }
    public static final class Plan implements SystemSchema, SystemFactory {
        private final LongSupplier clock;
        public Plan() { this(System::nanoTime); }
        public Plan(LongSupplier clock) { this.clock = Objects.requireNonNull(clock); }
        @Override public void validate(Node config) { Config.parse(config); }
        @Override public Set<SessionServices.Key<?>> provides() { return Set.of(ACCESS); }
        @Override public SessionSystem create(GenericSession session, Node config) {
            TurnSystem system = new TurnSystem(Config.parse(config), clock, session.thread(), session::markDirty);
            session.services().provide(ACCESS, system); return system;
        }
    }
    private record State(String team, boolean running, Map<String, Long> banks, long anchor) {
        private State { banks = Map.copyOf(banks); }
    }
    private final Config config;
    private final LongSupplier clock;
    private final ThreadGuard thread;
    private final Runnable temporalDirty;
    private State state;
    private boolean active, closed, seen, lastExpired;
    private long lastSample, lastCheckpoint, revision;
    public TurnSystem(Config config, LongSupplier clock, ThreadGuard thread, Runnable temporalDirty) {
        this.config = Objects.requireNonNull(config); this.clock = Objects.requireNonNull(clock); this.thread = Objects.requireNonNull(thread); this.temporalDirty = Objects.requireNonNull(temporalDirty);
    }
    private void requireActive() { thread.check(); if (!active) throw new IllegalStateException("Turn clock inactive"); }
    private long sample() {
        long now = clock.getAsLong(); if (seen && now - lastSample < 0) throw new IllegalArgumentException("Non-monotonic clock");
        seen = true; lastSample = now; return now;
    }
    private long remaining(State value, String team, long now) {
        Long stored = value.banks().get(team); if (stored == null) throw new IllegalArgumentException("Unknown clock team");
        if (!config.enabled() || !value.running() || !team.equals(value.team())) return stored;
        long elapsed = now - value.anchor(); if (elapsed < 0) throw new IllegalArgumentException("Clock horizon exceeded");
        return elapsed >= stored ? 0 : stored - elapsed;
    }
    @Override public void start() {
        thread.check(); if (active || closed) throw new IllegalStateException("Turn system already started/closed");
        Map<String, Long> banks = new LinkedHashMap<>(); config.banks().forEach((team, bank) -> banks.put(team, bank.initial()));
        long now = sample(); state = new State(config.initialTeam(), !config.startsPaused(), banks, now); lastCheckpoint = now; active = true;
    }
    @Override public String team() { requireActive(); return state.team(); }
    @Override public boolean running() { requireActive(); return state.running(); }
    @Override public boolean expired() { requireActive(); return config.enabled() && remaining(state, state.team(), sample()) == 0; }
    @Override public long remainingNanos(String team) { requireActive(); return remaining(state, team, sample()); }
    private StateChange prepare(Function<Long, State> transition) {
        State before = state; long expected = revision; if (revision == Long.MAX_VALUE) throw new IllegalStateException("Clock revision exhausted");
        transition.apply(sample());
        return new StateChange() {
            private int status;
            private State applied;
            @Override public void apply() {
                requireActive(); if (status != 0 || state != before || revision != expected) throw new IllegalStateException("Stale or reused clock transaction");
                // Re-evaluate elapsed time at commit, including an expiry between prepare/apply.
                State candidate = transition.apply(sample()); state = candidate; applied = candidate; revision++; status = 1;
            }
            @Override public void rollback() {
                requireActive(); if (status != 1 || state != applied || revision != expected + 1) throw new IllegalStateException("Clock rollback conflict");
                state = before; revision = expected; status = 2;
            }
        };
    }
    @Override public StateChange preparePass(String actingTeam, String nextTeam) {
        requireActive(); State before = state;
        if (!before.running() || !before.team().equals(actingTeam) || !config.banks().containsKey(nextTeam) || actingTeam.equals(nextTeam)) throw new IllegalArgumentException("Invalid turn switch");
        return prepare(now -> {
            long left = remaining(before, actingTeam, now); if (config.enabled() && left == 0) throw new IllegalStateException("Clock expired");
            Bank bank = config.banks().get(actingTeam); Map<String, Long> balances = new LinkedHashMap<>(before.banks());
            balances.put(actingTeam, config.enabled() ? Math.min(bank.maximum(), Math.addExact(left, bank.increment())) : left);
            return new State(nextTeam, true, balances, now);
        });
    }
    @Override public StateChange prepareRunning(boolean running) {
        requireActive(); State before = state;
        return prepare(now -> {
            Map<String, Long> banks = new LinkedHashMap<>(before.banks()); banks.put(before.team(), remaining(before, before.team(), now));
            return new State(before.team(), running, banks, now);
        });
    }
    @Override public void tick(long tick) {
        requireActive(); long now = sample(); boolean expired = config.enabled() && remaining(state, state.team(), now) == 0;
        if (expired != lastExpired || config.enabled() && state.running() && now - lastCheckpoint >= config.persistenceInterval()) {
            lastExpired = expired; lastCheckpoint = now; temporalDirty.run();
        }
    }
    @Override public int stateSchema() { return 1; }
    @Override public Map<String, Object> snapshot() {
        requireActive(); long now = sample(); Map<String, Object> banks = new TreeMap<>();
        for (String team : config.banks().keySet()) banks.put(team, remaining(state, team, now));
        return Values.map(Map.of("team", state.team(), "running", state.running(), "banks", banks, "revision", revision));
    }
    @Override public void restore(int schema, Map<String, Object> saved) {
        thread.check(); if (active || closed || schema != 1) throw new IllegalArgumentException("Invalid clock restore");
        Node n = new Node(saved, "turn-state"); n.only("team", "running", "banks", "revision"); String team = n.string("team");
        Node banks = n.node("banks"); if (!config.banks().containsKey(team) || !banks.values().keySet().equals(config.banks().keySet())) throw new ConfigException("Persisted clock team mismatch");
        Map<String, Long> restored = new TreeMap<>(); config.banks().forEach((key, bank) -> restored.put(key, banks.integer(key, 0, bank.maximum())));
        boolean running = n.bool("running", false); long version = n.integer("revision", 0, Long.MAX_VALUE - 1), now = sample();
        state = new State(team, running, restored, now); revision = version; lastCheckpoint = now; active = true;
    }
    @Override public void close() { thread.check(); active = false; closed = true; state = null; }
}
