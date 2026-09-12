package vn.svframe.svarcade.systems.board;

import java.util.*;
import vn.svframe.svarcade.runtime.StateChange;
import vn.svframe.svarcade.systems.board.MovementRules.Move;

public interface AdjudicationAccess {
    enum Command { OFFER_DRAW, ACCEPT_DRAW, DECLINE_DRAW, RESIGN, CLAIM_REPETITION, CLAIM_QUIET }
    record MovePlan(List<StateChange> changes, boolean finishes) {
        public MovePlan { changes=List.copyOf(changes); }
    }
    boolean mayPlay();
    Optional<String> offeredBy();
    BoardOutcomeRules.Config rules();
    MovePlan prepareAfterMove(UUID actor, GridPosition result);
    List<StateChange> prepareCommand(UUID actor, Command command, Optional<Move> intended);
}
