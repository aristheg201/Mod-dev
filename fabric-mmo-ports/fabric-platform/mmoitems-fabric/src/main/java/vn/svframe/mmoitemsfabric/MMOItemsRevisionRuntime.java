package vn.svframe.mmoitemsfabric;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import vn.svframe.compat.YamlLite;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Rebuilds stale MMOItems when their template revision increases while obeying the original revision keep-data policy. */
public final class MMOItemsRevisionRuntime {
    public enum Reason { PICKUP, CRAFT, CLICK, JOIN }

    private static final String NBT_REVISION = "mmoitems_revision";
    private static final Path ROOT = FabricLoader.getInstance().getConfigDir().resolve("MMOItems");
    private static final Map<String, CachedRevision> CACHE = new ConcurrentHashMap<>();
    private static volatile long configStamp = Long.MIN_VALUE;
    private static volatile RevisionPolicy policy = RevisionPolicy.DEFAULT;

    private MMOItemsRevisionRuntime() {}

    public static ItemStack refresh(ItemStack stack, Reason reason) {
        if (stack == null || stack.isEmpty()) return null;
        MMOItemsGameplayMod.Template template = MMOItemsGameplayMod.template(stack);
        if (template == null || disabled(reason)) return null;

        int templateRevision = templateRevision(template);
        NbtCompound oldData = MMOItemsGameplayMod.customData(stack);
        int itemRevision = oldData.contains(NBT_REVISION) ? oldData.getInt(NBT_REVISION) : 1;
        if (templateRevision <= itemRevision) return null;

        ItemStack rebuilt = MMOItemsFabricMod.createStack(template.type(), template.id(), stack.getCount());
        if (rebuilt.isEmpty()) return null;
        MMOItemsGameplayMod.hydrate(rebuilt);

        RevisionPolicy active = policy();
        NbtCompound freshData = MMOItemsGameplayMod.customData(rebuilt);
        if (active.externalData) copyExternalState(oldData, freshData);

        // Custom durability is live gameplay state. The original reforge lifecycle keeps it independently
        // from generated template data, so revision refreshes preserve it as well.
        copyIfPresent(oldData, freshData, MMOItemsGameplayMod.NBT_DURABILITY_MAX);
        copyIfPresent(oldData, freshData, MMOItemsGameplayMod.NBT_DURABILITY_CURRENT);
        copyIfPresent(oldData, freshData, MMOItemsGameplayMod.NBT_DURABILITY_LOST);

        if (active.gems) {
            copyIfPresent(oldData, freshData, MMOItemsGameplayMod.NBT_EMPTY_SOCKETS);
            copyIfPresent(oldData, freshData, MMOItemsGameplayMod.NBT_GEM_STATS);
            copyIfPresent(oldData, freshData, "mmoitems_gem_socket_history");
            copyIfPresent(oldData, freshData, "mmoitems_gem_upgrade_history");
            copyIfPresent(oldData, freshData, "mmoitems_gem_upgrade_history_count");
        }
        if (active.upgrades) copyIfPresent(oldData, freshData, MMOItemsGameplayMod.NBT_UPGRADE);
        if (active.soulbound) copyMatching(oldData, freshData, Set.of("soulbound", "soulbinding"));
        if (active.modifications) copyMatching(oldData, freshData, Set.of("modifier", "modification"));
        if (active.tier) copyMatching(oldData, freshData, Set.of("tier"));
        if (active.skins) copyMatching(oldData, freshData, Set.of("skin"));

        freshData.putString(MMOItemsGameplayMod.NBT_TYPE, template.type());
        freshData.putString(MMOItemsGameplayMod.NBT_ID, template.id());
        freshData.putInt(NBT_REVISION, templateRevision);
        freshData.putInt(MMOItemsGameplayMod.NBT_STATE_VERSION, MMOItemsGameplayMod.STATE_VERSION);
        MMOItemsGameplayMod.writeCustomData(rebuilt, freshData);

        if (active.enchantments) copyIfPresent(stack, rebuilt, DataComponentTypes.ENCHANTMENTS);
        if (active.displayName) copyIfPresent(stack, rebuilt, DataComponentTypes.CUSTOM_NAME);
        if (active.lore) copyIfPresent(stack, rebuilt, DataComponentTypes.LORE);
        if (active.externalData) copyIfPresent(stack, rebuilt, DataComponentTypes.REPAIR_COST);

        // Preserve applied skin appearance only when the live item actually carries skin state. Otherwise
        // the fresh template's current model data must win, or revision updates would freeze old CMD values.
        if (active.skins && hasMatchingKey(oldData, "skin")) {
            copyIfPresent(stack, rebuilt, DataComponentTypes.CUSTOM_MODEL_DATA);
            copyIfPresent(stack, rebuilt, DataComponentTypes.UNBREAKABLE);
            copyIfPresent(stack, rebuilt, DataComponentTypes.DYED_COLOR);
            copyIfPresent(stack, rebuilt, DataComponentTypes.TRIM);
            copyIfPresent(stack, rebuilt, DataComponentTypes.PROFILE);
        }
        return rebuilt;
    }

    public static void stampFresh(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        MMOItemsGameplayMod.Template template = MMOItemsGameplayMod.template(stack);
        if (template == null) return;
        NbtCompound data = MMOItemsGameplayMod.customData(stack);
        data.putInt(NBT_REVISION, templateRevision(template));
        MMOItemsGameplayMod.writeCustomData(stack, data);
    }

    private static void copyExternalState(NbtCompound source, NbtCompound target) {
        for (String key : source.getKeys()) {
            String lower = key.toLowerCase(Locale.ROOT);
            if (key.equals(MMOItemsGameplayMod.NBT_TYPE) || key.equals(MMOItemsGameplayMod.NBT_ID)
                    || key.equals(MMOItemsGameplayMod.NBT_STATE_VERSION) || key.equals(NBT_REVISION)) continue;
            if (lower.contains("gem") || lower.contains("socket") || lower.contains("durability")
                    || lower.contains("upgrade") || lower.contains("soulbound") || lower.contains("soulbinding")
                    || lower.contains("modifier") || lower.contains("modification") || lower.contains("tier")
                    || lower.contains("skin")) continue;
            copyKey(source, target, key);
        }
    }

    private static void copyIfPresent(NbtCompound source, NbtCompound target, String key) {
        if (source.contains(key)) copyKey(source, target, key);
    }

    private static void copyMatching(NbtCompound source, NbtCompound target, Set<String> fragments) {
        for (String key : source.getKeys()) {
            String lower = key.toLowerCase(Locale.ROOT);
            for (String fragment : fragments) {
                if (lower.contains(fragment)) { copyKey(source, target, key); break; }
            }
        }
    }

    private static boolean hasMatchingKey(NbtCompound source, String fragment) {
        String wanted = fragment.toLowerCase(Locale.ROOT);
        for (String key : source.getKeys()) if (key.toLowerCase(Locale.ROOT).contains(wanted)) return true;
        return false;
    }

    private static void copyKey(NbtCompound source, NbtCompound target, String key) {
        var element = source.get(key);
        if (element != null) target.put(key, element.copy());
    }

    private static <T> void copyIfPresent(ItemStack source, ItemStack target, net.minecraft.component.ComponentType<T> type) {
        T value = source.get(type);
        if (value != null) target.set(type, value);
    }

    private static int templateRevision(MMOItemsGameplayMod.Template template) {
        String key = template.type() + ':' + template.id();
        Path file = ROOT.resolve("item").resolve(template.type().toLowerCase(Locale.ROOT) + ".yml");
        long stamp = -1L;
        try { if (Files.isRegularFile(file)) stamp = Files.getLastModifiedTime(file).toMillis(); } catch (Exception ignored) { }
        CachedRevision cached = CACHE.get(key);
        if (cached != null && cached.stamp() == stamp) return cached.revision();
        int revision = 1;
        try {
            Map<String, Object> root = YamlLite.map(YamlLite.parse(file));
            Map<String, Object> section = map(find(root, template.id()));
            Map<String, Object> base = map(find(section, "base"));
            revision = Math.max(1, integer(find(base, "revision-id"), 1));
        } catch (Exception ignored) { }
        CACHE.put(key, new CachedRevision(stamp, revision));
        return revision;
    }

    private static boolean disabled(Reason reason) { return policy().disabledReasons.getOrDefault(reason, false); }

    private static RevisionPolicy policy() {
        Path file = ROOT.resolve("config.yml");
        long stamp = -1L;
        try { if (Files.isRegularFile(file)) stamp = Files.getLastModifiedTime(file).toMillis(); } catch (Exception ignored) { }
        if (stamp == configStamp) return policy;
        synchronized (MMOItemsRevisionRuntime.class) {
            if (stamp == configStamp) return policy;
            RevisionPolicy next = RevisionPolicy.DEFAULT;
            try {
                if (Files.isRegularFile(file)) {
                    Map<String, Object> root = YamlLite.map(YamlLite.parse(file));
                    Map<String, Object> revision = map(find(root, "item-revision"));
                    Map<String, Object> keep = map(find(revision, "keep-data"));
                    Map<String, Object> disableOn = map(find(revision, "disable-on"));
                    Map<Reason, Boolean> disabled = new LinkedHashMap<>();
                    for (Reason value : Reason.values()) disabled.put(value, bool(find(disableOn, value.name().toLowerCase(Locale.ROOT)), defaultDisabled(value)));
                    next = new RevisionPolicy(
                            bool(find(keep, "display-name"), true), bool(find(keep, "enchantments"), true),
                            bool(find(keep, "soulbound"), true), bool(find(keep, "gems"), true),
                            bool(find(keep, "upgrades"), true), bool(find(keep, "lore"), false),
                            bool(find(keep, "exsh"), true), bool(find(keep, "reroll"), false),
                            bool(find(keep, "modifications"), true), bool(find(keep, "skins"), true),
                            bool(find(keep, "tier"), true), Map.copyOf(disabled));
                }
            } catch (Exception ignored) { }
            policy = next;
            configStamp = stamp;
            return next;
        }
    }

    private static boolean defaultDisabled(Reason reason) { return reason == Reason.CRAFT; }
    private static Object find(Map<String, Object> map, String key) { Object direct = map.get(key); if (direct != null) return direct; for (Map.Entry<String, Object> entry : map.entrySet()) if (entry.getKey().equalsIgnoreCase(key)) return entry.getValue(); return null; }
    private static int integer(Object value, int fallback) { if (value instanceof Number number) return number.intValue(); try { return Integer.parseInt(String.valueOf(value).trim()); } catch (Exception ignored) { return fallback; } }
    private static boolean bool(Object value, boolean fallback) { if (value instanceof Boolean bool) return bool; if (value == null) return fallback; String raw = String.valueOf(value).trim(); return raw.equalsIgnoreCase("true") ? true : raw.equalsIgnoreCase("false") ? false : fallback; }
    @SuppressWarnings("unchecked") private static Map<String, Object> map(Object value) { return value instanceof Map<?, ?> ? (Map<String, Object>) value : Map.of(); }

    private record CachedRevision(long stamp, int revision) {}
    private record RevisionPolicy(boolean displayName, boolean enchantments, boolean soulbound, boolean gems,
                                  boolean upgrades, boolean lore, boolean externalData, boolean reroll,
                                  boolean modifications, boolean skins, boolean tier,
                                  Map<Reason, Boolean> disabledReasons) {
        private static final RevisionPolicy DEFAULT = new RevisionPolicy(true, true, true, true, true, false,
                true, false, true, true, true,
                Map.of(Reason.PICKUP, false, Reason.CRAFT, true, Reason.CLICK, false, Reason.JOIN, false));
    }
}
