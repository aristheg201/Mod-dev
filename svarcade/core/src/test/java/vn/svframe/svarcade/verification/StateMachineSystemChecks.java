package vn.svframe.svarcade.verification;

import java.util.*;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.runtime.*;

public final class StateMachineSystemChecks {
    private StateMachineSystemChecks() { }
    public static void main(String[] args) {
        StateMachineSystem.Plan plan = new StateMachineSystem.Plan(new Registry<>(Map.of()), new Registry<>(Map.of()));
        Node config = new Node(Map.of("initial", "ready", "data", Map.of(),
                "states", Map.of("ready", Map.of("terminal", true))), "fsm");
        plan.validate(config);
        SystemCatalog catalog = SystemCatalog.builder().add(StateMachineSystem.ID, plan).build();
        Definition definition = new Definition(1, Id.of("test:fsm"), "fingerprint", true, 1, 1, Set.of(),
                List.of(new Definition.SystemSpec(StateMachineSystem.ID, config)),
                Map.of("arena", new Node(Map.of("id", "arena"), "arena")));
        ThreadGuard thread = new ThreadGuard(); GenericSession session = new GenericSession(UUID.randomUUID(), definition, "arena",
                List.of(new Participant(UUID.randomUUID(), Participant.Kind.PLAYER, "one")), new ArenaRuntime(), thread);
        session.start(catalog.factories());
        StateMachineAccess fsm = session.services().require(StateMachineAccess.ACCESS);
        Checks.equal("ready", fsm.state()); Checks.equal(true, fsm.terminal()); Checks.equal(Map.of(), fsm.data());
        Checks.equal(false, fsm.event("none"));
        session.close(); Checks.equal(GenericSession.Status.CLOSED, session.status());
        System.out.println("StateMachineSystemChecks passed");
    }
}
