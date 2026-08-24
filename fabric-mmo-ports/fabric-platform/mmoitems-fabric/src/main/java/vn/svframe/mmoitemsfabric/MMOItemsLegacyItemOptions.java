package vn.svframe.mmoitemsfabric;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.ItemStack;
import vn.svframe.compat.YamlLite;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Lazily exposes non-numeric legacy item flags that are not part of the compact gameplay Template record. */
public final class MMOItemsLegacyItemOptions {
    private static final Path ITEM_ROOT = FabricLoader.getInstance().getConfigDir().resolve("MMOItems").resolve("item");
    private static final Map<String, Cached> CACHE = new ConcurrentHashMap<>();

    private MMOItemsLegacyItemOptions() {}

    public static boolean bool(ItemStack stack, String option, boolean fallback) {
        MMOItemsGameplayMod.Template template = MMOItemsGameplayMod.template(stack);
        if (template == null) return fallback;
        Object value = value(template, option);
        if (value instanceof Boolean bool) return bool;
        if (value == null) return fallback;
        String raw = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
        if (raw.equals("true") || raw.equals("yes") || raw.equals("on") || raw.equals("1")) return true;
        if (raw.equals("false") || raw.equals("no") || raw.equals("off") || raw.equals("0")) return false;
        return fallback;
    }

    public static String string(ItemStack stack, String option, String fallback) {
        MMOItemsGameplayMod.Template template = MMOItemsGameplayMod.template(stack);
        if (template == null) return fallback;
        Object value = value(template, option);
        return value == null ? fallback : String.valueOf(value).trim();
    }

    public static double number(ItemStack stack, String option, double fallback) {
        MMOItemsGameplayMod.Template template = MMOItemsGameplayMod.template(stack);
        if (template == null) return fallback;
        Object value = value(template, option);
        if (value instanceof Number number) return number.doubleValue();
        Map<String, Object> section = map(value);
        if (!section.isEmpty()) return numberValue(findIgnoreCase(section, "base"), fallback);
        return numberValue(value, fallback);
    }

    private static Object value(MMOItemsGameplayMod.Template template, String option) {
        Map<String, Object> base = base(template);
        return findIgnoreCase(base, option);
    }

    private static Map<String, Object> base(MMOItemsGameplayMod.Template template) {
        String cacheKey = template.type().toUpperCase(Locale.ROOT) + ':' + template.id().toUpperCase(Locale.ROOT);
        Path file = ITEM_ROOT.resolve(template.type().toLowerCase(Locale.ROOT) + ".yml");
        long stamp = -1L;
        try { if (Files.isRegularFile(file)) stamp = Files.getLastModifiedTime(file).toMillis(); } catch (Exception ignored) { }
        Cached cached = CACHE.get(cacheKey);
        if (cached != null && cached.stamp() == stamp) return cached.base();
        Map<String, Object> loaded = Map.of();
        try {
            if (Files.isRegularFile(file)) {
                Map<String, Object> root = YamlLite.map(YamlLite.parse(file));
                Map<String, Object> item = map(findIgnoreCase(root, template.id()));
                loaded = Map.copyOf(map(findIgnoreCase(item, "base")));
            }
        } catch (Exception ignored) { }
        CACHE.put(cacheKey, new Cached(stamp, loaded));
        return loaded;
    }

    private static Object findIgnoreCase(Map<String, Object> map, String key) {
        if (map == null || key == null) return null;
        Object direct = map.get(key);
        if (direct != null) return direct;
        for (Map.Entry<String, Object> entry : map.entrySet()) if (entry.getKey().equalsIgnoreCase(key)) return entry.getValue();
        return null;
    }

    private static double numberValue(Object value, double fallback) {
        if (value instanceof Number number) return number.doubleValue();
        try { return Double.parseDouble(String.valueOf(value).trim()); } catch (Exception ignored) { return fallback; }
    }

    @SuppressWarnings("unchecked") private static Map<String, Object> map(Object value) { return value instanceof Map<?, ?> ? (Map<String, Object>) value : Map.of(); }
    private record Cached(long stamp, Map<String, Object> base) {}
}
