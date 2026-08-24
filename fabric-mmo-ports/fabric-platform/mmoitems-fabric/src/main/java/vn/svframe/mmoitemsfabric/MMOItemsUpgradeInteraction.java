package vn.svframe.mmoitemsfabric;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.compat.YamlLite;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/** Cursor consumable upgrading with legacy reference, success, max-level and destroy-on-fail semantics. */
public final class MMOItemsUpgradeInteraction {
    public enum Result { NONE, FAILURE, FAILURE_DESTROYED, SUCCESS }

    private static final Path ITEM_ROOT = FabricLoader.getInstance().getConfigDir().resolve("MMOItems").resolve("item");
    private static final Map<String, Cached> CACHE = new ConcurrentHashMap<>();

    private MMOItemsUpgradeInteraction() {}

    public static Result apply(ServerPlayerEntity player, ItemStack consumable, ItemStack target) {
        if (player == null || consumable == null || target == null || consumable.isEmpty() || target.isEmpty()) return Result.NONE;
        if (target.getCount() != 1) return Result.NONE;
        MMOItemsGameplayMod.Template sourceTemplate = MMOItemsGameplayMod.template(consumable);
        MMOItemsGameplayMod.Template targetTemplate = MMOItemsGameplayMod.template(target);
        if (sourceTemplate == null || targetTemplate == null) return Result.NONE;

        UpgradeMeta source = meta(sourceTemplate);
        UpgradeMeta destination = meta(targetTemplate);
        if (!source.present() || !destination.present()) return Result.NONE;
        if (destination.template().isEmpty() || destination.workbench()) return Result.NONE;
        int level = MMOItemsGameplayMod.upgradeLevel(target);
        if (destination.max() > 0 && level >= destination.max()) return Result.NONE;
        if (!referencesMatch(source.reference(), destination.reference())) return Result.NONE;

        MMOItemsGameplayMod.setUpgradeLevel(target, level + 1);
        if (!MMOItemsRequirementGate.canUse(player, target)) {
            MMOItemsGameplayMod.setUpgradeLevel(target, level);
            return Result.NONE;
        }

        double sourceSuccess = source.success() == 0.0 ? 1.0 : source.success() / 100.0;
        double targetSuccess = destination.success() == 0.0 ? 1.0 : destination.success() / 100.0;
        if (ThreadLocalRandom.current().nextDouble() > sourceSuccess * targetSuccess) {
            MMOItemsGameplayMod.setUpgradeLevel(target, level);
            if (destination.destroy()) {
                target.decrement(1);
                return Result.FAILURE_DESTROYED;
            }
            return Result.FAILURE;
        }
        return Result.SUCCESS;
    }

    private static boolean referencesMatch(String consumable, String target) {
        String left = normalizeReference(consumable);
        String right = normalizeReference(target);
        if (left.isEmpty() || right.isEmpty()) return left.equals(right);
        for (String token : left.split("[,;]")) if (token.trim().equalsIgnoreCase(right)) return true;
        for (String token : right.split("[,;]")) if (token.trim().equalsIgnoreCase(left)) return true;
        return left.equalsIgnoreCase(right);
    }

    private static UpgradeMeta meta(MMOItemsGameplayMod.Template template) {
        String key = template.type() + ':' + template.id();
        Path file = ITEM_ROOT.resolve(template.type().toLowerCase(Locale.ROOT) + ".yml");
        long stamp = -1L;
        try { if (Files.isRegularFile(file)) stamp = Files.getLastModifiedTime(file).toMillis(); } catch (Exception ignored) { }
        Cached cached = CACHE.get(key);
        if (cached != null && cached.stamp() == stamp) return cached.meta();
        UpgradeMeta loaded = load(file, template.id());
        CACHE.put(key, new Cached(stamp, loaded));
        return loaded;
    }

    private static UpgradeMeta load(Path file, String id) {
        if (!Files.isRegularFile(file)) return UpgradeMeta.EMPTY;
        try {
            Map<String, Object> root = YamlLite.map(YamlLite.parse(file));
            Map<String, Object> section = map(find(root, id));
            Map<String, Object> base = map(find(section, "base"));
            Object rawUpgrade = find(base, "upgrade");
            if (!(rawUpgrade instanceof Map<?, ?>)) return UpgradeMeta.EMPTY;
            Map<String, Object> upgrade = map(rawUpgrade);
            return new UpgradeMeta(true,
                    string(find(upgrade, "reference")), string(find(upgrade, "template")),
                    bool(find(upgrade, "workbench"), false), bool(find(upgrade, "destroy"), false),
                    integer(find(upgrade, "max"), 0), integer(find(upgrade, "min"), 0),
                    number(find(upgrade, "success"), 100.0));
        } catch (Exception ignored) { return UpgradeMeta.EMPTY; }
    }

    private static Object find(Map<String, Object> map, String key) {
        Object direct = map.get(key);
        if (direct != null) return direct;
        for (Map.Entry<String, Object> entry : map.entrySet()) if (entry.getKey().equalsIgnoreCase(key)) return entry.getValue();
        return null;
    }

    private static String normalizeReference(String value) { return value == null ? "" : value.trim(); }
    private static String string(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
    private static double number(Object value, double fallback) { if (value instanceof Number number) return number.doubleValue(); try { return Double.parseDouble(String.valueOf(value).trim()); } catch (Exception ignored) { return fallback; } }
    private static int integer(Object value, int fallback) { if (value instanceof Number number) return number.intValue(); try { return Integer.parseInt(String.valueOf(value).trim()); } catch (Exception ignored) { return fallback; } }
    private static boolean bool(Object value, boolean fallback) { if (value instanceof Boolean bool) return bool; if (value == null) return fallback; String s = String.valueOf(value).trim(); return s.equalsIgnoreCase("true") ? true : s.equalsIgnoreCase("false") ? false : fallback; }
    @SuppressWarnings("unchecked") private static Map<String, Object> map(Object value) { return value instanceof Map<?, ?> ? (Map<String, Object>) value : Map.of(); }

    private record Cached(long stamp, UpgradeMeta meta) {}
    private record UpgradeMeta(boolean present, String reference, String template, boolean workbench, boolean destroy, int max, int min, double success) {
        private static final UpgradeMeta EMPTY = new UpgradeMeta(false, "", "", false, false, 0, 0, 100.0);
    }
}
