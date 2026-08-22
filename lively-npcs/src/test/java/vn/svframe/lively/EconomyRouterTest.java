package vn.svframe.lively;

import org.junit.jupiter.api.Test;
import vn.svframe.lively.api.EconomyProvider;
import vn.svframe.lively.economy.EconomyRouter;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

final class EconomyRouterTest {
    @Test void routesCurrenciesAcrossMultipleProvidersWithoutHardDependency() {
        EconomyRouter router = new EconomyRouter();
        FakeProvider beconomy = new FakeProvider("beconomy", true);
        FakeProvider cobble = new FakeProvider("cobbledollars", true);
        router.register(beconomy);
        router.register(cobble);
        router.route("beastcoin", "beconomy");
        router.route("cobbledollar", "cobbledollars");
        UUID player = UUID.randomUUID();
        beconomy.balances.put(player, new BigDecimal("100"));
        cobble.balances.put(player, new BigDecimal("50"));

        assertEquals("beconomy", router.resolve("beastcoin").orElseThrow().providerId());
        assertEquals("cobbledollars", router.resolve("cobbledollar").orElseThrow().providerId());
        assertTrue(router.withdraw(player, new BigDecimal("25"), "beastcoin").join());
        assertTrue(router.deposit(player, new BigDecimal("10"), "cobbledollar").join());
        assertEquals(new BigDecimal("75"), beconomy.balances.get(player));
        assertEquals(new BigDecimal("60"), cobble.balances.get(player));
    }

    private static final class FakeProvider implements EconomyProvider {
        private final String id;
        private final boolean available;
        private final ConcurrentHashMap<UUID, BigDecimal> balances = new ConcurrentHashMap<>();
        private FakeProvider(String id, boolean available) { this.id = id; this.available = available; }
        @Override public String id() { return id; }
        @Override public boolean available() { return available; }
        @Override public boolean supports(String currency) { return true; }
        @Override public CompletableFuture<BigDecimal> balance(UUID actor, String currency) { return CompletableFuture.completedFuture(balances.getOrDefault(actor, BigDecimal.ZERO)); }
        @Override public CompletableFuture<Boolean> deposit(UUID actor, BigDecimal amount, String currency) {
            balances.merge(actor, amount, BigDecimal::add); return CompletableFuture.completedFuture(true);
        }
        @Override public CompletableFuture<Boolean> withdraw(UUID actor, BigDecimal amount, String currency) {
            BigDecimal current = balances.getOrDefault(actor, BigDecimal.ZERO);
            if (current.compareTo(amount) < 0) return CompletableFuture.completedFuture(false);
            balances.put(actor, current.subtract(amount)); return CompletableFuture.completedFuture(true);
        }
        @Override public CompletableFuture<Boolean> transfer(UUID from, UUID to, BigDecimal amount, String currency) {
            if (!withdraw(from, amount, currency).join()) return CompletableFuture.completedFuture(false);
            deposit(to, amount, currency); return CompletableFuture.completedFuture(true);
        }
    }
}
