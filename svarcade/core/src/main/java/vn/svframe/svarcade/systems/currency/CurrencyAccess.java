package vn.svframe.svarcade.systems.currency;

import java.util.*;
import vn.svframe.svarcade.config.Id;
import vn.svframe.svarcade.runtime.StateChange;

/** Match-local ledger. No server-economy provider is reachable through this capability. */
public interface CurrencyAccess {
    record Delta(UUID actor, Id currency, long amount) {
        public Delta { Objects.requireNonNull(actor); Objects.requireNonNull(currency); }
    }
    interface Change extends StateChange { }
    long balance(UUID actor, Id currency);
    Change prepare(List<Delta> deltas);
    long revision();
}
