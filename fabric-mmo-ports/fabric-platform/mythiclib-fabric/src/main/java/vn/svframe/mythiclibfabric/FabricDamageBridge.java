package vn.svframe.mythiclibfabric;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ItemStack;
import vn.svframe.mythiclibfabric.runtime.DamageType;
import vn.svframe.mythiclibfabric.runtime.DefenseFormula;
import vn.svframe.mythiclibfabric.runtime.StatProviderRegistry;

import java.nio.file.Path;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class FabricDamageBridge {
    private static final Logger LOG = Logger.getLogger("MythicLib-Fabric/Combat");
    private static final Path CONFIG = FabricLoader.getInstance().getConfigDir().resolve("MythicLib").resolve("config.yml");
    private static volatile MythicLibDamageSettings settings = MythicLibDamageSettings.defaults();

    private FabricDamageBridge() {}

    public static boolean reload() {
        try {
            settings = MythicLibDamageSettings.load(CONFIG);
            return true;
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to load MythicLib damage settings", e);
            return false;
        }
    }

    public static String summary() {
        MythicLibDamageSettings value = settings;
        return "providers=" + StatProviderRegistry.providerCount() + ",natural=" + value.naturalFormula() + ",elemental=" + value.elementalFormula();
    }

    public static float modifyAppliedDamage(LivingEntity target, DamageSource source, float vanillaModifiedDamage) {
        if (target == null || source == null || vanillaModifiedDamage <= 0.0f || target.getWorld().isClient()) return vanillaModifiedDamage;
        MythicLibDamageSettings value = settings;
        List<DamageType> types = classify(source, value);
        double damage = vanillaModifiedDamage;

        Entity attacker = source.getAttacker();
        if (attacker != null) {
            for (DamageType type : types) {
                double offense = StatProviderRegistry.stat(attacker.getUuid(), type.getOffenseStat());
                damage *= Math.max(0.0d, 1.0d + offense / 100.0d);
            }
        }

        for (DamageType type : types) {
            double reduction = StatProviderRegistry.stat(target.getUuid(), type.name() + "_DAMAGE_REDUCTION");
            damage *= Math.max(0.0d, 1.0d - reduction / 100.0d);
        }

        double defense = StatProviderRegistry.stat(target.getUuid(), "DEFENSE");
        if (defense != 0.0d && damage > 0.0d) {
            damage = DefenseFormula.calculateDamage(false, defense, damage, value.naturalFormula(), value.elementalFormula());
        }
        if (!Double.isFinite(damage)) return vanillaModifiedDamage;
        return (float) Math.max(0.0d, Math.min(Float.MAX_VALUE, damage));
    }

    public static List<DamageType> classify(DamageSource source) {
        return classify(source, settings);
    }

    /** Native 1.21.1 equivalent of Bukkit EntityDamageEvent.DamageCause classification. */
    public static String causeKey(DamageSource source) {
        if (source == null) return "CUSTOM";
        if (source.isOf(DamageTypes.MAGIC) || source.isOf(DamageTypes.INDIRECT_MAGIC)) return "MAGIC";
        if (source.isOf(DamageTypes.DRAGON_BREATH)) return "DRAGON_BREATH";
        if (source.isOf(DamageTypes.WITHER) || source.isOf(DamageTypes.WITHER_SKULL)) return "WITHER";
        if (source.isOf(DamageTypes.ON_FIRE)) return "FIRE_TICK";
        if (source.isOf(DamageTypes.IN_FIRE) || source.isOf(DamageTypes.CAMPFIRE)) return "FIRE";
        if (source.isOf(DamageTypes.LAVA)) return "LAVA";
        if (source.isOf(DamageTypes.HOT_FLOOR)) return "HOT_FLOOR";
        if (source.isOf(DamageTypes.FREEZE)) return "FREEZE";
        if (source.isOf(DamageTypes.STARVE)) return "STARVATION";
        if (source.isOf(DamageTypes.DRY_OUT)) return "DRYOUT";
        if (source.isOf(DamageTypes.DROWN)) return "DROWNING";
        if (source.isOf(DamageTypes.FALL)) return "FALL";
        if (source.isOf(DamageTypes.FLY_INTO_WALL)) return "FLY_INTO_WALL";
        if (source.isOf(DamageTypes.CACTUS) || source.isOf(DamageTypes.SWEET_BERRY_BUSH)) return "CONTACT";
        if (source.isOf(DamageTypes.CRAMMING)) return "CRAMMING";
        if (source.isOf(DamageTypes.SONIC_BOOM)) return "SONIC_BOOM";
        if (source.isOf(DamageTypes.LIGHTNING_BOLT)) return "LIGHTNING";
        if (source.isOf(DamageTypes.THORNS)) return "THORNS";
        if (source.isOf(DamageTypes.EXPLOSION) || source.isOf(DamageTypes.PLAYER_EXPLOSION) || source.isOf(DamageTypes.BAD_RESPAWN_POINT)) return "ENTITY_EXPLOSION";
        if (source.isOf(DamageTypes.FALLING_ANVIL) || source.isOf(DamageTypes.FALLING_BLOCK) || source.isOf(DamageTypes.FALLING_STALACTITE)) return "FALLING_BLOCK";
        if (source.getSource() instanceof ProjectileEntity || source.isOf(DamageTypes.ARROW) || source.isOf(DamageTypes.TRIDENT) || source.isOf(DamageTypes.MOB_PROJECTILE) || source.isOf(DamageTypes.FIREWORKS) || source.isOf(DamageTypes.THROWN)) return "PROJECTILE";
        if (source.getAttacker() instanceof LivingEntity) return "ENTITY_ATTACK";
        return "CUSTOM";
    }

    private static List<DamageType> classify(DamageSource source, MythicLibDamageSettings value) {
        if (source.isOf(DamageTypes.MAGIC) || source.isOf(DamageTypes.INDIRECT_MAGIC) || source.isOf(DamageTypes.DRAGON_BREATH)) {
            return value.source(source.isOf(DamageTypes.DRAGON_BREATH) ? "DRAGON_BREATH" : "MAGIC", List.of(DamageType.MAGIC));
        }
        if (source.isOf(DamageTypes.WITHER) || source.isOf(DamageTypes.WITHER_SKULL)) return value.source("WITHER", List.of(DamageType.MAGIC, DamageType.DOT));
        if (source.isOf(DamageTypes.ON_FIRE)) return value.source("FIRE_TICK", List.of(DamageType.PHYSICAL, DamageType.DOT));
        if (source.isOf(DamageTypes.IN_FIRE) || source.isOf(DamageTypes.CAMPFIRE)) return value.source("FIRE", List.of(DamageType.PHYSICAL));
        if (source.isOf(DamageTypes.LAVA)) return value.source("LAVA", List.of(DamageType.PHYSICAL));
        if (source.isOf(DamageTypes.HOT_FLOOR)) return value.source("HOT_FLOOR", List.of(DamageType.PHYSICAL));
        if (source.isOf(DamageTypes.FREEZE)) return value.source("FREEZE", List.of(DamageType.DOT));
        if (source.isOf(DamageTypes.STARVE)) return value.source("STARVATION", List.of(DamageType.DOT));
        if (source.isOf(DamageTypes.DRY_OUT)) return value.source("DRYOUT", List.of(DamageType.DOT));
        if (source.isOf(DamageTypes.DROWN)) return value.source("DROWNING", List.of(DamageType.PHYSICAL));
        if (source.isOf(DamageTypes.FALL)) return value.source("FALL", List.of(DamageType.PHYSICAL));
        if (source.isOf(DamageTypes.FLY_INTO_WALL)) return value.source("FLY_INTO_WALL", List.of(DamageType.PHYSICAL));
        if (source.isOf(DamageTypes.CACTUS) || source.isOf(DamageTypes.SWEET_BERRY_BUSH)) return value.source("CONTACT", List.of(DamageType.PHYSICAL));
        if (source.isOf(DamageTypes.CRAMMING)) return value.source("CRAMMING", List.of(DamageType.PHYSICAL));
        if (source.isOf(DamageTypes.SONIC_BOOM)) return value.source("SONIC_BOOM", List.of(DamageType.PHYSICAL));
        if (source.isOf(DamageTypes.LIGHTNING_BOLT)) return value.source("LIGHTNING", List.of(DamageType.PHYSICAL));
        if (source.isOf(DamageTypes.THORNS)) return value.source("THORNS", List.of(DamageType.PHYSICAL));
        if (source.isOf(DamageTypes.EXPLOSION) || source.isOf(DamageTypes.PLAYER_EXPLOSION) || source.isOf(DamageTypes.BAD_RESPAWN_POINT)) return value.source("ENTITY_EXPLOSION", List.of(DamageType.PHYSICAL));
        if (source.isOf(DamageTypes.FALLING_ANVIL) || source.isOf(DamageTypes.FALLING_BLOCK) || source.isOf(DamageTypes.FALLING_STALACTITE)) return value.source("FALLING_BLOCK", List.of(DamageType.PHYSICAL));

        Entity direct = source.getSource();
        if (direct instanceof ProjectileEntity || source.isOf(DamageTypes.ARROW) || source.isOf(DamageTypes.TRIDENT) || source.isOf(DamageTypes.MOB_PROJECTILE) || source.isOf(DamageTypes.FIREWORKS) || source.isOf(DamageTypes.THROWN)) {
            return value.projectile();
        }
        Entity attacker = source.getAttacker();
        if (attacker instanceof LivingEntity) {
            ItemStack weapon = source.getWeaponStack();
            if (weapon != null && !weapon.isEmpty()) return value.meleeWeapon();
            if (source.isOf(DamageTypes.PLAYER_ATTACK)) return value.meleeUnarmed();
            return value.meleeDefault();
        }
        return value.meleeDefault();
    }
}
