package vn.svframe.mmoitemsfabric;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import vn.svframe.compat.YamlLite;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Rebuilds stale MMOItems when their template revision increases, preserving live item state. */
public final class MMOItemsRevisionRuntime {
    public enum Reason { PICKUP, CRAFT, CLICK, JOIN }

    private static final String NBT_REVISION = "mmoitems_revision";
    private static final Path ROOT = FabricLoader.getInstance().getConfigDir().resolve("MMOItems");
    private static final Map<String, CachedRevision> CACHE = new ConcurrentHashMap<>();
    private static volatile long configStamp = Long.MIN_VALUE;
    private static volatile Map<String, Boolean> disabledReasons = Map.of();

    private MMOItemsRevisionRuntime() {}

    public static ItemStack refresh(ItemStack stack, Reason reason) {
        if (stack == null || stack.isEmpty()) return null;
        MMOItemsGameplayMod.Template template = MMOItemsGameplayMod.template(stack);
        if (template == null || template.id().equalsIgnoreCase("VANILLA")) return null;
        if (disabled(reason)) return null;

        int templateRevision = templateRevision(template);
        NbtCompound oldData = MMOItemsGameplayMod.customData(stack);
        int itemRevision = oldData.contains(NBT_REVISION) ? oldData.getInt(NBT_REVISION) : 1;
        if (templateRevision <= itemRevision) return null;

        ItemStack rebuilt = MMOItemsFabricMod.createStack(template.type(), template.id(), stack.getCount());
        if (rebuilt.isEmpty()) return null;
        MMOItemsGameplayMod.hydrate(rebuilt);

        NbtCompound freshData = MMOItemsGameplayMod.customData(rebuilt);
        // The original reforger preserves generated/runtime state. Keep all custom state from the
        // live item, then force identity and revision back to the freshly loaded template values.
        freshData.copyFrom(oldData);
        freshData.putString(MMOItemsGameplayMod.NBT_TYPE, template.type());
        freshData.putString(MMOItemsGameplayMod.NBT_ID, template.id());
        freshData.putInt(NBT_REVISION, templateRevision);
        freshData.putInt(MMOItemsGameplayMod.NBT_STATE_VERSION, MMOItemsGameplayMod.STATE_VERSION);
        MMOItemsGameplayMod.writeCustomData(rebuilt, freshData);

        // Preserve external/player-side state which revision reforging is expected not to erase.
        copyIfPresent(stack, rebuilt, DataComponentTypes.ENCHANTMENTS);
        copyIfPresent(stack, rebuilt, DataComponentTypes.CUSTOM_NAME);
        copyIfPresent(stack, rebuilt, DataComponentTypes.LORE);
        copyIfPresent(stack, rebuilt, DataComponentTypes.REPAIR_COST);

        // A skinned item keeps its applied skin appearance across revisions.
        if (!oldData.getString("mmoitems_skin_id").isEmpty()) {
            ItemStack skinned = rebuilt.copyComponentsToNewStack(stack.getItem(), rebuilt.getCount());
            copyIfPresent(stack, skinned, DataComponentTypes.CUSTOM_MODEL_DATA);
            copyIfPresent(stack, skinned, DataComponentTypes.UNBREAKABLE);
            copyIfPresent(stack, skinned, DataComponentTypes.DYED_COLOR);
            copyIfPresent(stack, skinned, DataComponentTypes.TRIM);
            copyIfPresent(stack, skinned, DataComponentTypes.PROFILE);
            copyIfPresent(stack, skinned, DataComponentTypes.DAMAGE);
            rebuilt = skinned;
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

    private static boolean disabled(Reason reason) {
        Path file = ROOT.resolve("config.yml");
        long stamp = -1L;
        try { if (Files.isRegularFile(file)) stamp = Files.getLastModifiedTime(file).toMillis(); } catch (Exception ignored) { }
        if (stamp != configStamp) {
            synchronized (MMOItemsRevisionRuntime.class) {
                if (stamp != configStamp) {
                    Map<String, Boolean> next = new java.util.LinkedHashMap<>();
                    try {
                        Map<String, Object> root = YamlLite.map(YamlLite.parse(file));
                        Map<String, Object> revision = map(find(root, "item-revision"));
                        Map<String, Object> disableOn = map(find(revision, "disable-on"));
                        for (Reason value : Reason.values()) next.put(value.name(), bool(find(disableOn, value.name().toLowerCase(Locale.ROOT)), false));
                    } catch (Exception ignored) { }
                    disabledReasons = Map.copyOf(next);
                    configStamp = stamp;
                }
            }
        }
        return disabledReasons.getOrDefault(reason.name(), false);
    }

    private static Object find(Map<String, Object> map, String key) { Object direct = map.get(key); if (direct != null) return direct; for (Map.Entry<String, Object> entry : map.entrySet()) if (entry.getKey().equalsIgnoreCase(key)) return entry.getValue(); return null; }
    private static int integer(Object value, int fallback) { if (value instanceof Number number) return number.intValue(); try { return Integer.parseInt(String.valueOf(value).trim()); } catch (Exception ignored) { return fallback; } }
    private static boolean bool(Object value, boolean fallback) { if (value instanceof Boolean bool) return bool; if (value == null) return fallback; String raw = String.valueOf(value).trim(); return raw.equalsIgnoreCase("true") ? true : raw.equalsIgnoreCase("false") ? false : fallback; }
    @SuppressWarnings("unchecked") private static Map<String, Object> map(Object value) { return value instanceof Map<?, ?> ? (Map<String, Object>) value : Map.of(); }
    private record CachedRevision(long stamp, int revision) {}
}
