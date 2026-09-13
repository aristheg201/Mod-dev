package vn.svframe.svarcade.verification;

import java.util.*;
import vn.svframe.svarcade.bot.BotDecisionSource;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.runtime.*;
import vn.svframe.svarcade.security.IntentGate;
import vn.svframe.svarcade.systems.board.*;

public final class BoardBotSourcePlanChecks {
    private BoardBotSourcePlanChecks() { }
    public static void main(String[] args) {
        BoardDecisionSource.Plan plan = new BoardDecisionSource.Plan(BoardBotStrategy.builtins(),
                session -> (actor, tick) -> new IntentGate.Facts(actor, tick, 0, true));
        Node config = new Node(Map.of("actions", Map.of("move", "chess:move", "claim_repetition", "chess:claim_repetition",
                        "claim_quiet", "chess:claim_quiet", "accept_draw", "chess:accept_draw"),
                "allowed_states", List.of("PLAYING")), "board-bot-source");
        plan.validate(config);
        Checks.equal(Set.of(BoardSystem.ID, MovementSystem.ID, BoardAdjudicationSystem.ID, StateMachineSystem.ID), plan.dependencies(config));
        Checks.equal(Set.of(BoardSystem.ACCESS, MovementSystem.ACCESS, BoardAdjudicationSystem.ACCESS, StateMachineAccess.ACCESS), plan.requires(config));
        Checks.equal(Set.of(BotDecisionSource.ACCESS), plan.provides());
        System.out.println("BoardBotSourcePlanChecks passed");
    }
}
