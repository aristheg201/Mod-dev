package vn.svframe.svarcade.systems.board;

import java.util.*;
import vn.svframe.svarcade.runtime.StateChange;
import vn.svframe.svarcade.systems.board.MovementRules.Move;

public interface BoardAccess {
    record Entry(Move move, String key) { }
    record Archive(GridPosition initial, List<Entry> moves) {
        public Archive { Objects.requireNonNull(initial); moves = List.copyOf(moves); }
    }
    GridPosition position();
    StateChange prepareMove(UUID actor, Move move);
    long revision();
    int repetitions();
    int repetitions(String canonicalKey);
    List<Entry> history(int from, int maximum);
    Archive archive();
}
