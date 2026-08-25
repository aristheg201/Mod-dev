package vn.svframe.mmoitemsfabric;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.component.ComponentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.nbt.NbtCompound;
import vn.svframe.compat.YamlLite;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/** Legacy MMOItems item-skin cursor interaction for the 1.21.1 component model. */
public final class MMOItemsItemSkinInteraction {
    public enum Result { NONE, FAILURE, SUCCESS }
    public record ApplyResult(Result result, ItemStack stack) {}

    private static final String NBT_SKIN_ID = "mmoitems_skin_id";
    private static final String NBT_SKIN_TYPE = "mmoitems_skin_type";
    private static final Path ROOT = FabricLoader.getInstance().getConfigDir().resolve("MMOItems");
    private static final Map<String, CachedCompatibility> CACHE = new ConcurrentHashMap<>();
    private static volatile long globalStamp = Long.MIN_VALUE;
    private static volatile boolean lockedSkins;

    private MMOItemsItemSkinInteraction() {}

    public static ApplyResult apply(ServerPlayerEntity player, ItemStack skinStack, ItemStack targetStack) {
        if (player == null || skinStack == null || targetStack == null || skinStack.isEmpty() || targetStack.isEmpty()) return none();
        MMOItemsGameplayMod.Template skin = MMOItemsGameplayMod.template(skinStack);
        MMOItemsGameplayMod.Template target = MMOItemsGameplayMod.template(targetStack);
        if (skin == null || target == null || !MMOItemsTypeRegistry.isA(skin.type(), "SKIN")) return none();
        if (MMOItemsTypeRegistry.isA(target.type(), "SKIN") || targetStack.getCount() > 1) return none();
        if (!MMOItemsRequirementGate.canUse(player, skinStack)) return none();

        refreshGlobal();
        NbtCompound targetData = MMOItemsGameplayMod.customData(targetStack);
        if (lockedSkins && !targetData.getString(NBT_SKIN_ID).isEmpty()) return none();

        Compatibility compatibility = compatibility(skin);
        if (!compatibility.types().isEmpty() && compatibility.types().stream().noneMatch(value -> MMOItemsTypeRegistry.isA(target.type(), value))) return none();
        if (!compatibility.ids().isEmpty() && compatibility.ids().stream().noneMatch(value -> value.equalsIgnoreCase(target.id()))) return none();
        String materialId = Registries.ITEM.getId(targetStack.getItem()).getPath();
        if (!compatibility.materials().isEmpty() && compatibility.materials().stream().noneMatch(value -> material(value).equalsIgnoreCase(materialId))) return none();

        double success = skin.successRate();
        if (success != 0.0 && ThreadLocalRandom.current().nextDouble() >= Math.max(0.0, Math.min(1.0, success / 100.0)))
            return new ApplyResult(Result.FAILURE, targetStack);

        ItemStack result = targetStack.copyComponentsToNewStack(skinStack.getItem(), 1);
        copyIfPresent(skinStack, result, DataComponentTypes.CUSTOM_MODEL_DATA);
        copyIfPresent(skinStack, result, DataComponentTypes.UNBREAKABLE);
        copyIfPresent(skinStack, result, DataComponentTypes.DYED_COLOR);
        copyIfPresent(skinStack, result, DataComponentTypes.TRIM);
        copyIfPresent(skinStack, result, DataComponentTypes.PROFILE);
        if (skinStack.contains(DataComponentTypes.DAMAGE)) copyIfPresent(skinStack, result, DataComponentTypes.DAMAGE);

        NbtCompound data = MMOItemsGameplayMod.customData(result);
        data.putString(NBT_SKIN_ID, skin.id());
        data.putString(NBT_SKIN_TYPE, skin.type());
        NbtComponent skinCustom = skinStack.get(DataComponentTypes.CUSTOM_DATA);
        if (skinCustom != null) {
            NbtCompound skinNbt = skinCustom.copyNbt();
            String particles = skinNbt.getString("mmoitems_item_particles");
            if (!particles.isEmpty()) data.putString("mmoitems_item_particles", particles);
        }
        MMOItemsGameplayMod.writeCustomData(result, data);
        return new ApplyResult(Result.SUCCESS, result);
    }

    private static <T> void copyIfPresent(ItemStack source, ItemStack target, ComponentType<T> type) {
        T value = source.get(type);
        if (value != null) target.set(type, value);
    }

    private static Compatibility compatibility(MMOItemsGameplayMod.Template skin) {
        String cacheKey = skin.type() + ':' + skin.id();
        Path file = ROOT.resolve("item").resolve(skin.type().toLowerCase(Locale.ROOT) + ".yml");
        long stamp = -1L;
        try { if (Files.isRegularFile(file)) stamp = Files.getLastModifiedTime(file).toMillis(); } catch (Exception ignored) { }
        CachedCompatibility cached = CACHE.get(cacheKey);
        if (cached != null && cached.stamp() == stamp) return cached.compatibility();
        Compatibility loaded = Compatibility.EMPTY;
        try {
            Map<String, Object> root = YamlLite.map(YamlLite.parse(file));
            Map<String, Object> section = map(find(root, skin.id()));
            Map<String, Object> base = map(find(section, "base"));
            loaded = new Compatibility(values(find(base, "compatible-types")), values(find(base, "compatible-ids")), values(find(base, "compatible-materials")));
        } catch (Exception ignored) { }
        CACHE.put(cacheKey, new CachedCompatibility(stamp, loaded));
        return loaded;
    }

    private static synchronized void refreshGlobal() {
        Path file = ROOT.resolve("config.yml");
        long stamp = -1L;
        try { if (Files.isRegularFile(file)) stamp = Files.getLastModifiedTime(file).toMillis(); } catch (Exception ignored) { }
        if (stamp == globalStamp) return;
        boolean next = false;
        try {
            Map<String, Object> config = YamlLite.map(YamlLite.parse(file));
            Object raw = find(config, "locked-skins");
            next = raw instanceof Boolean value ? value : raw != null && Boolean.parseBoolean(String.valueOf(raw));
        } catch (Exception ignored) { }
        lockedSkins = next;
        globalStamp = stamp;
    }

    private static Set<String> values(Object raw) {
        Set<String> out = new LinkedHashSet<>();
        if (raw instanceof Collection<?> collection) for (Object value : collection) add(out, value);
        else if (raw != null) for (String value : String.valueOf(raw).split("[,;]")) add(out, value);
        return Set.copyOf(out);
    }

    private static void add(Set<String> out, Object value) {
        String text = value == null ? "" : String.valueOf(value).trim();
        if (!text.isEmpty()) out.add(text);
    }

    private static String material(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith("minecraft:") ? normalized.substring("minecraft:".length()) : normalized;
    }

    private static ApplyResult none() { return new ApplyResult(Result.NONE, ItemStack.EMPTY); }
    private static Object find(Map<String, Object> map, String key) { Object direct = map.get(key); if (direct != null) return direct; for (Map.Entry<String, Object> entry : map.entrySet()) if (entry.getKey().equalsIgnoreCase(key)) return entry.getValue(); return null; }
    @SuppressWarnings("unchecked") private static Map<String, Object> map(Object value) { return value instanceof Map<?, ?> ? (Map<String, Object>) value : Map.of(); }

    private record CachedCompatibility(long stamp, Compatibility compatibility) {}
    private record Compatibility(Set<String> types, Set<String> ids, Set<String> materials) {
        private static final Compatibility EMPTY = new Compatibility(Set.of(), Set.of(), Set.of());
    }
}
