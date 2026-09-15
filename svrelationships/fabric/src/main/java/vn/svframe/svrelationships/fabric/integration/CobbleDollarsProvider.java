package vn.svframe.svrelationships.fabric.integration;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.svrelationships.integration.EconomyProvider;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CobbleDollarsProvider implements EconomyProvider {
    private final MinecraftServer server;
    private final ConcurrentHashMap<Class<?>, Accessors> accessors = new ConcurrentHashMap<>();

    public CobbleDollarsProvider(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public String id() {
        return "cobbledollars";
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public BigDecimal balance(UUID playerId, String currencyId) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
        if (player == null) {
            return BigDecimal.ZERO;
        }
        try {
            Object value = access(player).getter().invoke(player);
            if (value instanceof BigInteger integer) {
                return new BigDecimal(integer);
            }
            if (value instanceof Number number) {
                return BigDecimal.valueOf(number.longValue());
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return BigDecimal.ZERO;
    }

    @Override
    public boolean withdraw(UUID playerId, String currencyId, BigDecimal amount) {
        BigInteger integer = integerAmount(amount);
        if (integer == null) {
            return false;
        }
        BigDecimal current = balance(playerId, currencyId);
        if (current.compareTo(amount) < 0) {
            return false;
        }
        return set(playerId, currencyId, current.subtract(amount));
    }

    @Override
    public boolean deposit(UUID playerId, String currencyId, BigDecimal amount) {
        if (integerAmount(amount) == null) {
            return false;
        }
        return set(playerId, currencyId, balance(playerId, currencyId).add(amount));
    }

    @Override
    public boolean set(UUID playerId, String currencyId, BigDecimal amount) {
        BigInteger integer = integerAmount(amount);
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
        if (integer == null || player == null || integer.signum() < 0) {
            return false;
        }
        try {
            Method setter = access(player).setter();
            Class<?> parameter = setter.getParameterTypes()[0];
            if (parameter == BigInteger.class) {
                setter.invoke(player, integer);
            } else if (parameter == long.class || parameter == Long.class) {
                setter.invoke(player, integer.longValueExact());
            } else if (parameter == int.class || parameter == Integer.class) {
                setter.invoke(player, integer.intValueExact());
            } else {
                return false;
            }
            return true;
        } catch (ReflectiveOperationException | ArithmeticException exception) {
            return false;
        }
    }

    private Accessors access(ServerPlayerEntity player) {
        return accessors.computeIfAbsent(player.getClass(), CobbleDollarsProvider::resolveAccessors);
    }

    private static Accessors resolveAccessors(Class<?> type) {
        Method getter = first(type, new Signature("getCobbleDollars"), new Signature("cobbleDollars$getCobbleDollars"));
        Method setter = first(type,
                new Signature("setCobbleDollars", BigInteger.class),
                new Signature("cobbleDollars$setCobbleDollars", BigInteger.class),
                new Signature("setCobbleDollars", long.class),
                new Signature("cobbleDollars$setCobbleDollars", long.class),
                new Signature("setCobbleDollars", int.class),
                new Signature("cobbleDollars$setCobbleDollars", int.class));
        if (getter == null || setter == null) {
            throw new IllegalStateException("CobbleDollars player accessors are unavailable");
        }
        return new Accessors(getter, setter);
    }

    private static Method first(Class<?> type, Signature... signatures) {
        for (Signature signature : signatures) {
            try {
                Method method = type.getMethod(signature.name(), signature.parameters());
                method.setAccessible(true);
                return method;
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return null;
    }

    private static BigInteger integerAmount(BigDecimal value) {
        try {
            return value.toBigIntegerExact();
        } catch (ArithmeticException exception) {
            return null;
        }
    }

    private record Signature(String name, Class<?>... parameters) {
    }

    private record Accessors(Method getter, Method setter) {
    }
}
