package vn.svframe.mmoitemsfabric;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import vn.svframe.compat.YamlLite;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Preserves per-gem upgrade history so HISTORIC/SUBSEQUENT/NEVER scale exactly across later item upgrades. */
public final class MMOItemsGemScalingRuntime {
    private static final String NBT_HISTORY = "mmoitems_gem_upgrade_history";
    private static final String NBT_COUNT = "mmoitems_gem_upgrade_history_count";
    private static final Path UPGRADE_FILE = FabricLoader.getInstance().getConfigDir().resolve("MMOItems").resolve("upgrade-templates.yml");
    private static final Map<String, UpgradeTemplate> TEMPLATES = new ConcurrentHashMap<>();
    private static volatile long loadedStamp = Long.MIN_VALUE;

    private MMOItemsGemScalingRuntime() {}

    public static void recordAppliedGem(ItemStack target, Map<String, Double> baseStats, String configuredMode) {
        if (target == null || target.isEmpty() || baseStats == null || baseStats.isEmpty()) return;
        String mode = configuredMode == null || configuredMode.isBlank() ? "SUBSEQUENT" : configuredMode.trim().toUpperCase(Locale.ROOT);
        int baseline = switch (mode) {
            case "HISTORIC" -> 0;
            case "SUBSEQUENT" -> MMOItemsGameplayMod.upgradeLevel(target);
            default -> -1;
        };

        NbtCompound data = MMOItemsGameplayMod.customData(target);
        NbtCompound history = data.getCompound(NBT_HISTORY);
        int index = Math.max(0, data.getInt(NBT_COUNT));
        NbtCompound entry = new NbtCompound();
        entry.putInt("baseline", baseline);
        NbtCompound stats = new NbtCompound();
        baseStats.forEach((key, value) -> stats.putDouble(norm(key), value));
        entry.put("stats", stats);
        history.put(Integer.toString(index), entry);
        data.put(NBT_HISTORY, history);
        data.putInt(NBT_COUNT, index + 1);
        MMOItemsGameplayMod.writeCustomData(target, data);
    }

    public static Map<String, Double> deltaStats(ItemStack target) {
        if (target == null || target.isEmpty()) return Map.of();
        MMOItemsGameplayMod.Template item = MMOItemsGameplayMod.template(target);
        if (item == null || item.upgradeTemplate().isBlank()) return Map.of();
        UpgradeTemplate upgrade = upgradeTemplate(item.upgradeTemplate());
        if (upgrade == null || upgrade.modifiers().isEmpty()) return Map.of();

        NbtCompound data = MMOItemsGameplayMod.customData(target);
        NbtCompound history = data.getCompound(NBT_HISTORY);
        int count = Math.max(0, data.getInt(NBT_COUNT));
        int currentLevel = MMOItemsGameplayMod.upgradeLevel(target);
        if (count == 0 || currentLevel == 0) return Map.of();

        Map<String, Double> delta = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            NbtCompound entry = history.getCompound(Integer.toString(index));
            int baseline = entry.getInt("baseline");
            if (baseline < 0 || currentLevel <= baseline) continue;
            int steps = currentLevel - baseline;
            NbtCompound stats = entry.getCompound("stats");
            for (String stat : stats.getKeys()) {
                UpgradeModifier modifier = upgrade.modifiers().get(norm(stat));
                if (modifier == null) continue;
                double base = stats.getDouble(stat);
                double scaled = base;
                for (int level = 0; level < steps; level++) {
                    scaled = modifier.percent() ? scaled * (1.0 + modifier.value() / 100.0) : scaled + modifier.value();
                }
                delta.merge(norm(stat), scaled - base, Double::sum);
            }
        }
        return Map.copyOf(delta);
    }

    private static UpgradeTemplate upgradeTemplate(String id) {
        reloadIfNeeded();
        return TEMPLATES.get(norm(id));
    }

    private static synchronized void reloadIfNeeded() {
        long stamp = -1L;
        try { if (Files.isRegularFile(UPGRADE_FILE)) stamp = Files.getLastModifiedTime(UPGRADE_FILE).toMillis(); } catch (Exception ignored) { }
        if (stamp == loadedStamp) return;
        Map<String, UpgradeTemplate> next = new LinkedHashMap<>();
        try {
            if (Files.isRegularFile(UPGRADE_FILE)) {
                Map<String, Object> root = YamlLite.map(YamlLite.parse(UPGRADE_FILE));
                for (Map.Entry<String, Object> template : root.entrySet()) {
                    Map<String, Object> section = map(template.getValue());
                    Map<String, UpgradeModifier> modifiers = new LinkedHashMap<>();
                    for (Map.Entry<String, Object> stat : section.entrySet()) {
                        String raw = String.valueOf(stat.getValue()).trim();
                        boolean percent = raw.endsWith("%");
                        if (percent) raw = raw.substring(0, raw.length() - 1).trim();
                        try { modifiers.put(norm(stat.getKey()), new UpgradeModifier(Double.parseDouble(raw), percent)); }
                        catch (NumberFormatException ignored) { }
                    }
                    next.put(norm(template.getKey()), new UpgradeTemplate(Map.copyOf(modifiers)));
                }
            }
        } catch (Exception ignored) { }
        TEMPLATES.clear();
        TEMPLATES.putAll(next);
        loadedStamp = stamp;
    }

    @SuppressWarnings("unchecked") private static Map<String, Object> map(Object value) { return value instanceof Map<?, ?> ? (Map<String, Object>) value : Map.of(); }
    private static String norm(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('_', '-').replace(' ', '-'); }
    private record UpgradeTemplate(Map<String, UpgradeModifier> modifiers) {}
    private record UpgradeModifier(double value, boolean percent) {}
}
