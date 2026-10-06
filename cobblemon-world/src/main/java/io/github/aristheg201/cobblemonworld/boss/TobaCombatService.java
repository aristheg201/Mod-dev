package io.github.aristheg201.cobblemonworld.boss;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.pokemon.Pokemon;
import io.github.aristheg201.cobblemonworld.network.RpgSkillPayload;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TobaCombatService {
    private static final Map<UUID, RpgCombatState> STATES = new ConcurrentHashMap<>();

    private TobaCombatService() {}

    public static void register() {
        PayloadTypeRegistry.playC2S().register(RpgSkillPayload.TYPE, RpgSkillPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(RpgSkillPayload.TYPE, (payload, context) ->
                context.server().execute(() -> useSkill(context.player(), payload.skill())));

        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!(entity instanceof ServerPlayer player)) return true;
            if (!(source.getEntity() instanceof TobaEntity)) return true;
            RpgCombatState state = STATES.get(player.getUUID());
            return state == null || !state.protectedAt(player.serverLevel().getGameTime());
        });

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer player
                    && TobaEncounterService.state(player) == TobaBossState.PHASE_TWO_RPG) {
                TobaEncounterService.resetAfterFailure(player);
            }
        });
    }

    public static void begin(ServerPlayer player) {
        STATES.computeIfAbsent(player.getUUID(), ignored -> new RpgCombatState()).reset();
        player.sendSystemMessage(Component.literal(
                "TOBA PHASE II — Dash [Z] • Guard [X] • Break [C] • Purge [V] • Anchor [G] • Partner [H]"));
    }

    public static void reset(ServerPlayer player) {
        RpgCombatState state = STATES.remove(player.getUUID());
        if (state != null) state.reset();
    }

    public static boolean isAnchored(ServerPlayer player) {
        RpgCombatState state = STATES.get(player.getUUID());
        return state != null && state.anchoredAt(player.serverLevel().getGameTime());
    }

    public static int corruption(ServerPlayer player) {
        RpgCombatState state = STATES.get(player.getUUID());
        return state == null ? 0 : state.corruption();
    }

    public static void addCorruption(ServerPlayer player, int amount) {
        RpgCombatState state = STATES.computeIfAbsent(player.getUUID(), ignored -> new RpgCombatState());
        state.addCorruption(amount);
        player.sendSystemMessage(Component.literal("Corruption: " + state.corruption() + "/10"), true);
    }

    public static void useSkill(ServerPlayer player, String rawSkill) {
        if (TobaEncounterService.state(player) != TobaBossState.PHASE_TWO_RPG) return;

        RpgSkill skill;
        try {
            skill = RpgSkill.valueOf(rawSkill.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return;
        }

        TobaEntity boss = TobaEncounterService.activeBoss(player);
        if (boss == null || !boss.isAlive() || player.distanceToSqr(boss) > 64.0 * 64.0) return;

        long tick = player.serverLevel().getGameTime();
        RpgCombatState state = STATES.computeIfAbsent(player.getUUID(), ignored -> new RpgCombatState());
        if (!state.ready(skill, tick)) {
            long remaining = state.remaining(skill, tick);
            player.sendSystemMessage(Component.literal(skill.name() + " cooldown: "
                    + String.format(java.util.Locale.ROOT, "%.1fs", remaining / 20.0)), true);
            return;
        }

        switch (skill) {
            case DASH -> dash(player, state, tick);
            case GUARD -> guard(player, state, tick);
            case BREAK -> breakBoss(player, boss, state, tick);
            case PURGE -> purge(player, boss, state, tick);
            case ANCHOR -> anchor(player, state, tick);
            case PARTNER -> partner(player, boss, state, tick);
        }
    }

    private static void dash(ServerPlayer player, RpgCombatState state, long tick) {
        Vec3 look = player.getLookAngle();
        Vec3 horizontal = new Vec3(look.x, 0.0, look.z);
        if (horizontal.lengthSqr() < 0.001) horizontal = new Vec3(0.0, 0.0, 1.0);
        horizontal = horizontal.normalize().scale(1.65);
        player.setDeltaMovement(player.getDeltaMovement().add(horizontal.x, 0.15, horizontal.z));
        state.setInvulnerableUntil(tick + 12);
        state.consume(RpgSkill.DASH, tick, 60);
        player.sendSystemMessage(Component.literal("DASH"), true);
    }

    private static void guard(ServerPlayer player, RpgCombatState state, long tick) {
        state.setGuardUntil(tick + 30);
        state.consume(RpgSkill.GUARD, tick, 100);
        player.sendSystemMessage(Component.literal("GUARD — damage window protected"), true);
    }

    private static void breakBoss(ServerPlayer player, TobaEntity boss, RpgCombatState state, long tick) {
        if (player.distanceToSqr(boss) > 10.0 * 10.0 || !boss.isBreakableState()) {
            player.sendSystemMessage(Component.literal("BREAK — no interrupt window"), true);
            return;
        }
        boss.stagger(50);
        boss.applyRpgDamage(player, 28.0F);
        state.consume(RpgSkill.BREAK, tick, 160);
        player.sendSystemMessage(Component.literal("BREAK — TOBA staggered"), true);
    }

    private static void purge(ServerPlayer player, TobaEntity boss, RpgCombatState state, long tick) {
        if (state.corruption() <= 0) {
            player.sendSystemMessage(Component.literal("PURGE — no corruption to remove"), true);
            return;
        }
        int stacks = state.corruption();
        state.purge();
        boss.applyRpgDamage(player, 8.0F + stacks * 2.0F);
        state.consume(RpgSkill.PURGE, tick, 220);
        player.sendSystemMessage(Component.literal("PURGE — corruption removed"), true);
    }

    private static void anchor(ServerPlayer player, RpgCombatState state, long tick) {
        state.setAnchorUntil(tick + 80);
        state.consume(RpgSkill.ANCHOR, tick, 180);
        player.sendSystemMessage(Component.literal("ANCHOR — pull/knockback immunity"), true);
    }

    private static void partner(ServerPlayer player, TobaEntity boss, RpgCombatState state, long tick) {
        Pokemon pokemon = Cobblemon.INSTANCE.getStorage().getParty(player).get(0);
        if (pokemon == null) {
            player.sendSystemMessage(Component.literal("PARTNER — no lead Pokemon"), true);
            return;
        }
        float damage = 18.0F + Math.min(30.0F, pokemon.getLevel() * 0.3F);
        boss.applyRpgDamage(player, damage);
        player.heal(4.0F);
        state.consume(RpgSkill.PARTNER, tick, 300);
        player.sendSystemMessage(Component.literal(
                "PARTNER — " + pokemon.getDisplayName(false).getString() + " strikes TOBA"), true);
    }
}
