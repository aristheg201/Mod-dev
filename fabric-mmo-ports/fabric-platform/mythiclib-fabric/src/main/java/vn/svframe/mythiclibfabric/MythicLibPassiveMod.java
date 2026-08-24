package vn.svframe.mythiclibfabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Fabric lifecycle/event adapter for MythicLib's legacy passive-skill runtime. */
public final class MythicLibPassiveMod implements ModInitializer {
    private record ActionKey(UUID player, LegacyTriggerType trigger) {}

    private static final Map<ActionKey, Long> LAST_ACTION_TICK = new ConcurrentHashMap<>();

    @Override
    public void onInitialize() {
        registerLifecycle();
        registerCombat();
        registerInteraction();
        registerBlocks();
    }

    private static void registerLifecycle() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            long tick = MythicLibFabricMod.currentTick();
            PassiveSkillRuntime.tick(tick);
            LAST_ACTION_TICK.entrySet().removeIf(entry -> tick - entry.getValue() > 2L);
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.player;
            PassiveSkillRuntime.fire(player.getUuid(), LegacyTriggerType.LOGIN, player.getUuid(), Map.of());
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID id = handler.player.getUuid();
            PassiveSkillRuntime.clear(id);
            LAST_ACTION_TICK.keySet().removeIf(key -> key.player().equals(id));
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            PassiveSkillRuntime.clearAll();
            LAST_ACTION_TICK.clear();
        });
    }

    private static void registerCombat() {
        ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, baseDamageTaken, damageTaken, blocked) -> {
            if (victim instanceof ServerPlayerEntity player) {
                Map<String, Object> context = damageContext(source.getAttacker(), source.getSource(), baseDamageTaken, damageTaken, blocked);
                PassiveSkillRuntime.fire(player.getUuid(), LegacyTriggerType.DAMAGED, attackerId(source.getAttacker()), context);
                if (source.getAttacker() != null) {
                    PassiveSkillRuntime.fire(player.getUuid(), LegacyTriggerType.DAMAGED_BY_ENTITY, source.getAttacker().getUuid(), context);
                }
            }
        });

        ServerLivingEntityEvents.AFTER_DEATH.register((victim, source) -> {
            if (victim instanceof ServerPlayerEntity dead) {
                PassiveSkillRuntime.fire(dead.getUuid(), LegacyTriggerType.DEATH, attackerId(source.getAttacker()), entityContext(source.getAttacker()));
            }

            Entity attacker = source.getAttacker();
            if (attacker instanceof ServerPlayerEntity killer) {
                LegacyTriggerType trigger = victim instanceof PlayerEntity
                        ? LegacyTriggerType.KILL_PLAYER
                        : LegacyTriggerType.KILL_ENTITY;
                PassiveSkillRuntime.fire(killer.getUuid(), trigger, victim.getUuid(), entityContext(victim));
            }
        });
    }

    private static void registerInteraction() {
        AttackEntityCallback.EVENT.register((player, world, hand, target, hitResult) -> {
            if (world.isClient || !(player instanceof ServerPlayerEntity serverPlayer)) return ActionResult.PASS;
            LegacyTriggerType trigger = player.isSneaking() ? LegacyTriggerType.SHIFT_LEFT_CLICK : LegacyTriggerType.LEFT_CLICK;
            Map<String, Object> context = entityContext(target);
            context.put("hand", hand.name());
            fireOnce(serverPlayer.getUuid(), trigger, target.getUuid(), context);
            PassiveSkillRuntime.fire(serverPlayer.getUuid(), LegacyTriggerType.ATTACK, target.getUuid(), context);
            return ActionResult.PASS;
        });

        UseEntityCallback.EVENT.register((player, world, hand, target, hitResult) -> {
            if (world.isClient || !(player instanceof ServerPlayerEntity serverPlayer)) return ActionResult.PASS;
            LegacyTriggerType trigger = player.isSneaking() ? LegacyTriggerType.SHIFT_RIGHT_CLICK : LegacyTriggerType.RIGHT_CLICK;
            Map<String, Object> context = entityContext(target);
            context.put("hand", hand.name());
            fireOnce(serverPlayer.getUuid(), trigger, target.getUuid(), context);
            return ActionResult.PASS;
        });

        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getStackInHand(hand);
            if (!world.isClient && player instanceof ServerPlayerEntity serverPlayer) {
                LegacyTriggerType trigger = player.isSneaking() ? LegacyTriggerType.SHIFT_RIGHT_CLICK : LegacyTriggerType.RIGHT_CLICK;
                Map<String, Object> context = itemContext(stack);
                context.put("hand", hand.name());
                fireOnce(serverPlayer.getUuid(), trigger, serverPlayer.getUuid(), context);
            }
            return TypedActionResult.pass(stack);
        });
    }

    private static void registerBlocks() {
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (world.isClient || !(player instanceof ServerPlayerEntity serverPlayer)) return ActionResult.PASS;
            LegacyTriggerType trigger = player.isSneaking() ? LegacyTriggerType.SHIFT_LEFT_CLICK : LegacyTriggerType.LEFT_CLICK;
            Map<String, Object> context = blockContext(world.getBlockState(pos), pos);
            context.put("hand", hand.name());
            context.put("face", direction.name());
            fireOnce(serverPlayer.getUuid(), trigger, serverPlayer.getUuid(), context);
            return ActionResult.PASS;
        });

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClient || !(player instanceof ServerPlayerEntity serverPlayer)) return ActionResult.PASS;
            LegacyTriggerType trigger = player.isSneaking() ? LegacyTriggerType.SHIFT_RIGHT_CLICK : LegacyTriggerType.RIGHT_CLICK;
            Map<String, Object> context = blockContext(world.getBlockState(hitResult.getBlockPos()), hitResult.getBlockPos());
            context.put("hand", hand.name());
            context.put("face", hitResult.getSide().name());
            fireOnce(serverPlayer.getUuid(), trigger, serverPlayer.getUuid(), context);
            return ActionResult.PASS;
        });

        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (!(player instanceof ServerPlayerEntity serverPlayer)) return;
            PassiveSkillRuntime.fire(serverPlayer.getUuid(), LegacyTriggerType.BREAK_BLOCK, serverPlayer.getUuid(), blockContext(state, pos));
        });
    }

    private static int fireOnce(UUID owner, LegacyTriggerType trigger, UUID target, Map<String, ?> context) {
        long tick = MythicLibFabricMod.currentTick();
        ActionKey key = new ActionKey(owner, trigger);
        Long previous = LAST_ACTION_TICK.put(key, tick);
        if (previous != null && previous == tick) return 0;
        return PassiveSkillRuntime.fire(owner, trigger, target, context);
    }

    private static UUID attackerId(Entity attacker) {
        return attacker == null ? null : attacker.getUuid();
    }

    private static Map<String, Object> damageContext(Entity attacker, Entity directSource,
                                                      float baseDamage, float damage, boolean blocked) {
        Map<String, Object> out = entityContext(attacker);
        out.put("base-damage", baseDamage);
        out.put("damage", damage);
        out.put("blocked", blocked);
        if (directSource != null) {
            out.put("source-uuid", directSource.getUuidAsString());
            out.put("source-type", Registries.ENTITY_TYPE.getId(directSource.getType()).toString());
        }
        return out;
    }

    private static Map<String, Object> entityContext(Entity entity) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (entity == null) return out;
        out.put("target-uuid", entity.getUuidAsString());
        out.put("target-name", entity.getName().getString());
        out.put("target-type", Registries.ENTITY_TYPE.getId(entity.getType()).toString());
        out.put("target-x", entity.getX());
        out.put("target-y", entity.getY());
        out.put("target-z", entity.getZ());
        return out;
    }

    private static Map<String, Object> itemContext(ItemStack stack) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (stack == null || stack.isEmpty()) return out;
        out.put("item", Registries.ITEM.getId(stack.getItem()).toString());
        out.put("item-count", stack.getCount());
        return out;
    }

    private static Map<String, Object> blockContext(BlockState state, BlockPos pos) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("block", Registries.BLOCK.getId(state.getBlock()).toString());
        out.put("block-x", pos.getX());
        out.put("block-y", pos.getY());
        out.put("block-z", pos.getZ());
        return out;
    }

    public static PassiveSkillRuntime.Binding register(UUID owner,
                                                        String key,
                                                        String trigger,
                                                        String skillId,
                                                        Map<String, ?> parameters,
                                                        long cooldownTicks,
                                                        long timerPeriodTicks) {
        return PassiveSkillRuntime.register(owner, key, LegacyTriggerType.parse(trigger), skillId,
                parameters, cooldownTicks, timerPeriodTicks);
    }

    public static int fire(UUID owner, String trigger, UUID target, Map<String, ?> context) {
        return PassiveSkillRuntime.fire(owner, LegacyTriggerType.parse(trigger), target, context);
    }

    public static boolean unregister(UUID owner, UUID bindingId) {
        return PassiveSkillRuntime.unregister(owner, bindingId);
    }

    public static int unregisterByKey(UUID owner, String key) {
        return PassiveSkillRuntime.unregisterByKey(owner, key);
    }
}
