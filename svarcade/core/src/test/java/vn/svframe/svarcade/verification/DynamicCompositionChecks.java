package vn.svframe.svarcade.verification;

import java.util.*;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.runtime.*;

/** Verifies config-sensitive system DAGs and capability contracts stay aligned. */
public final class DynamicCompositionChecks {
    private DynamicCompositionChecks() { }

    private interface Probe { }
    private static final Id PROVIDER = Id.of("test:provider");
    private static final Id CONSUMER = Id.of("test:consumer");
    private static final SessionServices.Key<Probe> ACCESS = new SessionServices.Key<>(Id.of("test:probe"), Probe.class);

    private static final class ProviderPlan implements SystemSchema, SystemFactory {
        @Override public void validate(Node config) { config.only(); }
        @Override public Set<SessionServices.Key<?>> provides() { return Set.of(ACCESS); }
        @Override public SessionSystem create(GenericSession session, Node config) {
            session.services().provide(ACCESS, new Probe() { });
            return inert();
        }
    }

    private static final class ConsumerPlan implements SystemSchema, SystemFactory {
        @Override public void validate(Node config) { config.only("needs_probe"); config.bool("needs_probe", false); }
        @Override public Set<Id> dependencies(Node config) { return config.bool("needs_probe", false) ? Set.of(PROVIDER) : Set.of(); }
        @Override public Set<SessionServices.Key<?>> requires(Node config) { return config.bool("needs_probe", false) ? Set.of(ACCESS) : Set.of(); }
        @Override public SessionSystem create(GenericSession session, Node config) {
            if (config.bool("needs_probe", false)) session.services().require(ACCESS);
            return inert();
        }
    }

    private static SessionSystem inert() {
        return new SessionSystem() {
            @Override public void start() { }
            @Override public void tick(long tick) { }
            @Override public int stateSchema() { return 1; }
            @Override public Map<String, Object> snapshot() { return Map.of(); }
            @Override public void restore(int schema, Map<String, Object> state) { if (schema != 1 || !state.isEmpty()) throw new IllegalArgumentException(); }
            @Override public void close() { }
        };
    }

    public static void main(String[] args) {
        SystemCatalog catalog = SystemCatalog.builder().add(PROVIDER, new ProviderPlan()).add(CONSUMER, new ConsumerPlan()).build();
        Node empty = new Node(Map.of(), "provider");
        Node needs = new Node(Map.of("needs_probe", true), "consumer");
        List<Definition.SystemSpec> unordered = List.of(new Definition.SystemSpec(CONSUMER, needs), new Definition.SystemSpec(PROVIDER, empty));
        List<Definition.SystemSpec> ordered = DependencyGraph.order(unordered, Definition.SystemSpec::id,
                spec -> catalog.schemas().require(spec.id()).dependencies(spec.config()));
        Checks.equal(PROVIDER, ordered.get(0).id());
        Checks.equal(CONSUMER, ordered.get(1).id());

        Definition definition = new Definition(1, Id.of("test:game"), "fingerprint", true, 1, 1, Set.of(), ordered,
                Map.of("arena", new Node(Map.of("id", "arena"), "arena")));
        ThreadGuard thread = new ThreadGuard();
        ArenaRuntime arenas = new ArenaRuntime();
        GenericSession session = new GenericSession(UUID.randomUUID(), definition, "arena",
                List.of(new Participant(UUID.randomUUID(), Participant.Kind.PLAYER, "one")), arenas, thread);
        session.start(catalog.factories());
        Checks.equal(GenericSession.Status.RUNNING, session.status());
        session.close();
        Checks.equal(GenericSession.Status.CLOSED, session.status());

        Definition invalid = new Definition(1, Id.of("test:bad"), "fingerprint", true, 1, 1, Set.of(), unordered,
                Map.of("arena", new Node(Map.of("id", "arena"), "arena")));
        GenericSession bad = new GenericSession(UUID.randomUUID(), invalid, "arena",
                List.of(new Participant(UUID.randomUUID(), Participant.Kind.PLAYER, "one")), arenas, thread);
        Checks.rejects(ConfigException.class, () -> bad.start(catalog.factories()));
        Checks.equal(GenericSession.Status.CLOSED, bad.status());
        System.out.println("DynamicCompositionChecks passed");
    }
}
