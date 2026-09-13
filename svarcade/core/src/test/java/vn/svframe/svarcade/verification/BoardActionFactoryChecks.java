package vn.svframe.svarcade.verification;

import java.util.*;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.runtime.StateMachineSystem;
import vn.svframe.svarcade.security.*;
import vn.svframe.svarcade.systems.board.*;
import vn.svframe.svarcade.systems.turn.TurnSystem;

public final class BoardActionFactoryChecks {
    private BoardActionFactoryChecks() { }
    public static void main(String[] args) {
        Registry<ActionHandlerFactory> handlers = BoardActionFactories.create();
        Node move = new Node(Map.of("effect", "test:moved", "allowed_states", List.of("PLAYING")), "move");
        ActionHandlerFactory moveFactory = handlers.require(BoardActionFactories.MOVE); moveFactory.validate(move);
        Checks.equal(Set.of(BoardSystem.ID, TurnSystem.ID, BoardAdjudicationSystem.ID, StateMachineSystem.ID), moveFactory.dependencies(move));
        Node command = new Node(Map.of("effect", "test:resigned", "command", "RESIGN"), "command");
        ActionHandlerFactory commandFactory = handlers.require(BoardActionFactories.COMMAND); commandFactory.validate(command);
        Checks.equal(Set.of(BoardSystem.ID, BoardAdjudicationSystem.ID), commandFactory.dependencies(command));
        Checks.rejects(ConfigException.class, () -> commandFactory.validate(new Node(Map.of("effect", "test:x", "command", "NOPE"), "bad")));

        Node actions = new Node(Map.of("rate_limit", Map.of("max_keys", 16, "capacity", 4, "per_tick", 1),
                "max_distance", 8, "event_capacity", 16,
                "actions", Map.of("chess:move", Map.of("handler", BoardActionFactories.MOVE.toString(), "config", move.values()),
                        "chess:resign", Map.of("handler", BoardActionFactories.COMMAND.toString(), "config", command.values()))), "actions");
        ActionSystem.Plan plan = new ActionSystem.Plan(handlers); plan.validate(actions);
        Checks.equal(Set.of(BoardSystem.ID, TurnSystem.ID, BoardAdjudicationSystem.ID, StateMachineSystem.ID), plan.dependencies(actions));
        System.out.println("BoardActionFactoryChecks passed");
    }
}
