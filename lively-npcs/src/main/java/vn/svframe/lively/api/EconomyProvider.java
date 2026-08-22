package vn.svframe.lively.api;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Async optional economy provider contract. Core never imports a concrete economy mod.
 * Implementations must not block the Minecraft server thread while waiting on storage/network I/O.
 */
public interface EconomyProvider extends AutoCloseable {
    String id();
    boolean available();
    boolean supports(String currency);

    CompletableFuture<BigDecimal> balance(UUID actor, String currency);
    CompletableFuture<Boolean> deposit(UUID actor, BigDecimal amount, String currency);
    CompletableFuture<Boolean> withdraw(UUID actor, BigDecimal amount, String currency);
    CompletableFuture<Boolean> transfer(UUID from, UUID to, BigDecimal amount, String currency);

    @Override default void close() {}
}
