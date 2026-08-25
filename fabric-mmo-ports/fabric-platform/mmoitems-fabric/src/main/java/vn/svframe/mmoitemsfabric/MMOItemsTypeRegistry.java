package vn.svframe.mmoitemsfabric;

import net.fabricmc.loader.api.FabricLoader;
import vn.svframe.compat.YamlLite;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Resolves custom MMOItems item types through item-types.yml parent inheritance. */
public final class MMOItemsTypeRegistry {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("MMOItems").resolve("item-types.yml");
    private static final Map<String, String> PARENTS = new ConcurrentHashMap<>();
    private static final Set<String> WEAPONS = Set.of(
            "SWORD", "DAGGER", "SPEAR", "HAMMER", "GAUNTLET", "WHIP", "STAFF", "GREATSTAFF",
            "BOW", "CROSSBOW", "MUSKET", "LUTE", "GREATSWORD", "LONG_SWORD", "KATANA", "HALBERD",
            "AXE", "GREATAXE", "GREATHAMMER");
    private static volatile long stamp = Long.MIN_VALUE;

    private MMOItemsTypeRegistry() {}

    public static boolean isA(String type, String expectedParent) {
        refresh();
        String current = norm(type);
        String expected = norm(expectedParent);
        if (current.isEmpty() || expected.isEmpty()) return false;
        Set<String> seen = ConcurrentHashMap.newKeySet();
        while (!current.isEmpty() && seen.add(current)) {
            if (current.equals(expected)) return true;
            if (expected.equals("WEAPON") && WEAPONS.contains(current)) return true;
            current = PARENTS.getOrDefault(current, "");
        }
        return false;
    }

    public static String root(String type) {
        refresh();
        String current = norm(type);
        if (current.isEmpty()) return "";
        Set<String> seen = ConcurrentHashMap.newKeySet();
        while (seen.add(current)) {
            String parent = PARENTS.get(current);
            if (parent == null || parent.isEmpty()) return current;
            current = parent;
        }
        return current;
    }

    private static void refresh() {
        long next = -1L;
        try { if (Files.isRegularFile(FILE)) next = Files.getLastModifiedTime(FILE).toMillis(); }
        catch (Exception ignored) { }
        if (next == stamp) return;
        synchronized (MMOItemsTypeRegistry.class) {
            if (next == stamp) return;
            Map<String, String> loaded = new LinkedHashMap<>();
            if (Files.isRegularFile(FILE)) {
                try {
                    Map<String, Object> root = YamlLite.map(YamlLite.parse(FILE));
                    for (Map.Entry<String, Object> entry : root.entrySet()) {
                        if (!(entry.getValue() instanceof Map<?, ?> raw)) continue;
                        @SuppressWarnings("unchecked") Map<String, Object> section = (Map<String, Object>) raw;
                        Object parent = findIgnoreCase(section, "parent");
                        if (parent != null && !String.valueOf(parent).isBlank()) loaded.put(norm(entry.getKey()), norm(String.valueOf(parent)));
                    }
                } catch (Exception ignored) { }
            }
            PARENTS.clear();
            PARENTS.putAll(loaded);
            stamp = next;
        }
    }

    private static Object findIgnoreCase(Map<String, Object> map, String key) {
        Object direct = map.get(key);
        if (direct != null) return direct;
        for (Map.Entry<String, Object> entry : map.entrySet()) if (entry.getKey().equalsIgnoreCase(key)) return entry.getValue();
        return null;
    }

    private static String norm(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }
}
