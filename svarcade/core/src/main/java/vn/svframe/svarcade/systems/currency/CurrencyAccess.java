package vn.svframe.svarcade.systems.currency;

import java.util.*;
import vn.svframe.svarcade.config.Id;

/** Match-local ledger. No server-economy provider is reachable through this capability. */
public interface CurrencyAccess {
    record Delta(UUID actor, Id currency, long amount) {
        public Delta { Objects.requireNonNull(actor); Objects.requireNonNull(currency); }
    }
    interface Change {
        /** Only the authoritative dispatcher may apply a prepared gameplay transaction. */
        void apply();
        void rollback();
    }
    long balance(UUID actor, Id currency);
    Change prepare(List<Delta> deltas);
    long revision();
}
