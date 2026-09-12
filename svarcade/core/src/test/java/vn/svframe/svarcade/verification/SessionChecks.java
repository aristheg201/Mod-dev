package vn.svframe.svarcade.verification;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.runtime.*;
import static vn.svframe.svarcade.verification.Checks.*;

public final class SessionChecks {
    private SessionChecks() { }
    private interface ReadValue { int value(); }
    private static final SessionServices.Key<ReadValue> VALUE = new SessionServices.Key<>(Id.of("test:value"), ReadValue.class);
    private static final Id FIRST = Id.of("test:first"), SECOND = Id.of("test:second");
    private static class System implements SessionSystem {
        int ticks, closes;
        public int stateSchema() { return 1; }
        public void start() { }
        public void tick(long tick) { ticks++; }
        public Map<String, Object> snapshot() { return Map.of(); }
        public void restore(int schema, Map<String, Object> state) { }
        public void close() { closes++; }
    }
    private static GenericSession session(ArenaRuntime arenas, Id... ids) {
        List<Definition.SystemSpec> specs = Arrays.stream(ids).map(id -> new Definition.SystemSpec(id, new Node(Map.of(), "test"))).toList();
        Definition d = new Definition(1, Id.of("test:game"), "test", true, 1, 1, Set.of(), specs, Map.of("arena", new Node(Map.of(), "arena")));
        return new GenericSession(UUID.randomUUID(), d, "arena", List.of(new Participant(UUID.randomUUID(), Participant.Kind.PLAYER, "team")), arenas, new ThreadGuard());
    }
    public static void main(String[] ignored) {
        ArenaRuntime arenas = new ArenaRuntime();
        GenericSession s = session(arenas, FIRST, SECOND);
        System provider = new System();
        SystemFactory first = new SystemFactory() {
            public Set<SessionServices.Key<?>> provides() { return Set.of(VALUE); }
            public SessionSystem create(GenericSession owner, Node config) { owner.services().provide(VALUE, () -> 7); return provider; }
        };
        SystemFactory second = new SystemFactory() {
            public Set<SessionServices.Key<?>> requires() { return Set.of(VALUE); }
            public SessionSystem create(GenericSession owner, Node config) { equal(7, owner.services().require(VALUE).value()); return new System(); }
        };
        Registry<SystemFactory> factories = new Registry<>(Map.of(FIRST, first, SECOND, second));
        s.start(factories); equal(7, s.services().require(VALUE).value());
        rejects(IllegalStateException.class, () -> s.services().provide(VALUE, () -> 8));
        rejects(java.util.concurrent.CompletionException.class, () -> CompletableFuture.runAsync(() -> s.services().require(VALUE)).join());
        s.close(); equal(0, s.services().size()); equal(1, provider.closes); equal(0, arenas.snapshot().size());
        rejects(IllegalStateException.class, () -> s.services().require(VALUE));
        rejects(IllegalStateException.class, s::changed);

        GenericSession wrong = session(arenas, SECOND, FIRST);
        rejects(ConfigException.class, () -> wrong.start(factories)); equal(GenericSession.Status.CLOSED, wrong.status());
        GenericSession missing = session(arenas, FIRST);
        System unpublished = new System();
        rejects(ConfigException.class, () -> missing.start(new Registry<>(Map.of(FIRST, new SystemFactory() {
            public Set<SessionServices.Key<?>> provides() { return Set.of(VALUE); }
            public SessionSystem create(GenericSession owner, Node config) { return unpublished; }
        })))); equal(1, unpublished.closes); equal(0, arenas.snapshot().size());

        GenericSession stopping = session(arenas, FIRST, SECOND); System later = new System();
        stopping.start(new Registry<>(Map.of(FIRST, (owner, cfg) -> new System() {
            public void tick(long tick) { owner.close(); }
            public void close() { owner.close(); super.close(); }
        }, SECOND, (owner, cfg) -> later)));
        stopping.tick(1); equal(0, later.ticks); equal(1, later.closes); equal(GenericSession.Status.CLOSED, stopping.status());
        stopping.close(); equal(1, later.closes); equal(0, arenas.snapshot().size());
        java.lang.System.out.println("SessionChecks: PASS (typed contracts, lifecycle, reentrant cleanup, tick-after-close, thread ownership)");
    }
}
