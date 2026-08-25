package vn.svframe.mmoitemsfabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.compat.YamlLite;
import vn.svframe.mmoitemsfabric.runtime.gameplay.EquipmentStats;
import vn.svframe.mmoitemsfabric.runtime.gameplay.ItemStatProfile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Shared MMOItems gameplay state for Fabric-facing durability, gems, upgrades,
 * reforging and stat lookup. ItemStack custom data is the authoritative state.
 */
public final class MMOItemsGameplayMod implements ModInitializer {
    private static final Logger LOG = Logger.getLogger("MMOItems-Fabric/Gameplay");
    private static final Path ROOT = FabricLoader.getInstance().getConfigDir().resolve("MMOItems");

    public static final String NBT_TYPE = "mmoitems_type";
    public static final String NBT_ID = "mmoitems_id";
    public static final String NBT_UPGRADE = "mmoitems_upgrade";
    public static final String NBT_STATE_VERSION = "mmoitems_state_version";
    public static final String NBT_DURABILITY_MAX = "mmoitems_durability_max";
    public static final String NBT_DURABILITY_CURRENT = "mmoitems_durability_current";
    public static final String NBT_DURABILITY_LOST = "mmoitems_durability_lost";
    public static final String NBT_EMPTY_SOCKETS = "mmoitems_empty_sockets";
    public static final String NBT_GEM_STATS = "mmoitems_gem_stats";
    public static final int STATE_VERSION = 1;

    private static final Map<String, Template> TEMPLATES = new ConcurrentHashMap<>();
    private static final Map<String, UpgradeTemplate> UPGRADES = new ConcurrentHashMap<>();
    private static volatile long ticks;
    private static volatile long lastStamp = Long.MIN_VALUE;

    @Override
    public void onInitialize() {
        reload();
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            ticks++;
            if (ticks % 100L != 0L) return;
            try {
                long stamp = configStamp();
                if (stamp != lastStamp) reload();
            } catch (IOException exception) {
                LOG.log(Level.WARNING, "Could not inspect MMOItems gameplay config timestamps", exception);
            }
        });
    }

    public static ItemStack hydrate(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return stack;
        NbtCompound data = customData(stack);
        Template template = template(data);
        if (template == null) return stack;

        boolean changed = false;
        if (data.getInt(NBT_STATE_VERSION) < STATE_VERSION) {
            data.putInt(NBT_STATE_VERSION, STATE_VERSION);
            changed = true;
        }
        if (!data.contains(NBT_UPGRADE)) {
            data.putInt(NBT_UPGRADE, Math.max(0, template.upgradeMin()));
            changed = true;
        }
        if (template.maxDurability() > 0) {
            if (!data.contains(NBT_DURABILITY_MAX)) {
                data.putInt(NBT_DURABILITY_MAX, template.maxDurability());
                changed = true;
            }
            if (!data.contains(NBT_DURABILITY_CURRENT)) {
                data.putInt(NBT_DURABILITY_CURRENT, template.maxDurability());
                changed = true;
            }
            if (!data.contains(NBT_DURABILITY_LOST)) {
                data.putBoolean(NBT_DURABILITY_LOST, template.lostWhenBroken());
                changed = true;
            }
        }
        if (!template.sockets().isEmpty() && !data.contains(NBT_EMPTY_SOCKETS)) {
            data.putString(NBT_EMPTY_SOCKETS, encodeSockets(template.sockets()));
            changed = true;
        }
        if (!data.contains(NBT_GEM_STATS)) {
            data.put(NBT_GEM_STATS, new NbtCompound());
            changed = true;
        }
        if (changed) writeCustomData(stack, data);
        return stack;
    }

    public static EquipmentStats equipmentStats(ServerPlayerEntity player) {
        EquipmentStats out = new EquipmentStats();
        addEffective(out, player.getMainHandStack());
        addEffective(out, player.getOffHandStack());
        for (ItemStack stack : player.getArmorItems()) addEffective(out, stack);
        return out;
    }

    public static ItemStatProfile effectiveStats(ItemStack stack) {
        ItemStatProfile profile = new ItemStatProfile();
        if (stack == null || stack.isEmpty()) return profile;
        hydrate(stack);
        NbtCompound data = customData(stack);
        Template template = template(data);
        if (template == null) return profile;

        Map<String, Double> effective = new LinkedHashMap<>(template.numericStats());
        int upgradeLevel = Math.max(0, data.getInt(NBT_UPGRADE));
        UpgradeTemplate upgrade = UPGRADES.get(norm(template.upgradeTemplate()));
        if (upgrade != null && upgradeLevel > 0) {
            for (Map.Entry<String, UpgradeModifier> entry : upgrade.modifiers().entrySet()) {
                String stat = entry.getKey();
                UpgradeModifier modifier = entry.getValue();
                double value = effective.getOrDefault(stat, 0.0);
                for (int level = 0; level < upgradeLevel; level++) {
                    value = modifier.percent() ? value * (1.0 + modifier.value() / 100.0) : value + modifier.value();
                }
                effective.put(stat, value);
            }
        }

        NbtCompound gems = data.getCompound(NBT_GEM_STATS);
        for (String stat : gems.getKeys()) effective.merge(norm(stat), gems.getDouble(stat), Double::sum);
        effective.forEach(profile::put);
        return profile;
    }

    public static Template template(ItemStack stack) {
        return stack == null || stack.isEmpty() ? null : template(customData(stack));
    }

    public static Template template(NbtCompound data) {
        String type = data.getString(NBT_TYPE);
        String id = data.getString(NBT_ID);
        if (type.isEmpty() || id.isEmpty()) return null;
        return TEMPLATES.get(key(type, id));
    }

    public static NbtCompound customData(ItemStack stack) {
        NbtComponent component = stack.get(DataComponentTypes.CUSTOM_DATA);
        return component == null ? new NbtCompound() : component.copyNbt();
    }

    public static void writeCustomData(ItemStack stack, NbtCompound data) {
        NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack, target -> {
            for (String key : new ArrayList<>(target.getKeys())) target.remove(key);
            target.copyFrom(data);
        });
    }

    public static List<String> emptySockets(ItemStack stack) {
        hydrate(stack);
        return decodeSockets(customData(stack).getString(NBT_EMPTY_SOCKETS));
    }

    public static void setEmptySockets(ItemStack stack, List<String> sockets) {
        NbtCompound data = customData(stack);
        data.putString(NBT_EMPTY_SOCKETS, encodeSockets(sockets));
        writeCustomData(stack, data);
    }

    public static void addGemStats(ItemStack stack, Map<String, Double> stats, double scale) {
        NbtCompound data = customData(stack);
        NbtCompound gems = data.getCompound(NBT_GEM_STATS);
        for (Map.Entry<String, Double> entry : stats.entrySet()) {
            String stat = norm(entry.getKey());
            gems.putDouble(stat, gems.getDouble(stat) + entry.getValue() * scale);
        }
        data.put(NBT_GEM_STATS, gems);
        writeCustomData(stack, data);
    }

    public static int upgradeLevel(ItemStack stack) {
        hydrate(stack);
        return Math.max(0, customData(stack).getInt(NBT_UPGRADE));
    }

    public static void setUpgradeLevel(ItemStack stack, int level) {
        NbtCompound data = customData(stack);
        data.putInt(NBT_UPGRADE, Math.max(0, level));
        writeCustomData(stack, data);
    }

    public static int durability(ItemStack stack) {
        hydrate(stack);
        return Math.max(0, customData(stack).getInt(NBT_DURABILITY_CURRENT));
    }

    public static int maxDurability(ItemStack stack) {
        hydrate(stack);
        return Math.max(0, customData(stack).getInt(NBT_DURABILITY_MAX));
    }

    public static boolean lostWhenBroken(ItemStack stack) {
        hydrate(stack);
        return customData(stack).getBoolean(NBT_DURABILITY_LOST);
    }

    public static void setDurability(ItemStack stack, int value) {
        NbtCompound data = customData(stack);
        int max = Math.max(0, data.getInt(NBT_DURABILITY_MAX));
        data.putInt(NBT_DURABILITY_CURRENT, Math.max(0, Math.min(max, value)));
        writeCustomData(stack, data);
    }

    private static void addEffective(EquipmentStats out, ItemStack stack) {
        Template template = template(stack);
        if (template != null) out.add(effectiveStats(stack));
    }

    private static void reload() {
        try {
            Map<String, Template> nextTemplates = new LinkedHashMap<>();
            Path items = ROOT.resolve("item");
            if (Files.isDirectory(items)) {
                try (var files = Files.walk(items)) {
                    for (Path file : files.filter(Files::isRegularFile)
                            .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".yml"))
                            .sorted(Comparator.comparing(Path::toString)).toList()) {
                        String type = removeExtension(file.getFileName().toString()).toUpperCase(Locale.ROOT);
                        Map<String, Object> root = YamlLite.map(YamlLite.parse(file));
                        for (Map.Entry<String, Object> entry : root.entrySet()) {
                            if (!(entry.getValue() instanceof Map<?, ?> raw)) continue;
                            @SuppressWarnings("unchecked") Map<String, Object> section = (Map<String, Object>) raw;
                            Map<String, Object> base = map(section.get("base"));
                            nextTemplates.put(key(type, entry.getKey()), parseTemplate(type, entry.getKey(), base));
                        }
                    }
                }
            }
            Map<String, UpgradeTemplate> nextUpgrades = loadUpgradeTemplates(ROOT.resolve("upgrade-templates.yml"));
            TEMPLATES.clear(); TEMPLATES.putAll(nextTemplates);
            UPGRADES.clear(); UPGRADES.putAll(nextUpgrades);
            lastStamp = configStamp();
            LOG.info("Loaded gameplay state templates=" + TEMPLATES.size() + ", upgradeTemplates=" + UPGRADES.size());
        } catch (Exception exception) {
            LOG.log(Level.SEVERE, "Failed to reload MMOItems gameplay state; keeping previous snapshot", exception);
        }
    }

    private static Template parseTemplate(String type, String id, Map<String, Object> base) {
        Map<String, Double> numeric = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : base.entrySet()) {
            Double value = numericBase(entry.getValue());
            if (value != null) numeric.put(norm(entry.getKey()), value);
        }
        List<String> sockets = strings(base.get("gem-sockets"));
        String gemColor = string(base.get("gem-color"), "Uncolored");
        Set<String> restriction = new LinkedHashSet<>(strings(base.get("item-type-restriction")));
        double success = numberBase(base.get("success-rate"), 100.0);
        int maxDurability = Math.max(0, (int) Math.round(numberBase(base.get("max-durability"), 0.0)));
        boolean lost = bool(base.get("will-break"), false);

        Map<String, Object> upgrade = map(base.get("upgrade"));
        String upgradeTemplate = string(upgrade.get("template"), string(upgrade.get("reference"), ""));
        int upgradeMax = integer(upgrade.get("max"), 0);
        int upgradeMin = integer(upgrade.get("min"), 0);
        double upgradeSuccess = number(upgrade.get("success"), 100.0);
        boolean upgradeDestroy = bool(upgrade.get("destroy"), false);
        boolean workbench = bool(upgrade.get("workbench"), true);

        return new Template(type.toUpperCase(Locale.ROOT), id.toUpperCase(Locale.ROOT), Map.copyOf(numeric),
                List.copyOf(sockets), gemColor, Set.copyOf(restriction), success, maxDurability, lost,
                upgradeTemplate, upgradeMin, upgradeMax, upgradeSuccess, upgradeDestroy, workbench);
    }

    private static Map<String, UpgradeTemplate> loadUpgradeTemplates(Path file) throws IOException {
        if (!Files.isRegularFile(file)) return Map.of();
        Map<String, Object> root = YamlLite.map(YamlLite.parse(file));
        Map<String, UpgradeTemplate> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : root.entrySet()) {
            Map<String, Object> section = map(entry.getValue());
            Map<String, UpgradeModifier> modifiers = new LinkedHashMap<>();
            for (Map.Entry<String, Object> stat : section.entrySet()) {
                String raw = String.valueOf(stat.getValue()).trim();
                boolean percent = raw.endsWith("%");
                if (percent) raw = raw.substring(0, raw.length() - 1).trim();
                try { modifiers.put(norm(stat.getKey()), new UpgradeModifier(Double.parseDouble(raw), percent)); }
                catch (NumberFormatException ignored) { }
            }
            out.put(norm(entry.getKey()), new UpgradeTemplate(Map.copyOf(modifiers)));
        }
        return Map.copyOf(out);
    }

    private static long configStamp() throws IOException {
        long stamp = 0L;
        Path items = ROOT.resolve("item");
        if (Files.isDirectory(items)) {
            try (var files = Files.walk(items)) {
                for (Path file : files.filter(Files::isRegularFile).toList()) stamp = Math.max(stamp, Files.getLastModifiedTime(file).toMillis());
            }
        }
        Path upgrades = ROOT.resolve("upgrade-templates.yml");
        if (Files.isRegularFile(upgrades)) stamp = Math.max(stamp, Files.getLastModifiedTime(upgrades).toMillis());
        return stamp;
    }

    private static String encodeSockets(List<String> sockets) {
        return String.join("\u001f", sockets.stream().map(String::trim).toList());
    }

    private static List<String> decodeSockets(String encoded) {
        if (encoded == null || encoded.isEmpty()) return List.of();
        List<String> out = new ArrayList<>();
        for (String part : encoded.split("\u001f", -1)) if (!part.isBlank()) out.add(part.trim());
        return List.copyOf(out);
    }

    private static Double numericBase(Object value) {
        if (value instanceof Number number) return number.doubleValue();
        if (value instanceof String string) {
            try { return Double.parseDouble(string.trim()); } catch (NumberFormatException ignored) { return null; }
        }
        Map<String, Object> map = map(value);
        if (!map.isEmpty()) {
            Object base = map.get("base");
            if (base instanceof Number number) return number.doubleValue();
            if (base != null) try { return Double.parseDouble(String.valueOf(base).trim()); } catch (NumberFormatException ignored) { }
        }
        return null;
    }

    private static double numberBase(Object value, double fallback) {
        Double parsed = numericBase(value);
        return parsed == null ? fallback : parsed;
    }

    private static double number(Object value, double fallback) {
        if (value instanceof Number number) return number.doubleValue();
        try { return Double.parseDouble(String.valueOf(value).trim()); } catch (Exception ignored) { return fallback; }
    }

    private static int integer(Object value, int fallback) {
        if (value instanceof Number number) return number.intValue();
        try { return Integer.parseInt(String.valueOf(value).trim()); } catch (Exception ignored) { return fallback; }
    }

    private static boolean bool(Object value, boolean fallback) {
        if (value instanceof Boolean bool) return bool;
        if (value == null) return fallback;
        String string = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
        if (string.equals("true") || string.equals("yes") || string.equals("on") || string.equals("1")) return true;
        if (string.equals("false") || string.equals("no") || string.equals("off") || string.equals("0")) return false;
        return fallback;
    }

    private static List<String> strings(Object value) {
        if (value == null) return List.of();
        if (value instanceof Collection<?> collection) return collection.stream().map(String::valueOf).map(String::trim).filter(s -> !s.isEmpty()).toList();
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) return List.of();
        List<String> out = new ArrayList<>();
        for (String part : text.split("[,;]")) if (!part.isBlank()) out.add(part.trim());
        return List.copyOf(out);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> ? (Map<String, Object>) value : Map.of();
    }

    private static String string(Object value, String fallback) { return value == null ? fallback : String.valueOf(value).trim(); }
    private static String removeExtension(String name) { int dot = name.lastIndexOf('.'); return dot < 0 ? name : name.substring(0, dot); }
    private static String key(String type, String id) { return (type + ':' + id).trim().toUpperCase(Locale.ROOT); }
    private static String norm(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('_', '-').replace(' ', '-'); }

    public record Template(String type, String id, Map<String, Double> numericStats, List<String> sockets,
                           String gemColor, Set<String> itemTypeRestriction, double successRate,
                           int maxDurability, boolean lostWhenBroken, String upgradeTemplate,
                           int upgradeMin, int upgradeMax, double upgradeSuccess, boolean upgradeDestroy,
                           boolean workbenchUpgrade) {
        public boolean canReceiveType(String sourceType) {
            if (itemTypeRestriction.isEmpty()) return true;
            for (String allowed : itemTypeRestriction) if (allowed.equalsIgnoreCase(sourceType)) return true;
            return false;
        }
    }

    private record UpgradeTemplate(Map<String, UpgradeModifier> modifiers) {}
    private record UpgradeModifier(double value, boolean percent) {}
}
