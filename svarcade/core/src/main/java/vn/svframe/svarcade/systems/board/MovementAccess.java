package vn.svframe.svarcade.systems.board;

import java.util.List;
import vn.svframe.svarcade.systems.board.MovementRules.*;

/** Session-facing rule contract; worker search receives the immutable definition instead. */
public interface MovementAccess {
    MovementDefinition definition();
    void validate(GridPosition position);
    List<Successor> successors(GridPosition position);
    GridPosition apply(GridPosition position, Move move);
    boolean threatened(GridPosition position, String team);
    String repetitionKey(GridPosition position);
}
