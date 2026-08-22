package vn.svframe.lively.api;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Optional bridge for external gambling/minigame mods. Lively can simulate NPC gambling without one. */
public interface GamblingBridge extends AutoCloseable {
    record Outcome(boolean accepted, boolean won, BigDecimal payout, String reference, String reason) {}

    String id();
    boolean available();
    Set<String> games();
    default boolean supportsNpcActors() { return false; }
    CompletableFuture<Outcome> play(UUID actor, String game, BigDecimal stake, String currency);
    @Override default void close() {}
}
