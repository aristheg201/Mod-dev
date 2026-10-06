package io.github.aristheg201.cobblemonworld.integration;

import io.github.aristheg201.cobblemonworld.CobblemonWorldMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.UUID;

/**
 * Runtime bridge for the server's existing SVFrame stack.
 *
 * This intentionally uses reflection so Cobblemon World does not hard-depend on
 * private SVFrame jars at compile time. When SVFrameMMO/SVFrameLib are installed,
 * calls are routed through their native PlayerData, resource and PlayerMetadata
 * damage APIs. If either module is absent, vanilla-safe fallbacks remain active.
 */
public final class SvFrameRpgBridge {
    private static final String MMO_ID = "svframemmo";
    private static final String LIB_ID = "svframelib";

    private static volatile Api api;
    private static volatile boolean resolved;
    private static volatile boolean warned;

    private SvFrameRpgBridge() {}

    public enum Resource {
        NONE, STAMINA, MANA
    }

    public enum DamageFlavor {
        PHYSICAL_SKILL("SKILL", "PHYSICAL"),
        MAGIC_SKILL("SKILL", "MAGIC"),
        PARTNER_SKILL("SKILL", "MINION");

        private final String[] svFrameTypes;
        DamageFlavor(String... svFrameTypes) { this.svFrameTypes = svFrameTypes; }
        public String[] svFrameTypes() { return svFrameTypes; }
    }

    public record Snapshot(
            boolean available,
            boolean libAvailable,
            String classId,
            int level,
            double mana,
            double maxMana,
            double stamina,
            double maxStamina,
            int strength,
            int dexterity,
            int intelligence,
            double cooldownReduction
    ) {
        public static Snapshot vanilla(ServerPlayer player) {
            return new Snapshot(false, FabricLoader.getInstance().isModLoaded(LIB_ID),
                    "VANILLA", 1, 0, 0, 0, 0, 0, 0, 0, 0);
        }
    }

    public static Snapshot snapshot(ServerPlayer player) {
        Api api = api();
        if (api == null) return Snapshot.vanilla(player);
        try {
            Object data = api.playerData(player.getUUID());
            Object mmoData = api.getMMOPlayerData.invoke(data);
            Object statMap = api.getStatMap.invoke(mmoData);
            Object attrs = api.getAttributes.invoke(data);

            return new Snapshot(
                    true,
                    api.libDamageAvailable,
                    string(api.getClassId.invoke(data), "HUMAN"),
                    intValue(api.getLevel.invoke(data), 1),
                    number(api.getMana.invoke(data), 0),
                    stat(api, statMap, "MAX_MANA"),
                    number(api.getStamina.invoke(data), 0),
                    stat(api, statMap, "MAX_STAMINA"),
                    attribute(api, attrs, "strength"),
                    attribute(api, attrs, "dexterity"),
                    attribute(api, attrs, "intelligence"),
                    Math.max(0, stat(api, statMap, "COOLDOWN_REDUCTION"))
            );
        } catch (Throwable failure) {
            warnOnce("Could not read SVFrameMMO profile; using vanilla-safe fallback.", failure);
            return Snapshot.vanilla(player);
        }
    }

    public static long cooldownTicks(ServerPlayer player, long baseTicks) {
        Snapshot snapshot = snapshot(player);
        if (!snapshot.available()) return Math.max(1L, baseTicks);
        double reduction = Math.min(65.0, Math.max(0.0, snapshot.cooldownReduction()));
        return Math.max(1L, Math.round(baseTicks * (1.0 - reduction / 100.0)));
    }

    public static boolean consume(ServerPlayer player, Resource resource, double amount) {
        if (resource == Resource.NONE || amount <= 0) return true;
        Api api = api();
        if (api == null) return true;

        try {
            Object data = api.playerData(player.getUUID());
            double current = resource == Resource.STAMINA
                    ? number(api.getStamina.invoke(data), 0)
                    : number(api.getMana.invoke(data), 0);
            if (current + 1.0E-6 < amount) return false;

            Method setter = resource == Resource.STAMINA ? api.setStamina : api.setMana;
            Object result = setter.invoke(data, current - amount, api.skillUpdateReason);
            api.markCombat.invoke(data);
            return !(result instanceof Boolean changed) || changed;
        } catch (Throwable failure) {
            warnOnce("SVFrameMMO resource hook failed; allowing skill without external resource mutation.", failure);
            return true;
        }
    }

    public static void markCombat(ServerPlayer player) {
        Api api = api();
        if (api == null) return;
        try {
            api.markCombat.invoke(api.playerData(player.getUUID()));
        } catch (Throwable failure) {
            warnOnce("SVFrameMMO combat-state hook failed.", failure);
        }
    }

    /**
     * Applies a player-owned RPG hit through SVFrameLib's PlayerMetadata.attack API.
     *
     * @return true if SVFrameLib accepted/handled the attack call. false means caller
     * should fall back to vanilla damage.
     */
    public static boolean attack(ServerPlayer player, LivingEntity target, double amount, DamageFlavor flavor) {
        Api api = api();
        if (api == null || !api.libDamageAvailable || amount <= 0) return false;
        try {
            Object data = api.playerData(player.getUUID());
            Object mmoData = api.getMMOPlayerData.invoke(data);
            Object metadata = api.playerMetadataConstructor.newInstance(mmoData);

            Object damageTypes = Array.newInstance(api.damageTypeClass, flavor.svFrameTypes().length);
            for (int i = 0; i < flavor.svFrameTypes().length; i++) {
                Array.set(damageTypes, i, enumConstant(api.damageTypeClass, flavor.svFrameTypes()[i]));
            }

            api.playerMetadataAttack.invoke(metadata, target, amount, true, damageTypes);
            api.markCombat.invoke(data);
            return true;
        } catch (Throwable failure) {
            warnOnce("SVFrameLib damage hook failed; falling back to vanilla damage.", failure);
            return false;
        }
    }

    public static String describe(ServerPlayer player) {
        Snapshot s = snapshot(player);
        if (!s.available()) return "SVFrameMMO unavailable";
        return s.classId() + " Lv." + s.level()
                + " | STA " + whole(s.stamina()) + "/" + whole(s.maxStamina())
                + " | MANA " + whole(s.mana()) + "/" + whole(s.maxMana());
    }

    private static int whole(double value) { return (int) Math.round(value); }

    private static Api api() {
        if (resolved) return api;
        synchronized (SvFrameRpgBridge.class) {
            if (resolved) return api;
            resolved = true;

            if (!FabricLoader.getInstance().isModLoaded(MMO_ID)) {
                CobblemonWorldMod.LOGGER.info("SVFrameMMO not installed; Cobblemon World RPG bridge will use vanilla fallbacks.");
                return null;
            }

            try {
                Class<?> svFrameMmo = Class.forName("vn.svframe.svframemmo.SVFrameMMO");
                Object manager = svFrameMmo.getMethod("playerData").invoke(null);
                Method managerGet = manager.getClass().getMethod("get", UUID.class);
                Object sampleManager = manager;

                // Resolve PlayerData API from the manager's declared return type.
                Class<?> dataClass = managerGet.getReturnType();
                Method getLevel = dataClass.getMethod("getLevel");
                Method getClassId = dataClass.getMethod("getClassId");
                Method getMana = dataClass.getMethod("getMana");
                Method getStamina = dataClass.getMethod("getStamina");
                Method getAttributes = dataClass.getMethod("getAttributes");
                Method getMMOPlayerData = dataClass.getMethod("getMMOPlayerData");
                Method markCombat = dataClass.getMethod("markCombat");

                Class<?> reasonClass = Class.forName("vn.svframe.svframelib.player.resource.ResourceUpdateReason");
                Object skillReason = enumConstant(reasonClass, "SKILL");
                Method setMana = dataClass.getMethod("setMana", double.class, reasonClass);
                Method setStamina = dataClass.getMethod("setStamina", double.class, reasonClass);

                Class<?> attrsClass = getAttributes.getReturnType();
                Method getAttribute = attrsClass.getMethod("getAttribute", String.class);

                Class<?> mmoDataClass = getMMOPlayerData.getReturnType();
                Method getStatMap = mmoDataClass.getMethod("getStatMap");
                Class<?> statMapClass = getStatMap.getReturnType();
                Method getStat = statMapClass.getMethod("getStat", String.class);

                boolean libAvailable = FabricLoader.getInstance().isModLoaded(LIB_ID);
                Constructor<?> metadataCtor = null;
                Method metadataAttack = null;
                Class<?> damageTypeClass = null;

                if (libAvailable) {
                    Class<?> metadataClass = Class.forName("vn.svframe.svframelib.player.PlayerMetadata");
                    metadataCtor = metadataClass.getConstructor(mmoDataClass);
                    damageTypeClass = Class.forName("vn.svframe.svframelib.damage.DamageType");
                    for (Method method : metadataClass.getMethods()) {
                        Class<?>[] p = method.getParameterTypes();
                        if (!method.getName().equals("attack") || p.length != 4) continue;
                        if (p[1] != double.class || p[2] != boolean.class || !p[3].isArray()) continue;
                        if (!p[3].getComponentType().equals(damageTypeClass)) continue;
                        metadataAttack = method;
                        break;
                    }
                    if (metadataAttack == null) {
                        libAvailable = false;
                        CobblemonWorldMod.LOGGER.warn("SVFrameLib PlayerMetadata.attack overload was not found; custom RPG damage will use vanilla fallback.");
                    }
                }

                api = new Api(
                        sampleManager, managerGet,
                        getLevel, getClassId, getMana, getStamina,
                        getAttributes, getAttribute, getMMOPlayerData,
                        getStatMap, getStat, setMana, setStamina, markCombat,
                        skillReason, libAvailable, metadataCtor, metadataAttack, damageTypeClass
                );
                CobblemonWorldMod.LOGGER.info("SVFrame RPG bridge ready: SVFrameMMO=true, SVFrameLibDamage={}.", libAvailable);
                return api;
            } catch (Throwable failure) {
                warnOnce("Could not initialize SVFrame RPG bridge; using vanilla fallbacks.", failure);
                api = null;
                return null;
            }
        }
    }

    private static int attribute(Api api, Object attrs, String id) throws Exception {
        return intValue(api.getAttribute.invoke(attrs, id), 0);
    }

    private static double stat(Api api, Object statMap, String id) throws Exception {
        return number(api.getStat.invoke(statMap, id), 0);
    }

    private static Object enumConstant(Class<?> enumClass, String name) {
        @SuppressWarnings({"rawtypes", "unchecked"})
        Object value = Enum.valueOf((Class<? extends Enum>) enumClass.asSubclass(Enum.class), name.toUpperCase(Locale.ROOT));
        return value;
    }

    private static double number(Object value, double fallback) {
        return value instanceof Number n ? n.doubleValue() : fallback;
    }

    private static int intValue(Object value, int fallback) {
        return value instanceof Number n ? n.intValue() : fallback;
    }

    private static String string(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private static void warnOnce(String message, Throwable failure) {
        if (warned) return;
        warned = true;
        CobblemonWorldMod.LOGGER.warn(message, failure);
    }

    private record Api(
            Object manager,
            Method managerGet,
            Method getLevel,
            Method getClassId,
            Method getMana,
            Method getStamina,
            Method getAttributes,
            Method getAttribute,
            Method getMMOPlayerData,
            Method getStatMap,
            Method getStat,
            Method setMana,
            Method setStamina,
            Method markCombat,
            Object skillUpdateReason,
            boolean libDamageAvailable,
            Constructor<?> playerMetadataConstructor,
            Method playerMetadataAttack,
            Class<?> damageTypeClass
    ) {
        Object playerData(UUID id) throws Exception {
            return managerGet.invoke(manager, id);
        }
    }
}
