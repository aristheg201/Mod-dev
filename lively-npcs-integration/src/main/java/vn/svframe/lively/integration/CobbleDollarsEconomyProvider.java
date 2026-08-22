package vn.svframe.lively.integration;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.lively.api.EconomyProvider;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Optional CobbleDollars 2.0 adapter verified against CobbleDollarsPlayer.
 * Runtime operations deliberately use online player state only; no offline account file is touched from a tick path.
 */
final class CobbleDollarsEconomyProvider implements EconomyProvider {
    private final MinecraftServer server;
    private final Method getBalance;
    private final Method setBalance;
    private final boolean usable;

    private CobbleDollarsEconomyProvider(MinecraftServer server, Method getBalance, Method setBalance, boolean usable) {
        this.server = server;
        this.getBalance = getBalance;
        this.setBalance = setBalance;
        this.usable = usable;
    }

    static EconomyProvider create(MinecraftServer server) {
        try {
            Class<?> playerApi = Class.forName("fr.harmex.cobbledollars.common.utils.CobbleDollarsPlayer");
            return new CobbleDollarsEconomyProvider(server,
                    playerApi.getMethod("cobbleDollars$getCobbleDollars"),
                    playerApi.getMethod("cobbleDollars$setCobbleDollars", BigInteger.class), true);
        } catch (ReflectiveOperationException error) {
            return new CobbleDollarsEconomyProvider(server, null, null, false);
        }
    }

    @Override public String id() { return "cobbledollars"; }
    @Override public boolean available() { return usable; }
    @Override public boolean supports(String currency) {
        if (!usable || currency == null) return false;
        String normalized = currency.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("cobbledollar") || normalized.equals("cobbledollars") || normalized.equals("cd");
    }

    @Override public CompletableFuture<BigDecimal> balance(UUID actor, String currency) {
        return onServer(() -> new BigDecimal(read(player(actor))));
    }

    @Override public CompletableFuture<Boolean> deposit(UUID actor, BigDecimal amount, String currency) {
        BigInteger delta = exact(amount);
        if (delta == null || delta.signum() <= 0) return CompletableFuture.completedFuture(false);
        return onServer(() -> {
            ServerPlayerEntity player = player(actor);
            write(player, read(player).add(delta));
            return true;
        });
    }

    @Override public CompletableFuture<Boolean> withdraw(UUID actor, BigDecimal amount, String currency) {
        BigInteger delta = exact(amount);
        if (delta == null || delta.signum() <= 0) return CompletableFuture.completedFuture(false);
        return onServer(() -> {
            ServerPlayerEntity player = player(actor);
            BigInteger balance = read(player);
            if (balance.compareTo(delta) < 0) return false;
            write(player, balance.subtract(delta));
            return true;
        });
    }

    @Override public CompletableFuture<Boolean> transfer(UUID from, UUID to, BigDecimal amount, String currency) {
        BigInteger delta = exact(amount);
        if (delta == null || delta.signum() <= 0 || from.equals(to)) return CompletableFuture.completedFuture(false);
        return onServer(() -> {
            ServerPlayerEntity source = player(from);
            ServerPlayerEntity target = player(to);
            BigInteger sourceBalance = read(source);
            if (sourceBalance.compareTo(delta) < 0) return false;
            write(source, sourceBalance.subtract(delta));
            write(target, read(target).add(delta));
            return true;
        });
    }

    private ServerPlayerEntity player(UUID id) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
        if (player == null) throw new IllegalStateException("CobbleDollars account is offline: " + id);
        return player;
    }

    private BigInteger read(ServerPlayerEntity player) {
        try { return (BigInteger) getBalance.invoke(player); }
        catch (ReflectiveOperationException error) { throw new IllegalStateException("CobbleDollars balance failed", error); }
    }

    private void write(ServerPlayerEntity player, BigInteger value) {
        try { setBalance.invoke(player, value.max(BigInteger.ZERO)); }
        catch (ReflectiveOperationException error) { throw new IllegalStateException("CobbleDollars balance update failed", error); }
    }

    private <T> CompletableFuture<T> onServer(Supplier<T> operation) {
        CompletableFuture<T> future = new CompletableFuture<>();
        server.execute(() -> {
            try { future.complete(operation.get()); }
            catch (Throwable error) { future.completeExceptionally(error); }
        });
        return future;
    }

    private static BigInteger exact(BigDecimal amount) {
        if (amount == null) return null;
        try { return amount.toBigIntegerExact(); }
        catch (ArithmeticException ignored) { return null; }
    }
}
