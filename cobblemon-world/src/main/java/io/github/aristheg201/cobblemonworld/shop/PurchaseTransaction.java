package io.github.aristheg201.cobblemonworld.shop;

import java.math.BigDecimal;
import java.util.UUID;

/** Runs on the server thread. Inventory preparation must have no side effects. */
public final class PurchaseTransaction {
    public interface Grant {
        boolean prepare();
        void commit();
        void rollback();
    }
    private PurchaseTransaction() {}

    public static String execute(BeastCoinAccount account, UUID player, int price, Grant grant) {
        if (price < 1) return "invalid";
        BigDecimal total = BigDecimal.valueOf(price);
        BigDecimal before = account.balance(player);
        if (before.compareTo(total) < 0) return "funds";
        if (!grant.prepare()) return "inventory";
        boolean debited = false;
        try {
            debited = account.debit(player, total);
            if (!debited) return "funds";
            if (account.balance(player).compareTo(before.subtract(total)) != 0)
                throw new IllegalStateException("BEconomy debit did not match canonical price");
            grant.commit();
            return "success";
        } catch (RuntimeException e) {
            RuntimeException rollbackFailure = null;
            try { grant.rollback(); } catch (RuntimeException rollback) { rollbackFailure = rollback; }
            // Some APIs can throw in event/log dispatch *after* changing the balance.
            BigDecimal after = account.balance(player);
            if (after.compareTo(before.subtract(total)) == 0) {
                account.refund(player, total);
                if (account.balance(player).compareTo(before) != 0)
                    throw new IllegalStateException("BeastCoin refund verification failed for " + player, e);
                if (rollbackFailure != null) {
                    e.addSuppressed(rollbackFailure);
                    throw new IllegalStateException("Inventory rollback failed after an exact BeastCoin refund; disable purchases for " + player, e);
                }
                return "refunded";
            }
            if (!debited && after.compareTo(before) == 0) return "economy";
            throw new IllegalStateException("Ambiguous economy change; disable purchases and investigate " + player, e);
        }
    }
}
