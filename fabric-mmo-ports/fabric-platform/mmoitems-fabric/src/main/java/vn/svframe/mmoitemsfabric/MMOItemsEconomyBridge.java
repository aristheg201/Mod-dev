package vn.svframe.mmoitemsfabric;

import net.minecraft.server.network.ServerPlayerEntity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Locale;
import java.util.UUID;

/** Optional real-economy bridge used by crafting money conditions without creating a fake fallback currency. */
public final class MMOItemsEconomyBridge {
    private static final String[] API_CLASSES = {
            "org.beconomy.api.BEconomy",
            "org.beconomy.api.BEconomyAPI"
    };

    private MMOItemsEconomyBridge() {}

    public static boolean canAfford(ServerPlayerEntity player, double amount) {
        if (amount <= 0.0) return true;
        Double balance = balance(player);
        return balance != null && balance + 1.0e-9 >= amount;
    }

    public static boolean withdraw(ServerPlayerEntity player, double amount) {
        if (amount <= 0.0) return true;
        for (String className : API_CLASSES) {
            try {
                Class<?> type = Class.forName(className);
                for (Method method : type.getMethods()) {
                    String name = method.getName().toLowerCase(Locale.ROOT);
                    if (!(name.equals("withdraw") || name.equals("remove") || name.equals("take")
                            || name.equals("removebalance") || name.equals("subtract"))) continue;
                    if (method.getParameterCount() != 2) continue;
                    Object[] args = arguments(method.getParameterTypes(), player, amount);
                    if (args == null) continue;
                    Object result = method.invoke(target(type, method), args);
                    if (result instanceof Boolean bool) return bool;
                    return balance(player) != null;
                }
            } catch (ReflectiveOperationException ignored) { }
        }
        return false;
    }

    public static Double balance(ServerPlayerEntity player) {
        for (String className : API_CLASSES) {
            try {
                Class<?> type = Class.forName(className);
                for (Method method : type.getMethods()) {
                    String name = method.getName().toLowerCase(Locale.ROOT);
                    if (!(name.equals("balance") || name.equals("getbalance"))) continue;
                    if (method.getParameterCount() != 1) continue;
                    Object[] args = arguments(method.getParameterTypes(), player, 0.0);
                    if (args == null) continue;
                    Object result = method.invoke(target(type, method), args);
                    if (result instanceof Number number) return number.doubleValue();
                }
            } catch (ReflectiveOperationException ignored) { }
        }
        return null;
    }

    private static Object target(Class<?> type, Method method) throws ReflectiveOperationException {
        if (Modifier.isStatic(method.getModifiers())) return null;
        try {
            Field instance = type.getField("INSTANCE");
            return instance.get(null);
        } catch (NoSuchFieldException ignored) {
            var constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        }
    }

    private static Object[] arguments(Class<?>[] parameters, ServerPlayerEntity player, double amount) {
        Object[] args = new Object[parameters.length];
        boolean amountUsed = false;
        for (int i = 0; i < parameters.length; i++) {
            Class<?> parameter = wrap(parameters[i]);
            if (parameter == UUID.class) args[i] = player.getUuid();
            else if (parameter.isAssignableFrom(player.getClass())) args[i] = player;
            else if (parameter == String.class) args[i] = player.getUuidAsString();
            else if (Number.class.isAssignableFrom(parameter) && !amountUsed) {
                amountUsed = true;
                if (parameter == Integer.class) args[i] = (int) Math.round(amount);
                else if (parameter == Long.class) args[i] = Math.round(amount);
                else if (parameter == Float.class) args[i] = (float) amount;
                else args[i] = amount;
            } else return null;
        }
        return args;
    }

    private static Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == float.class) return Float.class;
        if (type == double.class) return Double.class;
        if (type == boolean.class) return Boolean.class;
        return type;
    }
}
