package vn.svframe.lively.integration;

import net.minecraft.server.MinecraftServer;
import vn.svframe.lively.api.GamblingBridge;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Optional bridge verified against SVF All in One 0.1.5 public Tai Xiu API.
 *
 * <p>Actual wagers remain owned and settled by SVF All in One. Lively only submits a wager request and receives
 * the external bet receipt. NPC actors are deliberately not advertised as supported because CobbleDollars wagers
 * require an online player account and Lively must never mint or mirror player currency into synthetic NPC accounts.</p>
 */
final class SvfAllInOneGamblingBridge implements GamblingBridge {
    private static final Set<String> GAMES = Set.of("tai_xiu:tai", "tai_xiu:xiu", "taixiu:tai", "taixiu:xiu");

    private final Method runtime;
    private final Method runtimeServer;
    private final Method runtimeTaiXiu;
    private final Method sideParse;
    private final Method currencyParse;
    private final Method placeBet;
    private final Method receiptId;
    private final String initializationFailure;

    private SvfAllInOneGamblingBridge(Method runtime, Method runtimeServer, Method runtimeTaiXiu,
                                      Method sideParse, Method currencyParse, Method placeBet, Method receiptId,
                                      String initializationFailure) {
        this.runtime = runtime;
        this.runtimeServer = runtimeServer;
        this.runtimeTaiXiu = runtimeTaiXiu;
        this.sideParse = sideParse;
        this.currencyParse = currencyParse;
        this.placeBet = placeBet;
        this.receiptId = receiptId;
        this.initializationFailure = initializationFailure;
    }

    static GamblingBridge create() {
        try {
            Class<?> entry = Class.forName("com.svframe.svfallinone.SvfAllInOne");
            Class<?> runtimeType = Class.forName("com.svframe.svfallinone.SvfRuntime");
            Class<?> taiXiuType = Class.forName("com.svframe.svfallinone.service.TaiXiuService");
            Class<?> sideType = Class.forName("com.svframe.svfallinone.core.TaiXiuSide");
            Class<?> currencyType = Class.forName("com.svframe.svfallinone.core.CurrencyId");
            Class<?> receiptType = Class.forName("com.svframe.svfallinone.service.TaiXiuService$BetReceipt");
            return new SvfAllInOneGamblingBridge(
                    entry.getMethod("runtime"),
                    runtimeType.getMethod("server"),
                    runtimeType.getMethod("taiXiu"),
                    sideType.getMethod("parse", String.class),
                    currencyType.getMethod("parse", String.class),
                    taiXiuType.getMethod("placeBet", UUID.class, sideType, currencyType, BigInteger.class),
                    receiptType.getMethod("id"),
                    null);
        } catch (ReflectiveOperationException error) {
            return new SvfAllInOneGamblingBridge(null, null, null, null, null, null, null,
                    error.getClass().getSimpleName() + ":" + String.valueOf(error.getMessage()));
        }
    }

    @Override public String id() { return "svf_all_in_one"; }

    @Override
    public boolean available() {
        if (runtime == null) return false;
        try {
            Object current = runtime.invoke(null);
            return current != null && runtimeTaiXiu.invoke(current) != null && runtimeServer.invoke(current) instanceof MinecraftServer;
        } catch (ReflectiveOperationException error) {
            return false;
        }
    }

    @Override public Set<String> games() { return GAMES; }

    /** Real SVF Tai Xiu is player-economy backed. NPCs continue to use Lively's isolated virtual gambling economy. */
    @Override public boolean supportsNpcActors() { return false; }

    @Override
    public CompletableFuture<Outcome> play(UUID actor, String game, BigDecimal stake, String currency) {
        CompletableFuture<Outcome> future = new CompletableFuture<>();
        if (actor == null) return CompletableFuture.completedFuture(rejected("actor_required"));
        String side = side(game);
        if (side == null) return CompletableFuture.completedFuture(rejected("game_must_include_side_tai_or_xiu"));
        if (stake == null || stake.signum() <= 0) return CompletableFuture.completedFuture(rejected("invalid_stake"));
        final BigInteger amount;
        try { amount = stake.toBigIntegerExact(); }
        catch (ArithmeticException error) { return CompletableFuture.completedFuture(rejected("fractional_stake_not_supported")); }
        if (runtime == null) return CompletableFuture.completedFuture(rejected("bridge_unavailable:" + initializationFailure));

        try {
            Object current = runtime.invoke(null);
            if (current == null) return CompletableFuture.completedFuture(rejected("svf_runtime_not_started"));
            Object serverValue = runtimeServer.invoke(current);
            if (!(serverValue instanceof MinecraftServer server)) return CompletableFuture.completedFuture(rejected("svf_server_unavailable"));
            server.execute(() -> executeBet(future, actor, side, currency, amount));
            return future;
        } catch (ReflectiveOperationException error) {
            return CompletableFuture.completedFuture(rejected(reason(error)));
        }
    }

    private void executeBet(CompletableFuture<Outcome> future, UUID actor, String side, String currency, BigInteger amount) {
        try {
            Object current = runtime.invoke(null);
            if (current == null) { future.complete(rejected("svf_runtime_not_started")); return; }
            Object service = runtimeTaiXiu.invoke(current);
            if (service == null) { future.complete(rejected("taixiu_service_unavailable")); return; }
            Object sideValue = sideParse.invoke(null, side);
            Object currencyValue = currencyParse.invoke(null, normalizeCurrency(currency));
            Object receipt = placeBet.invoke(service, actor, sideValue, currencyValue, amount);
            String reference = String.valueOf(receiptId.invoke(receipt));
            future.complete(new Outcome(true, false, BigDecimal.ZERO, reference, "accepted_pending_external_settlement"));
        } catch (ReflectiveOperationException | RuntimeException error) {
            future.complete(rejected(reason(error)));
        }
    }

    private static Outcome rejected(String reason) {
        return new Outcome(false, false, BigDecimal.ZERO, "", reason == null || reason.isBlank() ? "rejected" : reason);
    }

    private static String side(String game) {
        if (game == null) return null;
        String normalized = game.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        if (normalized.equals("tai") || normalized.endsWith(":tai")) return "tai";
        if (normalized.equals("xiu") || normalized.endsWith(":xiu")) return "xiu";
        return null;
    }

    private static String normalizeCurrency(String currency) {
        String value = currency == null ? "beastcoin" : currency.trim().toLowerCase(Locale.ROOT);
        return value.isBlank() ? "beastcoin" : value;
    }

    private static String reason(Throwable error) {
        Throwable root = error;
        while (root instanceof InvocationTargetException invocation && invocation.getCause() != null) root = invocation.getCause();
        String message = root.getMessage();
        return root.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ":" + message);
    }
}
