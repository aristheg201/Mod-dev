package vn.svarcade.tcg.performance;
import vn.svarcade.tcg.duel.Duel;
import vn.svarcade.tcg.data.Catalog;
import java.util.*;
/** AI receives only its visible, immutable read model; it cannot access the mutable Duel. */
public record DuelReadSnapshot(String duelId,long revision,Duel.View view,Map<String,Catalog.Card> definitions) {
 public DuelReadSnapshot {Objects.requireNonNull(view);if(revision!=view.revision())throw new IllegalArgumentException("Snapshot revision mismatch");}
}
