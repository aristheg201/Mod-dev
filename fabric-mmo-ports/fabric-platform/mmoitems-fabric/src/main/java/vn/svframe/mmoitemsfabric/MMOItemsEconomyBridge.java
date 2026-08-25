package vn.svframe.mmoitemsfabric;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Optional BEconomy bridge using its public API without making BEconomy a hard dependency. */
public final class MMOItemsEconomyBridge {
    private static final Logger LOG = Logger.getLogger("MMOItems-Fabric/Economy");

    private MMOItemsEconomyBridge() {}

    public static boolean canAfford(ServerPlayerEntity player, double amount) {
        if (amount <= 0.0) return true;
        if (player == null || !FabricLoader.getInstance().isModLoaded("beconomy")) return false;
        try {
            Access access = access();
            Object result = access.hasEnoughFunds.invoke(access.api, player.getUuid(), BigDecimal.valueOf(amount), access.currency);
            return Boolean.TRUE.equals(result);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            LOG.log(Level.FINE, "BEconomy balance check failed", exception);
            return false;
        }
    }

    public static boolean withdraw(ServerPlayerEntity player, double amount) {
        if (amount <= 0.0) return true;
        if (player == null || !FabricLoader.getInstance().isModLoaded("beconomy")) return false;
        try {
            Access access = access();
            Object enough = access.hasEnoughFunds.invoke(access.api, player.getUuid(), BigDecimal.valueOf(amount), access.currency);
            if (!Boolean.TRUE.equals(enough)) return false;
            access.subtractBalance.invoke(access.api, player.getUuid(), BigDecimal.valueOf(amount), access.currency);
            return true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            LOG.log(Level.FINE, "BEconomy withdrawal failed", exception);
            return false;
        }
    }

    private static Access access() throws ReflectiveOperationException {
        Class<?> beconomy = Class.forName("org.beconomy.api.BEconomy");
        Field instanceField = beconomy.getField("INSTANCE");
        Object instance = instanceField.get(null);
        Object api = beconomy.getMethod("getAPI").invoke(instance);
        Object primaryCurrency = api.getClass().getMethod("getPrimaryCurrency").invoke(api);
        String currency = String.valueOf(primaryCurrency.getClass().getMethod("getCurrencyType").invoke(primaryCurrency));
        Method hasEnoughFunds = api.getClass().getMethod("hasEnoughFunds", UUID.class, BigDecimal.class, String.class);
        Method subtractBalance = api.getClass().getMethod("subtractBalance", UUID.class, BigDecimal.class, String.class);
        return new Access(api, currency, hasEnoughFunds, subtractBalance);
    }

    private record Access(Object api, String currency, Method hasEnoughFunds, Method subtractBalance) {}
}
