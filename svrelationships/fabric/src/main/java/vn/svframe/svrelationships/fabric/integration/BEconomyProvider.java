package vn.svframe.svrelationships.fabric.integration;

import vn.svframe.svrelationships.integration.EconomyProvider;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.UUID;

public final class BEconomyProvider implements EconomyProvider {
    private final Object api;
    private final Method getBalance;
    private final Method subtractBalance;
    private final Method addBalance;
    private final Method setBalance;

    public BEconomyProvider() {
        try {
            Class<?> entrypoint = Class.forName("org.krripe.beconomy.api.BEconomy");
            this.api = entrypoint.getMethod("getAPI").invoke(null);
            Class<?> type = api.getClass();
            this.getBalance = type.getMethod("getBalance", UUID.class, String.class);
            this.subtractBalance = type.getMethod("subtractBalance", UUID.class, BigDecimal.class, String.class);
            this.addBalance = type.getMethod("addBalance", UUID.class, BigDecimal.class, String.class);
            this.setBalance = type.getMethod("setBalance", UUID.class, BigDecimal.class, String.class);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to bind BEconomy API", exception);
        }
    }

    @Override
    public String id() {
        return "beconomy";
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public BigDecimal balance(UUID playerId, String currencyId) {
        try {
            Object value = getBalance.invoke(api, playerId, currencyId);
            if (value instanceof BigDecimal decimal) {
                return decimal;
            }
            if (value instanceof Number number) {
                return BigDecimal.valueOf(number.doubleValue());
            }
            return new BigDecimal(String.valueOf(value));
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("BEconomy balance query failed", exception);
        }
    }

    @Override
    public boolean withdraw(UUID playerId, String currencyId, BigDecimal amount) {
        try {
            Object value = subtractBalance.invoke(api, playerId, amount, currencyId);
            return !(value instanceof Boolean result) || result;
        } catch (ReflectiveOperationException exception) {
            return false;
        }
    }

    @Override
    public boolean deposit(UUID playerId, String currencyId, BigDecimal amount) {
        return invokeMutation(addBalance, playerId, currencyId, amount);
    }

    @Override
    public boolean set(UUID playerId, String currencyId, BigDecimal amount) {
        return invokeMutation(setBalance, playerId, currencyId, amount);
    }

    private boolean invokeMutation(Method method, UUID playerId, String currencyId, BigDecimal amount) {
        try {
            Object value = method.invoke(api, playerId, amount, currencyId);
            return !(value instanceof Boolean result) || result;
        } catch (ReflectiveOperationException exception) {
            return false;
        }
    }
}
