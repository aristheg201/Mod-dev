package io.github.aristheg201.cobblemonworld.shop;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PurchaseTransactionTest {
    private static final UUID PLAYER = UUID.randomUUID();
    private static class Wallet implements BeastCoinAccount {
        BigDecimal money; int debits, refunds; boolean throwAfterDebit;
        Wallet(int balance) { money = BigDecimal.valueOf(balance); }
        public BigDecimal balance(UUID id) { return money; }
        public boolean debit(UUID id, BigDecimal value) {
            if (money.compareTo(value) < 0) return false;
            money = money.subtract(value); debits++;
            if (throwAfterDebit) throw new IllegalStateException("API log dispatch failed");
            return true;
        }
        public void refund(UUID id, BigDecimal value) { money = money.add(value); refunds++; }
    }
    private static class Grant implements PurchaseTransaction.Grant {
        boolean space = true, fail; int items, commits;
        public boolean prepare() { return space; }
        public void commit() { items++; commits++; if (fail) throw new IllegalStateException("grant failed"); }
        public void rollback() { items = 0; }
    }
    @Test void zeroAndInsufficientNeverDebitOrGrant() {
        for (int starting : new int[] {0, 19}) {
            var wallet = new Wallet(starting); var grant = new Grant();
            assertEquals("funds", PurchaseTransaction.execute(wallet, PLAYER, 20, grant));
            assertEquals(0, wallet.debits); assertEquals(0, grant.commits);
        }
    }
    @Test void exactAndHigherBalanceChargeCanonicalAmountOnce() {
        for (int starting : new int[] {20, 80}) {
            var wallet = new Wallet(starting); var grant = new Grant();
            assertEquals("success", PurchaseTransaction.execute(wallet, PLAYER, 20, grant));
            assertEquals(BigDecimal.valueOf(starting - 20), wallet.money);
            assertEquals(1, wallet.debits); assertEquals(1, grant.items); assertEquals(0, wallet.refunds);
        }
    }
    @Test void inventoryFullDoesNotDebit() {
        var wallet = new Wallet(80); var grant = new Grant(); grant.space = false;
        assertEquals("inventory", PurchaseTransaction.execute(wallet, PLAYER, 20, grant));
        assertEquals(BigDecimal.valueOf(80), wallet.money); assertEquals(0, wallet.debits);
    }
    @Test void failedGrantRollsBackAndRefundsExactly() {
        var wallet = new Wallet(80); var grant = new Grant(); grant.fail = true;
        assertEquals("refunded", PurchaseTransaction.execute(wallet, PLAYER, 20, grant));
        assertEquals(BigDecimal.valueOf(80), wallet.money); assertEquals(1, wallet.debits);
        assertEquals(1, wallet.refunds); assertEquals(0, grant.items);
    }
    @Test void apiCanThrowAfterDebitWithoutLosingMoney() {
        var wallet = new Wallet(80); wallet.throwAfterDebit = true; var grant = new Grant();
        assertEquals("refunded", PurchaseTransaction.execute(wallet, PLAYER, 20, grant));
        assertEquals(BigDecimal.valueOf(80), wallet.money); assertEquals(1, wallet.refunds); assertEquals(0, grant.items);
    }
    @Test void rollbackFailureStillRefundsAndEscalatesToDisablePurchases() {
        var wallet = new Wallet(80);
        var grant = new Grant() {
            @Override public void commit() { super.commit(); throw new IllegalStateException("grant interrupted"); }
            @Override public void rollback() { throw new IllegalStateException("inventory unavailable"); }
        };
        assertThrows(IllegalStateException.class, () -> PurchaseTransaction.execute(wallet, PLAYER, 20, grant));
        assertEquals(BigDecimal.valueOf(80), wallet.money);
        assertEquals(1, wallet.refunds);
    }
}
