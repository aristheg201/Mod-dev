package vn.svframe.mythiclibfabric;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Native Fabric passive-skill dispatcher matching MythicLib's passive trigger model.
 */
final class PassiveTriggerRuntime {
    enum Trigger {
        ATTACK,
        DAMAGED,
        KILL_ENTITY,
        SNEAK,
        TIMER,
        EQUIP_ARMOR,
        UNEQUIP_ARMOR,
        LOGIN;

        static Trigger parse(Object value) {
            if (value == null) return null;
            try {
                return valueOf(String.valueOf(value).trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
    }

    record PassiveSkill(String id, Trigger trigger, long timerPeriodMs) {
        PassiveSkill {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("Passive skill id cannot be blank");
            if (trigger == null) throw new IllegalArgumentException("Passive trigger cannot be null");
            timerPeriodMs = Math.max(50L, timerPeriodMs);
        }
    }

    private static final Map<Trigger, List<PassiveSkill>> SKILLS = new EnumMap<>(Trigger.class);
    private static final Map<UUID, Map<String, Long>> LAST_CAST = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> LAST_SNEAKING = new ConcurrentHashMap<>();
    private static final Map<UUID, List<String>> LAST_ARMOR = new ConcurrentHashMap<>();

    private static volatile boolean registered;

    private PassiveTriggerRuntime() {}

    static synchronized void registerFabricEvents() {
        if (registered) return;
        registered = true;

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.player;
            LAST_SNEAKING.put(player.getUuid(), player.isSneaking());
            LAST_ARMOR.put(player.getUuid(), armorSnapshot(player));
            fire(Trigger.LOGIN, player, player, Map.of("trigger", "LOGIN"));
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID id = handler.player.getUuid();
            LAST_CAST.remove(id);
            LAST_SNEAKING.remove(id);
            LAST_ARMOR.remove(id);
        });

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!world.isClient && player instanceof ServerPlayerEntity serverPlayer) {
                fire(Trigger.ATTACK, serverPlayer, entity, Map.of(
                        "trigger", "ATTACK",
                        "hand", hand.name()));
            }
            return ActionResult.PASS;
        });

        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamageTaken, damageTaken, blocked) -> {
            if (entity instanceof ServerPlayerEntity damaged) {
                Entity attacker = source.getAttacker();
                fire(Trigger.DAMAGED, damaged, attacker, Map.of(
                        "trigger", "DAMAGED",
                        "damage", damageTaken,
                        "base_damage", baseDamageTaken,
                        "blocked", blocked));
            }

            Entity attacker = source.getAttacker();
            if (attacker instanceof ServerPlayerEntity killer && !entity.isAlive()) {
                fire(Trigger.KILL_ENTITY, killer, entity, Map.of(
                        "trigger", "KILL_ENTITY",
                        "damage", damageTaken,
                        "base_damage", baseDamageTaken));
            }
        });
    }

    static synchronized void replaceDefinitions(Map<String, Map<String, Object>> definitions) {
        EnumMap<Trigger, List<PassiveSkill>> next = new EnumMap<>(Trigger.class);
        for (Map.Entry<String, Map<String, Object>> entry : definitions.entrySet()) {
            Trigger trigger = Trigger.parse(entry.getValue().get("trigger"));
            if (trigger == null) continue;

            long timer = 50L;
            if (trigger == Trigger.TIMER) {
                Object raw = entry.getValue().get("timer");
                if (raw == null) raw = entry.getValue().get("period");
                timer = Math.max(1L, asLong(raw, 1L)) * 50L;
            }
            next.computeIfAbsent(trigger, ignored -> new ArrayList<>())
                    .add(new PassiveSkill(entry.getKey(), trigger, timer));
        }

        SKILLS.clear();
        for (Map.Entry<Trigger, List<PassiveSkill>> entry : next.entrySet()) {
            SKILLS.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        LAST_CAST.clear();
    }

    static void tick(MinecraftServer server) {
        long now = System.currentTimeMillis();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            UUID id = player.getUuid();

            boolean sneaking = player.isSneaking();
            boolean previousSneaking = LAST_SNEAKING.getOrDefault(id, sneaking);
            if (sneaking && !previousSneaking) {
                fire(Trigger.SNEAK, player, player, Map.of("trigger", "SNEAK"));
            }
            LAST_SNEAKING.put(id, sneaking);

            List<String> armor = armorSnapshot(player);
            List<String> previousArmor = LAST_ARMOR.put(id, armor);
            if (previousArmor != null) {
                int slots = Math.min(previousArmor.size(), armor.size());
                for (int slot = 0; slot < slots; slot++) {
                    String before = previousArmor.get(slot);
                    String after = armor.get(slot);
                    if (before.equals(after)) continue;
                    if (!before.isEmpty()) {
                        fire(Trigger.UNEQUIP_ARMOR, player, player, Map.of(
                                "trigger", "UNEQUIP_ARMOR",
                                "armor_slot", slot,
                                "item", before));
                    }
                    if (!after.isEmpty()) {
                        fire(Trigger.EQUIP_ARMOR, player, player, Map.of(
                                "trigger", "EQUIP_ARMOR",
                                "armor_slot", slot,
                                "item", after));
                    }
                }
            }

            if (!player.isSpectator()) tickTimers(player, now);
        }
    }

    static String summary() {
        int count = 0;
        for (List<PassiveSkill> value : SKILLS.values()) count += value.size();
        return "passives=" + count;
    }

    private static void tickTimers(ServerPlayerEntity player, long now) {
        List<PassiveSkill> timers = SKILLS.get(Trigger.TIMER);
        if (timers == null || timers.isEmpty()) return;

        Map<String, Long> playerTimes = LAST_CAST.computeIfAbsent(player.getUuid(), ignored -> new HashMap<>());
        for (PassiveSkill passive : timers) {
            long last = playerTimes.getOrDefault(passive.id(), 0L);
            if (last + passive.timerPeriodMs() > now) continue;

            // MythicLib updates timer lastCast before executing the skill.
            playerTimes.put(passive.id(), now);
            MythicLibFabricMod.castSkill(passive.id(), player.getUuid(), player.getUuid(), Map.of(
                    "trigger", "TIMER",
                    "timer_period", passive.timerPeriodMs()));
        }
    }

    private static void fire(Trigger trigger, ServerPlayerEntity owner, Entity target, Map<String, ?> parameters) {
        List<PassiveSkill> values = SKILLS.get(trigger);
        if (values == null || values.isEmpty()) return;

        UUID targetId = target == null ? owner.getUuid() : target.getUuid();
        for (PassiveSkill passive : values) {
            MythicLibFabricMod.castSkill(passive.id(), owner.getUuid(), targetId, parameters);
        }
    }

    private static List<String> armorSnapshot(PlayerEntity player) {
        List<String> out = new ArrayList<>(4);
        for (ItemStack stack : player.getInventory().armor) {
            out.add(stack.isEmpty() ? "" : Registries.ITEM.getId(stack.getItem()).toString());
        }
        return List.copyOf(out);
    }

    private static long asLong(Object value, long fallback) {
        if (value instanceof Number number) return number.longValue();
        if (value == null) return fallback;
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
