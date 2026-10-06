package io.github.aristheg201.cobblemonworld.boss;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.pokemon.Pokemon;
import io.github.aristheg201.cobblemonworld.integration.SvFrameRpgBridge;
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
        TobaSkillRegistry.INSTANCE.load();

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
        SvFrameRpgBridge.markCombat(player);
        player.sendSystemMessage(Component.literal(
                "TOBA PHASE II — " + SvFrameRpgBridge.describe(player)
                        + " — Dash [Z] • Guard [X] • Break [C] • Purge [V] • Anchor [G] • Partner [H]"));
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
        var definition = TobaSkillRegistry.INSTANCE.get(skill);

        if (!state.ready(skill, tick)) {
            long remaining = state.remaining(skill, tick);
            player.sendSystemMessage(Component.literal(skill.name() + " cooldown: "
                    + String.format(java.util.Locale.ROOT, "%.1fs", remaining / 20.0)), true);
            return;
        }

        boolean valid = switch (skill) {
            case DASH, GUARD, ANCHOR -> true;
            case BREAK -> player.distanceToSqr(boss) <= 10.0 * 10.0 && boss.isBreakableState();
            case PURGE -> state.corruption() > 0;
            case PARTNER -> leadPokemon(player) != null;
        };
        if (!valid) {
            player.sendSystemMessage(Component.literal(switch (skill) {
                case BREAK -> "BREAK — no interrupt window";
                case PURGE -> "PURGE — no corruption to remove";
                case PARTNER -> "PARTNER — no lead Pokemon";
                default -> skill.name() + " — unavailable";
            }), true);
            return;
        }

        if (!SvFrameRpgBridge.consume(player, definition.resourceType(), definition.cost)) {
            var profile = SvFrameRpgBridge.snapshot(player);
            double current = definition.resourceType() == SvFrameRpgBridge.Resource.MANA ? profile.mana() : profile.stamina();
            player.sendSystemMessage(Component.literal(skill.name() + " — not enough "
                    + definition.resourceType().name().toLowerCase(java.util.Locale.ROOT)
                    + " (" + (int) Math.floor(current) + "/" + (int) Math.ceil(definition.cost) + ")"), true);
            return;
        }

        long cooldown = SvFrameRpgBridge.cooldownTicks(player, definition.cooldownTicks);
        switch (skill) {
            case DASH -> dash(player, state, tick, cooldown, definition);
            case GUARD -> guard(player, state, tick, cooldown, definition);
            case BREAK -> breakBoss(player, boss, state, tick, cooldown, definition);
            case PURGE -> purge(player, boss, state, tick, cooldown, definition);
            case ANCHOR -> anchor(player, state, tick, cooldown, definition);
            case PARTNER -> partner(player, boss, state, tick, cooldown, definition);
        }
    }

    private static void dash(ServerPlayer player, RpgCombatState state, long tick, long cooldown, TobaSkillRegistry.Definition definition) {
        Vec3 look = player.getLookAngle();
        Vec3 horizontal = new Vec3(look.x, 0.0, look.z);
        if (horizontal.lengthSqr() < 0.001) horizontal = new Vec3(0.0, 0.0, 1.0);
        horizontal = horizontal.normalize().scale(definition.magnitude);
        player.setDeltaMovement(player.getDeltaMovement().add(horizontal.x, 0.15, horizontal.z));
        state.setInvulnerableUntil(tick + definition.durationTicks);
        state.consume(RpgSkill.DASH, tick, cooldown);
        player.sendSystemMessage(Component.literal("DASH"), true);
    }

    private static void guard(ServerPlayer player, RpgCombatState state, long tick, long cooldown, TobaSkillRegistry.Definition definition) {
        state.setGuardUntil(tick + definition.durationTicks);
        state.consume(RpgSkill.GUARD, tick, cooldown);
        player.sendSystemMessage(Component.literal("GUARD — damage window protected"), true);
    }

    private static void breakBoss(ServerPlayer player, TobaEntity boss, RpgCombatState state, long tick, long cooldown, TobaSkillRegistry.Definition definition) {
        boss.stagger(definition.durationTicks);
        boss.applyRpgDamage(player, (float) definition.magnitude, SvFrameRpgBridge.DamageFlavor.PHYSICAL_SKILL);
        state.consume(RpgSkill.BREAK, tick, cooldown);
        player.sendSystemMessage(Component.literal("BREAK — TOBA staggered"), true);
    }

    private static void purge(ServerPlayer player, TobaEntity boss, RpgCombatState state, long tick, long cooldown, TobaSkillRegistry.Definition definition) {
        int stacks = state.corruption();
        state.purge();
        boss.applyRpgDamage(player,
                (float) (definition.magnitude + stacks * definition.secondaryMagnitude),
                SvFrameRpgBridge.DamageFlavor.MAGIC_SKILL);
        state.consume(RpgSkill.PURGE, tick, cooldown);
        player.sendSystemMessage(Component.literal("PURGE — corruption removed"), true);
    }

    private static void anchor(ServerPlayer player, RpgCombatState state, long tick, long cooldown, TobaSkillRegistry.Definition definition) {
        state.setAnchorUntil(tick + definition.durationTicks);
        state.consume(RpgSkill.ANCHOR, tick, cooldown);
        player.sendSystemMessage(Component.literal("ANCHOR — pull/knockback immunity"), true);
    }

    private static void partner(ServerPlayer player, TobaEntity boss, RpgCombatState state, long tick, long cooldown, TobaSkillRegistry.Definition definition) {
        Pokemon pokemon = leadPokemon(player);
        if (pokemon == null) return;
        float damage = (float) (definition.magnitude
                + Math.min(definition.maxBonus, pokemon.getLevel() * definition.secondaryMagnitude));
        boss.applyRpgDamage(player, damage, SvFrameRpgBridge.DamageFlavor.PARTNER_SKILL);
        player.heal(4.0F);
        state.consume(RpgSkill.PARTNER, tick, cooldown);
        player.sendSystemMessage(Component.literal(
                "PARTNER — " + pokemon.getDisplayName(false).getString() + " strikes TOBA"), true);
    }

    private static Pokemon leadPokemon(ServerPlayer player) {
        return Cobblemon.INSTANCE.getStorage().getParty(player).get(0);
    }
}
