package vn.svframe.svarcade.security;

import java.util.*;
import vn.svframe.svarcade.bot.BotRuntime;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.runtime.*;

/** Configured session system that owns the single human/bot action ingress. */
public final class ActionSystem implements SessionSystem, ActionAccess {
    public static final Id ID = Id.of("svarcade:actions");
    public static final SessionServices.Key<ActionAccess> ACCESS = new SessionServices.Key<>(ID, ActionAccess.class);

    private record Binding(Id action, Id handler, Node config) { }
    private record Config(int maxKeys, double capacity, double perTick, double maxDistance,
                          int eventCapacity, List<Binding> bindings) {
        private Config {
            bindings = List.copyOf(bindings);
            if (bindings.isEmpty() || bindings.size() > 4096) throw new ConfigException("Action binding count outside limits");
        }
        static Config parse(Node n) {
            n.only("rate_limit", "max_distance", "event_capacity", "actions");
            Node rate = n.node("rate_limit"); rate.only("max_keys", "capacity", "per_tick");
            int maxKeys = (int) rate.integer("max_keys", 1, 1_000_000);
            double capacity = Numbers.decimal(rate, "capacity", 1, 1_000_000);
            double perTick = Numbers.decimal(rate, "per_tick", Double.MIN_NORMAL, 1_000_000);
            double range = Numbers.decimal(n, "max_distance", 0, 1_000_000);
            int events = (int) n.integer("event_capacity", 1, 1_000_000);
            Node actions = n.node("actions");
            if (actions.values().isEmpty()) throw new ConfigException("At least one configured action is required");
            List<Binding> result = new ArrayList<>();
            for (String raw : new TreeSet<>(actions.values().keySet())) {
                Id action = Id.of(raw); Node binding = actions.node(raw); binding.only("handler", "config");
                result.add(new Binding(action, Id.of(binding.string("handler")), binding.node("config")));
            }
            return new Config(maxKeys, capacity, perTick, range, events, result);
        }
    }

    public static final class Plan implements SystemSchema, SystemFactory {
        private final Registry<ActionHandlerFactory> handlers;
        public Plan(Registry<ActionHandlerFactory> handlers) { this.handlers = Objects.requireNonNull(handlers); }
        private Config compile(Node config) {
            Config parsed = Config.parse(config);
            Set<Id> actions = new HashSet<>();
            for (Binding binding : parsed.bindings()) {
                if (!actions.add(binding.action())) throw new ConfigException("Duplicate configured action: " + binding.action());
                handlers.require(binding.handler()).validate(binding.config());
            }
            return parsed;
        }
        @Override public void validate(Node config) { compile(config); }
        @Override public Set<Id> dependencies(Node config) {
            Set<Id> result = new LinkedHashSet<>();
            for (Binding binding : compile(config).bindings()) result.addAll(handlers.require(binding.handler()).dependencies(binding.config()));
            return Set.copyOf(result);
        }
        @Override public Set<SessionServices.Key<?>> requires(Node config) {
            Set<SessionServices.Key<?>> result = new LinkedHashSet<>();
            for (Binding binding : compile(config).bindings()) result.addAll(handlers.require(binding.handler()).requires(binding.config()));
            return Set.copyOf(result);
        }
        @Override public Set<SessionServices.Key<?>> provides() { return Set.of(ACCESS); }
        @Override public SessionSystem create(GenericSession session, Node config) {
            Config parsed = compile(config); Registry.Builder<ActionDispatcher.Handler> actions = new Registry.Builder<>();
            for (Binding binding : parsed.bindings()) actions.add(binding.action(), handlers.require(binding.handler()).create(session, binding.config()));
            ActionSystem system = new ActionSystem(session, new RateLimiter(parsed.maxKeys(), parsed.capacity(), parsed.perTick()),
                    actions.build(), parsed.maxDistance(), parsed.eventCapacity());
            session.services().provide(ACCESS, system); return system;
        }
    }

    private final GenericSession session;
    private final ActionDispatcher dispatcher;
    private boolean active, closed;
    private ActionSystem(GenericSession session, RateLimiter limiter, Registry<ActionDispatcher.Handler> handlers,
                         double maxDistance, int eventCapacity) {
        this.session = Objects.requireNonNull(session);
        dispatcher = new ActionDispatcher(session, session.arenaRuntime(), limiter, handlers, maxDistance, eventCapacity);
    }
    private void requireActive() { session.thread().check(); if (!active || closed) throw new IllegalStateException("Action service inactive"); }
    @Override public void start() { session.thread().check(); if (active || closed) throw new IllegalStateException("Action service already initialized"); active = true; }
    @Override public void tick(long tick) { requireActive(); }
    @Override public int stateSchema() { return 1; }
    @Override public Map<String, Object> snapshot() { requireActive(); return Map.of(); }
    @Override public void restore(int schema, Map<String, Object> state) {
        session.thread().check(); if (schema != 1 || !state.isEmpty()) throw new ConfigException("Invalid action service state"); start();
    }
    @Override public UUID issueController(UUID actor, long expires) { requireActive(); return dispatcher.issueController(actor, expires); }
    @Override public void revokeController(UUID actor) { session.thread().check(); dispatcher.revokeController(actor); }
    @Override public IntentGate.Result dispatchHuman(IntentGate.Facts facts, IntentGate.Intent intent) { requireActive(); return dispatcher.dispatchHuman(facts, intent); }
    @Override public IntentGate.Result dispatchBot(IntentGate.Facts facts, UUID intendedSession, long revision, BotRuntime.Decision decision) { requireActive(); return dispatcher.dispatchBot(facts, intendedSession, revision, decision); }
    @Override public List<ActionDispatcher.Event> drainEvents(int maximum) { requireActive(); return dispatcher.drainEvents(maximum); }
    @Override public Set<Id> actions() { requireActive(); return dispatcher.actions(); }
    @Override public Map<String, Integer> ownedCounts() { session.thread().check(); return dispatcher.ownedCounts(); }
    @Override public void close() { session.thread().check(); if (closed) return; closed = true; active = false; dispatcher.close(); }
}
