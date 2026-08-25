package vn.svframe.mmoitemsfabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import vn.svframe.compat.YamlLite;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Ports MMOItems' DurabilityListener paths that vanilla never invokes for
 * items whose base material is not damageable: armor damage and melee use.
 */
public final class MMOItemsDurabilityIngressMod implements ModInitializer {
    private static final Logger LOG = Logger.getLogger("MMOItems-Fabric/Durability");
    private static final Path CONFIG = FabricLoader.getInstance().getConfigDir().resolve("MMOItems").resolve("config.yml");
    private static final EquipmentSlot[] ARMOR = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };
    private static final Set<String> IGNORED_DAMAGE = Set.of(
            "drown", "fall", "outofworld", "onfire", "inwall", "poison",
            "wither", "starve", "magic", "indirectmagic", "generickill"
    );
    private static final Map<UUID, Long> LAST_ATTACK = new ConcurrentHashMap<>();
    private static volatile int durabilityLossCap;
    private static volatile long configStamp = Long.MIN_VALUE;
    private static long ticks;

    @Override
    public void onInitialize() {
        reloadConfig();
        ServerLivingEntityEvents.AFTER_DAMAGE.register(MMOItemsDurabilityIngressMod::afterDamage);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> LAST_ATTACK.remove(handler.player.getUuid()));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (++ticks % 100L != 0L) return;
            try {
                long stamp = Files.exists(CONFIG) ? Files.getLastModifiedTime(CONFIG).toMillis() : Long.MIN_VALUE;
                if (stamp != configStamp) reloadConfig();
            } catch (IOException exception) {
                LOG.log(Level.WARNING, "Could not inspect MMOItems durability config", exception);
            }
        });
    }

    private static void afterDamage(net.minecraft.entity.LivingEntity victim, DamageSource source,
                                    float baseDamageTaken, float damageTaken, boolean blocked) {
        if (baseDamageTaken <= 0.0F) return;

        if (victim instanceof ServerPlayerEntity player && !ignored(source)) {
            int armorLoss = cap(Math.max(1, ((int) baseDamageTaken) / 4));
            for (EquipmentSlot slot : ARMOR) damageUndamageable(player, player.getEquippedStack(slot), armorLoss);
        }

        if (!(source.getAttacker() instanceof ServerPlayerEntity attacker) || !source.isDirect()) return;
        long now = System.currentTimeMillis();
        Long previous = LAST_ATTACK.put(attacker.getUuid(), now);
        if (previous != null && previous + 50L > now) return;
        damageUndamageable(attacker, attacker.getMainHandStack(), cap(1));
    }

    private static boolean ignored(DamageSource source) {
        String name = source.getName().toLowerCase(Locale.ROOT).replace("_", "").replace(".", "");
        return IGNORED_DAMAGE.contains(name);
    }

    private static void damageUndamageable(ServerPlayerEntity player, ItemStack stack, int amount) {
        if (amount <= 0 || stack == null || stack.isEmpty()) return;
        if (stack.getItem().getDefaultStack().isDamageable()) return;
        MMOItemsGameplayMod.Template template = MMOItemsGameplayMod.template(stack);
        if (template == null || template.maxDurability() <= 0) return;
        stack.damage(amount, player.getServerWorld(), player,
                broken -> player.playSound(SoundEvents.ENTITY_ITEM_BREAK, 1.0F, 1.0F));
    }

    private static int cap(int amount) {
        int limit = durabilityLossCap;
        return limit < 1 ? amount : Math.min(limit, amount);
    }

    private static void reloadConfig() {
        int cap = 0;
        try {
            if (Files.isRegularFile(CONFIG)) {
                Map<String, Object> root = YamlLite.map(YamlLite.parse(CONFIG));
                Map<String, Object> durability = YamlLite.map(root.get("durability"));
                Object raw = durability.get("loss_cap");
                if (raw == null) raw = durability.get("loss-cap");
                if (raw instanceof Number number) cap = Math.max(0, number.intValue());
                else if (raw != null) cap = Math.max(0, Integer.parseInt(String.valueOf(raw).trim()));
                configStamp = Files.getLastModifiedTime(CONFIG).toMillis();
            } else configStamp = Long.MIN_VALUE;
        } catch (Exception exception) {
            LOG.log(Level.WARNING, "Could not load MMOItems durability.loss_cap; keeping previous value", exception);
            return;
        }
        durabilityLossCap = cap;
    }
}
