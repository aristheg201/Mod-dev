package vn.svframe.lively.integration;

import vn.svframe.lively.api.EconomyBridge;
import vn.svframe.lively.api.EconomyProvider;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Async adapter over the verified BEconomy 1.5 bridge. */
final class BEconomyProvider implements EconomyProvider {
    private final EconomyBridge bridge;
    private final ExecutorService worker;

    private BEconomyProvider(EconomyBridge bridge) {
        this.bridge = bridge;
        this.worker = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "Lively-BEconomy-IO");
            thread.setDaemon(true);
            return thread;
        });
    }

    static EconomyProvider create() { return new BEconomyProvider(BEconomyBridge.create()); }
    @Override public String id() { return "beconomy"; }
    @Override public boolean available() { return bridge.available(); }
    @Override public boolean supports(String currency) { return currency != null && !currency.isBlank(); }

    @Override public CompletableFuture<BigDecimal> balance(UUID actor, String currency) {
        return CompletableFuture.supplyAsync(() -> bridge.balance(actor, currency), worker);
    }

    @Override public CompletableFuture<Boolean> deposit(UUID actor, BigDecimal amount, String currency) {
        return CompletableFuture.supplyAsync(() -> {
            bridge.deposit(actor, amount, currency);
            return true;
        }, worker);
    }

    @Override public CompletableFuture<Boolean> withdraw(UUID actor, BigDecimal amount, String currency) {
        return CompletableFuture.supplyAsync(() -> bridge.withdraw(actor, amount, currency), worker);
    }

    @Override public CompletableFuture<Boolean> transfer(UUID from, UUID to, BigDecimal amount, String currency) {
        return CompletableFuture.supplyAsync(() -> bridge.transfer(from, to, amount, currency), worker);
    }

    @Override public void close() { worker.shutdown(); }
}
