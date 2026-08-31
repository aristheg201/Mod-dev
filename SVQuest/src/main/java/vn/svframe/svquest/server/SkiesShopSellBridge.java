package vn.svframe.svquest.server;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.svquest.SVQuest;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/** Restores the beta.5 SHOP_SELL objective from SkiesShop's real transaction event. */
public final class SkiesShopSellBridge {
    private final QuestEngine engine;
    public SkiesShopSellBridge(QuestEngine engine) { this.engine = engine; }

    public void install() {
        if (!FabricLoader.getInstance().isModLoaded("skiesshop")) return;
        try {
            Class<?> listener = Class.forName("com.pokeskies.skiesshop.utils.ShopTransactionEvent");
            Object event = listener.getField("EVENT").get(null);
            Object callback = Proxy.newProxyInstance(listener.getClassLoader(), new Class<?>[]{listener}, (proxy, method, args) -> {
                if ("execute".equals(method.getName()) && args != null && args.length >= 2 && args[0] instanceof ServerPlayerEntity player) {
                    Object transaction = args[1];
                    Object type = null;
                    try { type = transaction.getClass().getField("type").get(transaction); } catch (Throwable ignored) { }
                    if (type != null && "SELL".equalsIgnoreCase(String.valueOf(type))) engine.emit(player, "SHOP_SELL");
                }
                return enumOrDefault(method.getReturnType(), "PASS");
            });
            for (Method m : event.getClass().getMethods()) {
                if (m.getName().equals("register") && m.getParameterCount() == 1) { m.invoke(event, callback); break; }
            }
        } catch (Throwable t) {
            SVQuest.LOGGER.warn("SkiesShop SELL bridge disabled safely: {}", t.toString());
        }
    }

    private static Object enumOrDefault(Class<?> type, String name) {
        if (type == void.class) return null;
        if (type.isEnum()) {
            try { return Enum.valueOf((Class) type, name); } catch (Throwable ignored) { }
            Object[] values = type.getEnumConstants(); return values != null && values.length > 0 ? values[0] : null;
        }
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        return null;
    }
}
