package vn.svframe.mmoitemsfabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.compat.YamlLite;
import vn.svframe.mythiclibfabric.runtime.RpgProfileRegistry;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Native Fabric requirement gate for MMOItems level, class, attributes and permissions. */
public final class MMOItemsRequirementMod implements ModInitializer {
    private static final Logger LOG = Logger.getLogger("MMOItems-Fabric/Requirements");
    private static final Path ROOT = FabricLoader.getInstance().getConfigDir().resolve("MMOItems");
    private static final Map<String, Requirement> REQUIREMENTS = new ConcurrentHashMap<>();
    private static volatile long stamp = Long.MIN_VALUE;
    private static long ticks;

    @Override
    public void onInitialize() {
        reload();
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (++ticks % 100L != 0L) return;
            try {
                long next = configStamp();
                if (next != stamp) reload();
            } catch (IOException exception) {
                LOG.log(Level.WARNING, "Could not inspect MMOItems requirement configs", exception);
            }
        });
    }

    public static boolean meets(ServerPlayerEntity player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()) return false;
        NbtCompound data = MMOItemsGameplayMod.customData(stack);
        String type = data.getString(MMOItemsGameplayMod.NBT_TYPE);
        String id = data.getString(MMOItemsGameplayMod.NBT_ID);
        if (type.isEmpty() || id.isEmpty()) return true;
        Requirement requirement = REQUIREMENTS.get(key(type, id));
        if (requirement == null) return true;

        RpgProfileRegistry.Snapshot profile = RpgProfileRegistry.mergeOrDefault(player.getUuid());
        if (profile.level() < requirement.level()) return false;
        if (!requirement.classes().isEmpty()) {
            String actual = normClass(profile.playerClass());
            if (!requirement.classes().contains(actual)) return false;
        }
        for (Map.Entry<String, Double> entry : requirement.attributes().entrySet()) {
            double actual = profile.attributes().getOrDefault(entry.getKey(), 0.0);
            if (actual + 1.0e-9 < entry.getValue()) return false;
        }
        return requirement.permission().isEmpty() || hasPermission(player, requirement.permission());
    }

    private static synchronized void reload() {
        try {
            Map<String, Requirement> next = new LinkedHashMap<>();
            Path itemRoot = ROOT.resolve("item");
            if (Files.isDirectory(itemRoot)) {
                try (var paths = Files.walk(itemRoot)) {
                    for (Path file : paths.filter(Files::isRegularFile)
                            .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".yml"))
                            .sorted().toList()) {
                        String type = removeExtension(file.getFileName().toString()).toUpperCase(Locale.ROOT);
                        Map<String, Object> root = YamlLite.map(YamlLite.parse(file));
                        for (Map.Entry<String, Object> entry : root.entrySet()) {
                            if (!(entry.getValue() instanceof Map<?, ?> raw)) continue;
                            @SuppressWarnings("unchecked") Map<String, Object> section = (Map<String, Object>) raw;
                            Map<String, Object> base = map(section.get("base"));
                            Requirement requirement = parse(base);
                            if (!requirement.empty()) next.put(key(type, entry.getKey()), requirement);
                        }
                    }
                }
            }
            REQUIREMENTS.clear();
            REQUIREMENTS.putAll(next);
            stamp = configStamp();
            LOG.info("Loaded item requirements=" + REQUIREMENTS.size());
        } catch (Exception exception) {
            LOG.log(Level.SEVERE, "Failed to reload MMOItems requirements; keeping previous snapshot", exception);
        }
    }

    private static Requirement parse(Map<String, Object> base) {
        int level = Math.max(0, (int) Math.ceil(numberBase(base.get("required-level"), 0.0)));
        Set<String> classes = new LinkedHashSet<>();
        for (String value : strings(base.get("required-class"))) classes.add(normClass(value));
        if (classes.isEmpty()) for (String value : strings(base.get("required-classes"))) classes.add(normClass(value));

        Map<String, Double> attributes = new LinkedHashMap<>();
        requirementAttribute(base, attributes, "strength");
        requirementAttribute(base, attributes, "dexterity");
        requirementAttribute(base, attributes, "intelligence");
        requirementAttribute(base, attributes, "power");
        String permission = string(base.get("required-permission"), string(base.get("permission"), ""));
        return new Requirement(level, Set.copyOf(classes), Map.copyOf(attributes), permission.trim());
    }

    private static void requirementAttribute(Map<String, Object> base, Map<String, Double> out, String attribute) {
        double value = numberBase(base.get("required-" + attribute), 0.0);
        if (value > 0.0) out.put(attribute, value);
    }

    private static boolean hasPermission(ServerPlayerEntity player, String permission) {
        if (permission == null || permission.isBlank()) return true;
        if (player.hasPermissionLevel(2)) return true;
        if (!FabricLoader.getInstance().isModLoaded("luckperms")) return false;
        try {
            Class<?> provider = Class.forName("net.luckperms.api.LuckPermsProvider");
            Object api = provider.getMethod("get").invoke(null);
            Object userManager = api.getClass().getMethod("getUserManager").invoke(api);
            Object user = userManager.getClass().getMethod("getUser", java.util.UUID.class).invoke(userManager, player.getUuid());
            if (user == null) return false;
            Object cachedData = user.getClass().getMethod("getCachedData").invoke(user);
            Object permissionData = cachedData.getClass().getMethod("getPermissionData").invoke(cachedData);
            Method checkPermission = permissionData.getClass().getMethod("checkPermission", String.class);
            Object result = checkPermission.invoke(permissionData, permission);
            return (boolean) result.getClass().getMethod("asBoolean").invoke(result);
        } catch (ReflectiveOperationException exception) {
            LOG.log(Level.FINE, "LuckPerms permission lookup failed for " + permission, exception);
            return false;
        }
    }

    private static long configStamp() throws IOException {
        Path itemRoot = ROOT.resolve("item");
        long latest = 0L;
        if (!Files.isDirectory(itemRoot)) return latest;
        try (var paths = Files.walk(itemRoot)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                latest = Math.max(latest, Files.getLastModifiedTime(path).toMillis());
            }
        }
        return latest;
    }

    private static double numberBase(Object value, double fallback) {
        if (value instanceof Number number) return number.doubleValue();
        if (value instanceof String string) {
            try { return Double.parseDouble(string.trim()); } catch (NumberFormatException ignored) { return fallback; }
        }
        Map<String, Object> values = map(value);
        Object base = values.get("base");
        if (base instanceof Number number) return number.doubleValue();
        if (base != null) try { return Double.parseDouble(String.valueOf(base).trim()); } catch (NumberFormatException ignored) { }
        return fallback;
    }

    private static List<String> strings(Object value) {
        if (value == null) return List.of();
        List<String> out = new ArrayList<>();
        if (value instanceof Collection<?> collection) {
            for (Object element : collection) addString(out, element);
        } else addString(out, value);
        return List.copyOf(out);
    }

    private static void addString(List<String> out, Object value) {
        if (value == null) return;
        String raw = String.valueOf(value).trim();
        if (raw.startsWith("[") && raw.endsWith("]")) raw = raw.substring(1, raw.length() - 1);
        for (String part : raw.split(",")) if (!part.isBlank()) out.add(part.trim());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private static String string(Object value, String fallback) { return value == null ? fallback : String.valueOf(value); }
    private static String removeExtension(String name) { int dot = name.lastIndexOf('.'); return dot < 0 ? name : name.substring(0, dot); }
    private static String key(String type, String id) { return (type + ':' + id).trim().toUpperCase(Locale.ROOT); }
    private static String normClass(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('_', '-').replace(' ', '-'); }

    private record Requirement(int level, Set<String> classes, Map<String, Double> attributes, String permission) {
        boolean empty() { return level <= 0 && classes.isEmpty() && attributes.isEmpty() && permission.isEmpty(); }
    }
}
