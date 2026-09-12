package vn.svframe.svarcade.systems.objective;

import java.util.*;
import vn.svframe.svarcade.config.Id;
import vn.svframe.svarcade.runtime.StateChange;

/** Generic match counters and immutable final outcome; no game lifecycle is encoded here. */
public interface ObjectiveAccess {
    record Result(Id reason, Set<String> winners, boolean draw) {
        public Result {
            Objects.requireNonNull(reason); winners = Set.copyOf(winners);
            if (draw && !winners.isEmpty()) throw new IllegalArgumentException("A drawn result cannot have winners");
        }
    }
    Set<Id> reasons();
    long value(Id counter);
    long revision();
    Optional<Result> result();
    StateChange prepare(Map<Id, Long> deltas, Optional<Result> finish);
}
