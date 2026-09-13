package vn.svframe.svarcade.verification;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.runtime.*;
import vn.svframe.svarcade.security.*;

public final class ActionSystemChecks {
    private ActionSystemChecks() { }

    private static final class PingFactory implements ActionHandlerFactory {
        private final AtomicInteger applied;
        PingFactory(AtomicInteger applied) { this.applied = applied; }
        @Override public void validate(Node config) { config.only("effect"); Id.of(config.string("effect")); }
        @Override public ActionDispatcher.Handler create(GenericSession session, Node config) {
            Id effect = Id.of(config.string("effect"));
            return new ActionDispatcher.Handler() {
                @Override public Optional<String> reject(GenericSession owner, IntentGate.Facts facts, IntentGate.Intent intent) { return Optional.empty(); }
                @Override public ActionDispatcher.Prepared prepare(GenericSession owner, IntentGate.Facts facts, IntentGate.Intent intent) {
                    if (!intent.payload().isEmpty()) throw new IllegalArgumentException("Unexpected ping payload");
                    return new ActionDispatcher.Prepared(applied::incrementAndGet, applied::decrementAndGet,
                            List.of(new ActionDispatcher.Effect(effect, Map.of("actor", facts.actor().toString()))));
                }
            };
        }
    }

    public static void main(String[] args) {
        AtomicInteger applied = new AtomicInteger();
        Registry<ActionHandlerFactory> handlers = new Registry.Builder<ActionHandlerFactory>()
                .add(Id.of("test:ping_handler"), new PingFactory(applied)).build();
        Node config = new Node(Map.of(
                "rate_limit", Map.of("max_keys", 16, "capacity", 4, "per_tick", 1),
                "max_distance", 8,
                "event_capacity", 8,
                "actions", Map.of("test:ping", Map.of("handler", "test:ping_handler", "config", Map.of("effect", "test:pong")))), "actions");
        ActionSystem.Plan plan = new ActionSystem.Plan(handlers); plan.validate(config);
        SystemCatalog catalog = SystemCatalog.builder().add(ActionSystem.ID, plan).build();
        Definition definition = new Definition(1, Id.of("test:actions"), "fingerprint", true, 1, 1, Set.of(),
                List.of(new Definition.SystemSpec(ActionSystem.ID, config)), Map.of("arena", new Node(Map.of("id", "arena"), "arena")));
        UUID actor = UUID.randomUUID(); ThreadGuard thread = new ThreadGuard(); ArenaRuntime arenas = new ArenaRuntime();
        GenericSession session = new GenericSession(UUID.randomUUID(), definition, "arena",
                List.of(new Participant(actor, Participant.Kind.PLAYER, "one")), arenas, thread);
        session.start(catalog.factories());
        ActionAccess actions = session.services().require(ActionSystem.ACCESS);
        Checks.equal(Set.of(Id.of("test:ping")), actions.actions());
        UUID token = actions.issueController(actor, 20);
        long revision = session.revision();
        IntentGate.Result result = actions.dispatchHuman(new IntentGate.Facts(actor, 1, 0, true),
                new IntentGate.Intent(session.id(), token, 0, revision, Id.of("test:ping"), Map.of()));
        Checks.equal(true, result.accepted()); Checks.equal(1, applied.get());
        List<ActionDispatcher.Event> events = actions.drainEvents(8); Checks.equal(1, events.size());
        Checks.equal(Id.of("test:pong"), events.getFirst().effect().type());
        Checks.equal(revision + 1, session.revision());
        session.close(); Checks.equal(GenericSession.Status.CLOSED, session.status());
        System.out.println("ActionSystemChecks passed");
    }
}
