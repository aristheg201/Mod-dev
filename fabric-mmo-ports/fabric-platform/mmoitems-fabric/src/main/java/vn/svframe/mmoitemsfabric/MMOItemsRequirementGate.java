package vn.svframe.mmoitemsfabric;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.biome.Biome;
import vn.svframe.compat.YamlLite;
import vn.svframe.mythiclibfabric.runtime.RpgProfileRegistry;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Enforces legacy MMOItems item requirements before any server-side item effect is fired. */
public final class MMOItemsRequirementGate {
    private static final Path ITEM_ROOT = FabricLoader.getInstance().getConfigDir().resolve("MMOItems").resolve("item");
    private static final Map<String, Cached> CACHE = new ConcurrentHashMap<>();
    private static final List<String> ATTRIBUTES = List.of("strength", "dexterity", "intelligence", "power");

    private MMOItemsRequirementGate() {}

    public static boolean canUse(ServerPlayerEntity player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()) return false;
        MMOItemsGameplayMod.Template template = MMOItemsGameplayMod.template(stack);
        if (template == null) return true;
        Requirement requirement = requirement(template);
        RpgProfileRegistry.Snapshot profile = RpgProfileRegistry.mergeOrDefault(player.getUuid());

        if (profile.level() < requirement.level()) return false;
        if (!requirement.classes().isEmpty() && requirement.classes().stream().noneMatch(value -> value.equalsIgnoreCase(profile.playerClass()))) return false;
        for (Map.Entry<String, Double> entry : requirement.attributes().entrySet()) {
            if (profile.attributes().getOrDefault(entry.getKey(), 0.0) + 1.0e-9 < entry.getValue()) return false;
        }
        if (!requirement.permission().isEmpty() && !hasPermission(player, requirement.permission())) return false;
        if (!requirement.biomes().isEmpty()) {
            RegistryKey<Biome> biome = player.getWorld().getBiome(player.getBlockPos()).getKey().orElse(null);
            String id = biome == null ? "" : biome.getValue().toString().toLowerCase(Locale.ROOT);
            String path = biome == null ? "" : biome.getValue().getPath().toLowerCase(Locale.ROOT);
            if (!requirement.biomes().contains(id) && !requirement.biomes().contains(path)) return false;
        }
        return true;
    }

    public static String gemUpgradeScaling(ItemStack stack) {
        MMOItemsGameplayMod.Template template = MMOItemsGameplayMod.template(stack);
        return template == null ? "" : requirement(template).gemUpgradeScaling();
    }

    private static Requirement requirement(MMOItemsGameplayMod.Template template) {
        String key = template.type().toUpperCase(Locale.ROOT) + ':' + template.id().toUpperCase(Locale.ROOT);
        Path file = ITEM_ROOT.resolve(template.type().toLowerCase(Locale.ROOT) + ".yml");
        long stamp = -1L;
        try { if (Files.isRegularFile(file)) stamp = Files.getLastModifiedTime(file).toMillis(); } catch (Exception ignored) { }
        Cached cached = CACHE.get(key);
        if (cached != null && cached.stamp() == stamp) return cached.requirement();
        Requirement loaded = load(file, template.id());
        CACHE.put(key, new Cached(stamp, loaded));
        return loaded;
    }

    private static Requirement load(Path file, String itemId) {
        if (!Files.isRegularFile(file)) return Requirement.EMPTY;
        try {
            Map<String, Object> root = YamlLite.map(YamlLite.parse(file));
            Object rawSection = findIgnoreCase(root, itemId);
            Map<String, Object> section = map(rawSection);
            Map<String, Object> base = map(findIgnoreCase(section, "base"));
            int level = Math.max(0, (int) Math.ceil(number(findIgnoreCase(base, "required-level"), 0.0)));
            Set<String> classes = normalizedSet(findIgnoreCase(base, "required-class"));
            if (classes.isEmpty()) classes = normalizedSet(findIgnoreCase(base, "required-classes"));
            Map<String, Double> attributes = new LinkedHashMap<>();
            for (String attribute : ATTRIBUTES) {
                double required = number(findIgnoreCase(base, "required-" + attribute), 0.0);
                if (required > 0) attributes.put(attribute, required);
            }
            String permission = string(findIgnoreCase(base, "required-permission"));
            Set<String> biomes = normalizedSet(findIgnoreCase(base, "required-biome"));
            if (biomes.isEmpty()) biomes = normalizedSet(findIgnoreCase(base, "required-biomes"));
            String scaling = string(findIgnoreCase(base, "gem-upgrade-scaling")).toUpperCase(Locale.ROOT);
            return new Requirement(level, classes, Map.copyOf(attributes), permission, biomes, scaling);
        } catch (Exception ignored) {
            return Requirement.EMPTY;
        }
    }

    private static boolean hasPermission(ServerPlayerEntity player, String permission) {
        if (permission == null || permission.isBlank()) return true;
        if (FabricLoader.getInstance().isModLoaded("luckperms")) {
            try {
                Class<?> provider = Class.forName("net.luckperms.api.LuckPermsProvider");
                Object api = provider.getMethod("get").invoke(null);
                Object userManager = api.getClass().getMethod("getUserManager").invoke(api);
                Object user = userManager.getClass().getMethod("getUser", java.util.UUID.class).invoke(userManager, player.getUuid());
                if (user != null) {
                    Object cachedData = user.getClass().getMethod("getCachedData").invoke(user);
                    Object permissionData = cachedData.getClass().getMethod("getPermissionData").invoke(cachedData);
                    Object result = permissionData.getClass().getMethod("checkPermission", String.class).invoke(permissionData, permission);
                    Method asBoolean = result.getClass().getMethod("asBoolean");
                    return Boolean.TRUE.equals(asBoolean.invoke(result));
                }
            } catch (ReflectiveOperationException ignored) { }
        }
        return player.hasPermissionLevel(2);
    }

    private static Object findIgnoreCase(Map<String, Object> map, String key) {
        Object direct = map.get(key);
        if (direct != null) return direct;
        for (Map.Entry<String, Object> entry : map.entrySet()) if (entry.getKey().equalsIgnoreCase(key)) return entry.getValue();
        return null;
    }

    private static double number(Object value, double fallback) {
        if (value instanceof Number number) return number.doubleValue();
        Map<String, Object> section = map(value);
        if (!section.isEmpty()) return number(findIgnoreCase(section, "base"), fallback);
        try { return Double.parseDouble(String.valueOf(value).trim()); } catch (Exception ignored) { return fallback; }
    }

    private static Set<String> normalizedSet(Object value) {
        Set<String> result = new LinkedHashSet<>();
        if (value instanceof Collection<?> collection) for (Object entry : collection) add(result, entry);
        else if (value != null) for (String entry : String.valueOf(value).split("[,;]")) add(result, entry);
        return Set.copyOf(result);
    }

    private static void add(Set<String> result, Object value) {
        String normalized = string(value).toLowerCase(Locale.ROOT);
        if (!normalized.isEmpty()) result.add(normalized);
    }

    private static String string(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
    @SuppressWarnings("unchecked") private static Map<String, Object> map(Object value) { return value instanceof Map<?, ?> ? (Map<String, Object>) value : Map.of(); }

    private record Cached(long stamp, Requirement requirement) {}
    private record Requirement(int level, Set<String> classes, Map<String, Double> attributes, String permission, Set<String> biomes, String gemUpgradeScaling) {
        private static final Requirement EMPTY = new Requirement(0, Set.of(), Map.of(), "", Set.of(), "");
    }
}
