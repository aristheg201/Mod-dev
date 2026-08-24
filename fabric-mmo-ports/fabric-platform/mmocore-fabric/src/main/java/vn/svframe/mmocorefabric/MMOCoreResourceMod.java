package vn.svframe.mmocorefabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import vn.svframe.mythiclibfabric.runtime.RpgProfileRegistry;
import vn.svframe.mythiclibfabric.runtime.RpgResourceRegistry;

import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Native live RPG resources used by MMOCore skills and MMOItems crafting conditions. */
public final class MMOCoreResourceMod implements ModInitializer {
    private static final Map<UUID, Map<String, Double>> CURRENT = new ConcurrentHashMap<>();
    private static final String[] RESOURCES = {"mana", "stamina", "stellium"};
    private static long ticks;

    @Override
    public void onInitialize() {
        RpgResourceRegistry.register(new RpgResourceRegistry.Provider() {
            @Override public OptionalDouble current(UUID player, String resource) {
                if (!supported(resource)) return OptionalDouble.empty();
                return OptionalDouble.of(resourceMap(player).computeIfAbsent(resource, ignored -> maximumValue(player, resource)));
            }
            @Override public OptionalDouble maximum(UUID player, String resource) {
                return supported(resource) ? OptionalDouble.of(maximumValue(player, resource)) : OptionalDouble.empty();
            }
            @Override public boolean add(UUID player, String resource, double amount) {
                if (!supported(resource)) return false;
                Map<String, Double> values = resourceMap(player);
                double max = maximumValue(player, resource);
                double current = values.computeIfAbsent(resource, ignored -> max);
                values.put(resource, Math.max(0.0, Math.min(max, current + amount)));
                return true;
            }
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> initialize(handler.getPlayer().getUuid()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> CURRENT.remove(handler.getPlayer().getUuid()));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (++ticks % 20L != 0L) return;
            for (var player : server.getPlayerManager().getPlayerList()) regenerate(player.getUuid());
        });
    }

    public static double current(UUID player, String resource) {
        String key = normalize(resource);
        if (!supported(key)) return 0.0;
        return resourceMap(player).computeIfAbsent(key, ignored -> maximumValue(player, key));
    }

    public static double maximum(UUID player, String resource) {
        String key = normalize(resource);
        return supported(key) ? maximumValue(player, key) : 0.0;
    }

    public static void set(UUID player, String resource, double value) {
        String key = normalize(resource);
        if (!supported(key)) return;
        resourceMap(player).put(key, Math.max(0.0, Math.min(maximumValue(player, key), value)));
    }

    private static void initialize(UUID player) {
        Map<String, Double> values = resourceMap(player);
        for (String resource : RESOURCES) values.put(resource, maximumValue(player, resource));
    }

    private static void regenerate(UUID player) {
        Map<String, Double> values = resourceMap(player);
        for (String resource : RESOURCES) {
            double max = maximumValue(player, resource);
            double current = values.computeIfAbsent(resource, ignored -> max);
            double regen = attribute(player, resource + "_regeneration", 0.0);
            if (regen > 0.0 && current < max) values.put(resource, Math.min(max, current + regen));
            else if (current > max) values.put(resource, max);
        }
    }

    private static Map<String, Double> resourceMap(UUID player) {
        return CURRENT.computeIfAbsent(player, ignored -> new ConcurrentHashMap<>());
    }

    private static double maximumValue(UUID player, String resource) {
        return Math.max(0.0, attribute(player, "max_" + resource, 20.0));
    }

    private static double attribute(UUID player, String key, double fallback) {
        RpgProfileRegistry.Snapshot snapshot = RpgProfileRegistry.mergeOrDefault(player);
        for (Map.Entry<String, Double> entry : snapshot.attributes().entrySet()) {
            if (normalize(entry.getKey()).equals(key)) return entry.getValue();
        }
        return fallback;
    }

    private static boolean supported(String resource) {
        for (String value : RESOURCES) if (value.equals(resource)) return true;
        return false;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }
}
