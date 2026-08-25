package vn.svframe.mythiclibfabric;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import vn.svframe.mythiclibfabric.runtime.NativePlayerData;
import vn.svframe.mythiclibfabric.runtime.NativeStatEngine;
import vn.svframe.mythiclibfabric.runtime.session.CooldownInfoRuntime;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/** Native Brigadier implementation of MythicLib 1.7.1 command surface. */
final class MythicLibCommands {
    private static final String ADMIN = "mythiclib.admin";
    private static final String HEALTH_SCALE = "mythiclib.mythiclib.command.healthscale";
    private static final String TEMP_STAT = "mythiclib.tempstat";

    private MythicLibCommands() {}

    static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(main("mythiclib"));
        dispatcher.register(main("ml"));
        dispatcher.register(healthScale("healthscale"));
        dispatcher.register(mmoTempStat());
    }

    private static LiteralArgumentBuilder<ServerCommandSource> main(String name) {
        LiteralArgumentBuilder<ServerCommandSource> root = literal(name).requires(source -> permitted(source, ADMIN));
        root.then(literal("reload").executes(ctx -> reload(ctx.getSource())));
        root.then(literal("cast")
                .then(argument("skill", StringArgumentType.word())
                        .executes(ctx -> cast(ctx.getSource(), StringArgumentType.getString(ctx, "skill"), null))
                        .then(argument("target", EntityArgumentType.player())
                                .executes(ctx -> cast(ctx.getSource(), StringArgumentType.getString(ctx, "skill"), EntityArgumentType.getPlayer(ctx, "target"))))));
        root.then(literal("damage")
                .then(argument("player", EntityArgumentType.player())
                        .then(argument("target", EntityArgumentType.entity())
                                .then(argument("value", DoubleArgumentType.doubleArg(0.0d))
                                        .executes(ctx -> damage(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player"), EntityArgumentType.getEntity(ctx, "target"), DoubleArgumentType.getDouble(ctx, "value")))))));
        root.then(statTree());
        root.then(cooldownTree());
        root.then(tempStatTree());
        root.then(statModLegacy());
        root.then(debugTree());
        return root;
    }

    private static LiteralArgumentBuilder<ServerCommandSource> statTree() {
        LiteralArgumentBuilder<ServerCommandSource> stat = literal("stat");
        stat.then(literal("check")
                .then(argument("player", EntityArgumentType.player())
                        .then(argument("stat", StringArgumentType.word())
                                .executes(ctx -> statCheck(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player"), StringArgumentType.getString(ctx, "stat"))))));
        stat.then(literal("remove")
                .then(argument("player", EntityArgumentType.player())
                        .then(argument("stat", StringArgumentType.word())
                                .then(argument("key", StringArgumentType.string())
                                        .executes(ctx -> statRemove(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player"), StringArgumentType.getString(ctx, "stat"), StringArgumentType.getString(ctx, "key")))))));
        stat.then(literal("clear")
                .then(argument("player", EntityArgumentType.player())
                        .then(argument("key", StringArgumentType.string())
                                .executes(ctx -> statClear(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player"), StringArgumentType.getString(ctx, "key"))))));
        var value = argument("value", StringArgumentType.string())
                .executes(ctx -> statAdd(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player"), StringArgumentType.getString(ctx, "stat"), StringArgumentType.getString(ctx, "value"), 0L, UUID.randomUUID().toString(), false))
                .then(argument("duration", LongArgumentType.longArg(0L))
                        .executes(ctx -> statAdd(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player"), StringArgumentType.getString(ctx, "stat"), StringArgumentType.getString(ctx, "value"), LongArgumentType.getLong(ctx, "duration"), UUID.randomUUID().toString(), false))
                        .then(argument("key", StringArgumentType.string())
                                .executes(ctx -> statAdd(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player"), StringArgumentType.getString(ctx, "stat"), StringArgumentType.getString(ctx, "value"), LongArgumentType.getLong(ctx, "duration"), StringArgumentType.getString(ctx, "key"), false))
                                .then(argument("unique", BoolArgumentType.bool())
                                        .executes(ctx -> statAdd(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player"), StringArgumentType.getString(ctx, "stat"), StringArgumentType.getString(ctx, "value"), LongArgumentType.getLong(ctx, "duration"), StringArgumentType.getString(ctx, "key"), BoolArgumentType.getBool(ctx, "unique"))))));
        stat.then(literal("add").then(argument("player", EntityArgumentType.player()).then(argument("stat", StringArgumentType.word()).then(value))));
        return stat;
    }

    private static LiteralArgumentBuilder<ServerCommandSource> cooldownTree() {
        LiteralArgumentBuilder<ServerCommandSource> cooldown = literal("cooldown");
        cooldown.then(literal("check").then(argument("key", StringArgumentType.string()).then(argument("player", EntityArgumentType.player())
                .executes(ctx -> cooldownCheck(ctx.getSource(), StringArgumentType.getString(ctx, "key"), EntityArgumentType.getPlayer(ctx, "player"))))));
        cooldown.then(literal("reset").then(argument("key", StringArgumentType.string()).then(argument("player", EntityArgumentType.player())
                .executes(ctx -> cooldownReset(ctx.getSource(), StringArgumentType.getString(ctx, "key"), EntityArgumentType.getPlayer(ctx, "player"))))));
        cooldown.then(literal("apply").then(argument("key", StringArgumentType.string()).then(argument("player", EntityArgumentType.player()).then(argument("duration", LongArgumentType.longArg())
                .executes(ctx -> cooldownApply(ctx.getSource(), StringArgumentType.getString(ctx, "key"), EntityArgumentType.getPlayer(ctx, "player"), LongArgumentType.getLong(ctx, "duration")))))));
        cooldown.then(literal("reduce")
                .then(literal("flat").then(argument("key", StringArgumentType.string()).then(argument("player", EntityArgumentType.player()).then(argument("duration", LongArgumentType.longArg())
                        .executes(ctx -> cooldownReduceFlat(ctx.getSource(), StringArgumentType.getString(ctx, "key"), EntityArgumentType.getPlayer(ctx, "player"), LongArgumentType.getLong(ctx, "duration")))))))
                .then(literal("initial").then(argument("key", StringArgumentType.string()).then(argument("player", EntityArgumentType.player()).then(argument("percent", DoubleArgumentType.doubleArg(0.0d, 1.0d))
                        .executes(ctx -> cooldownReducePercent(ctx.getSource(), StringArgumentType.getString(ctx, "key"), EntityArgumentType.getPlayer(ctx, "player"), DoubleArgumentType.getDouble(ctx, "percent"), true))))))
                .then(literal("remaining").then(argument("key", StringArgumentType.string()).then(argument("player", EntityArgumentType.player()).then(argument("percent", DoubleArgumentType.doubleArg(0.0d, 1.0d))
                        .executes(ctx -> cooldownReducePercent(ctx.getSource(), StringArgumentType.getString(ctx, "key"), EntityArgumentType.getPlayer(ctx, "player"), DoubleArgumentType.getDouble(ctx, "percent"), false)))))));
        return cooldown;
    }

    private static LiteralArgumentBuilder<ServerCommandSource> tempStatTree() {
        LiteralArgumentBuilder<ServerCommandSource> temp = literal("tempstat");
        temp.then(literal("add").then(argument("player", EntityArgumentType.player()).then(argument("stat", StringArgumentType.word()).then(argument("value", StringArgumentType.string()).then(argument("duration", LongArgumentType.longArg(1L))
                .executes(ctx -> statAdd(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player"), StringArgumentType.getString(ctx, "stat"), StringArgumentType.getString(ctx, "value"), LongArgumentType.getLong(ctx, "duration"), UUID.randomUUID().toString(), false)))))));
        temp.then(literal("remove").then(argument("player", EntityArgumentType.player()).then(argument("stat", StringArgumentType.word()).then(argument("key", StringArgumentType.string())
                .executes(ctx -> statRemove(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player"), StringArgumentType.getString(ctx, "stat"), StringArgumentType.getString(ctx, "key")))))));
        return temp;
    }

    private static LiteralArgumentBuilder<ServerCommandSource> statModLegacy() {
        return literal("statmod").then(argument("player", EntityArgumentType.player()).then(argument("stat", StringArgumentType.word()).then(argument("value", StringArgumentType.string())
                .executes(ctx -> statAdd(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player"), StringArgumentType.getString(ctx, "stat"), StringArgumentType.getString(ctx, "value"), 0L, UUID.randomUUID().toString(), false))
                .then(argument("duration", LongArgumentType.longArg(1L)).executes(ctx -> statAdd(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player"), StringArgumentType.getString(ctx, "stat"), StringArgumentType.getString(ctx, "value"), LongArgumentType.getLong(ctx, "duration"), UUID.randomUUID().toString(), false))))));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> debugTree() {
        LiteralArgumentBuilder<ServerCommandSource> debug = literal("debug");
        debug.then(literal("cast").then(argument("skill", StringArgumentType.word()).executes(ctx -> cast(ctx.getSource(), StringArgumentType.getString(ctx, "skill"), null))));
        debug.then(literal("stats").then(argument("player", EntityArgumentType.player()).executes(ctx -> dumpStats(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player")))));
        debug.then(literal("attributes").then(argument("player", EntityArgumentType.player()).executes(ctx -> dumpAttributes(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player")))));
        debug.then(literal("healthscale")
                .then(literal("set").then(argument("player", EntityArgumentType.player()).then(argument("scale", DoubleArgumentType.doubleArg(0.0001d)).executes(ctx -> healthScaleSet(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player"), DoubleArgumentType.getDouble(ctx, "scale"))))))
                .then(literal("reset").then(argument("player", EntityArgumentType.player()).executes(ctx -> healthScaleReset(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player"))))));
        debug.then(literal("info").executes(ctx -> { success(ctx.getSource(), "MythicLib Fabric | " + MythicLibFabricMod.definitionSummary() + " | " + FabricDamageBridge.summary()); return 1; }));
        debug.then(literal("versions").executes(ctx -> { success(ctx.getSource(), "MythicLib 1.7.1 behavior port | Minecraft 1.21.1 | Fabric"); return 1; }));
        debug.then(literal("parse").then(argument("text", StringArgumentType.greedyString()).executes(ctx -> {
            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
            String parsed = vn.svframe.mythiclibfabric.runtime.NativePlaceholderRegistry.parse(player.getUuid(), StringArgumentType.getString(ctx, "text"));
            success(ctx.getSource(), parsed);
            return 1;
        })));
        return debug;
    }

    private static LiteralArgumentBuilder<ServerCommandSource> healthScale(String name) {
        return literal(name).requires(source -> permitted(source, HEALTH_SCALE))
                .then(argument("scale", DoubleArgumentType.doubleArg(0.0001d)).executes(ctx -> healthScaleSet(ctx.getSource(), ctx.getSource().getPlayerOrThrow(), DoubleArgumentType.getDouble(ctx, "scale"))))
                .then(argument("player", EntityArgumentType.player()).then(argument("scale", DoubleArgumentType.doubleArg(0.0001d)).executes(ctx -> healthScaleSet(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player"), DoubleArgumentType.getDouble(ctx, "scale")))));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> mmoTempStat() {
        return literal("mmotempstat").requires(source -> permitted(source, TEMP_STAT))
                .then(argument("player", EntityArgumentType.player()).then(argument("stat", StringArgumentType.word()).then(argument("value", StringArgumentType.string()).then(argument("duration", LongArgumentType.longArg(1L))
                        .executes(ctx -> statAdd(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player"), StringArgumentType.getString(ctx, "stat"), StringArgumentType.getString(ctx, "value"), LongArgumentType.getLong(ctx, "duration"), UUID.randomUUID().toString(), false))))));
    }

    private static int reload(ServerCommandSource source) {
        boolean ok = MythicLibFabricMod.reloadAll();
        if (!ok) { source.sendError(Text.literal("MythicLib reload failed. Check server log.")); return 0; }
        success(source, "MythicLib reloaded | " + MythicLibFabricMod.definitionSummary() + " | " + FabricDamageBridge.summary());
        return 1;
    }

    private static int cast(ServerCommandSource source, String skill, ServerPlayerEntity target)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity caster = source.getPlayerOrThrow();
        ServerPlayerEntity actualTarget = target == null ? caster : target;
        boolean ok = MythicLibFabricMod.castSkill(skill, caster.getUuid(), actualTarget.getUuid(), Map.of());
        if (!ok) source.sendError(Text.literal("Skill did not cast."));
        return ok ? 1 : 0;
    }

    private static int damage(ServerCommandSource source, ServerPlayerEntity attacker, Entity target, double amount) {
        if (!(target instanceof LivingEntity living)) { source.sendError(Text.literal("Target is not a living entity.")); return 0; }
        boolean accepted = living.damage(living.getDamageSources().playerAttack(attacker), (float) Math.min(Float.MAX_VALUE, amount));
        if (!accepted) source.sendError(Text.literal("Damage was rejected by the target/runtime."));
        return accepted ? 1 : 0;
    }

    private static int statCheck(ServerCommandSource source, ServerPlayerEntity player, String stat) {
        NativeStatEngine.StatInstance instance = MythicLibStatMod.engine().instance(player.getUuid(), stat);
        success(source, player.getGameProfile().getName() + " " + normalize(stat) + " = " + instance.formatFinal());
        return 1;
    }

    private static int statAdd(ServerCommandSource source, ServerPlayerEntity player, String stat, String encoded, long duration, String key, boolean unique) {
        ParsedModifier parsed;
        try { parsed = parseModifier(encoded); }
        catch (IllegalArgumentException ex) { source.sendError(Text.literal("Invalid stat modifier value: " + encoded)); return 0; }
        String actualKey = key == null || key.isBlank() ? UUID.randomUUID().toString() : key;
        UUID id = unique ? UUID.nameUUIDFromBytes(actualKey.getBytes(StandardCharsets.UTF_8)) : UUID.randomUUID();
        long expires = duration > 0L ? saturatingAdd(MythicLibFabricMod.currentTick(), duration) : Long.MAX_VALUE;
        MythicLibStatMod.engine().register(player.getUuid(), stat, new NativeStatEngine.Modifier(id, actualKey, parsed.value(), parsed.type(), NativeStatEngine.EquipmentSlot.OTHER, NativeStatEngine.ModifierSource.OTHER, expires));
        success(source, "Added " + encoded + " " + normalize(stat) + (duration > 0 ? " for " + duration + " ticks" : "") + " to " + player.getGameProfile().getName());
        return 1;
    }

    private static int statRemove(ServerCommandSource source, ServerPlayerEntity player, String stat, String key) {
        int removed = MythicLibStatMod.engine().removeByKey(player.getUuid(), stat, key);
        success(source, "Removed " + removed + " modifier(s) with key '" + key + "' from " + normalize(stat));
        return 1;
    }

    private static int statClear(ServerCommandSource source, ServerPlayerEntity player, String key) {
        int removed = 0;
        for (NativeStatEngine.StatInstance instance : MythicLibStatMod.engine().instances(player.getUuid())) removed += instance.removeIf(modifier -> modifier.key().equals(key));
        success(source, "Removed " + removed + " modifier(s) with key '" + key + "' from " + player.getGameProfile().getName());
        return 1;
    }

    private static int cooldownCheck(ServerCommandSource source, String key, ServerPlayerEntity player) {
        NativePlayerData data = requireData(source, player); if (data == null) return 0;
        CooldownInfoRuntime info = data.cooldowns().info(key);
        if (info == null || info.hasEnded()) success(source, player.getGameProfile().getName() + " is not on cooldown for '" + key + "'.");
        else success(source, player.getGameProfile().getName() + " has " + info.remaining() / 1000.0d + "s remaining for '" + key + "'.");
        return 1;
    }

    private static int cooldownReset(ServerCommandSource source, String key, ServerPlayerEntity player) {
        NativePlayerData data = requireData(source, player); if (data == null) return 0;
        data.cooldowns().reset(key); success(source, "Reset cooldown '" + key + "' for " + player.getGameProfile().getName()); return 1;
    }

    private static int cooldownApply(ServerCommandSource source, String key, ServerPlayerEntity player, long durationTicks) {
        NativePlayerData data = requireData(source, player); if (data == null) return 0;
        data.cooldowns().apply(key, durationTicks / 20.0d); success(source, "Applied " + durationTicks + " ticks of cooldown '" + key + "' to " + player.getGameProfile().getName()); return 1;
    }

    private static int cooldownReduceFlat(ServerCommandSource source, String key, ServerPlayerEntity player, long duration) {
        NativePlayerData data = requireData(source, player); if (data == null) return 0;
        CooldownInfoRuntime info = data.cooldowns().info(key);
        if (info == null || info.hasEnded()) { success(source, player.getGameProfile().getName() + " is not on cooldown for '" + key + "'."); return 1; }
        info.reduceFlat(duration); success(source, "Cooldown '" + key + "' now has " + info.remaining() / 1000.0d + "s remaining."); return 1;
    }

    private static int cooldownReducePercent(ServerCommandSource source, String key, ServerPlayerEntity player, double percent, boolean initial) {
        NativePlayerData data = requireData(source, player); if (data == null) return 0;
        CooldownInfoRuntime info = data.cooldowns().info(key);
        if (info == null || info.hasEnded()) { success(source, player.getGameProfile().getName() + " is not on cooldown for '" + key + "'."); return 1; }
        if (initial) info.reduceInitialCooldown(percent); else info.reduceRemainingCooldown(percent);
        success(source, "Cooldown '" + key + "' now has " + info.remaining() / 1000.0d + "s remaining."); return 1;
    }

    private static NativePlayerData requireData(ServerCommandSource source, ServerPlayerEntity player) {
        NativePlayerData data = MythicLibPlayerDataMod.getOrNull(player.getUuid());
        if (data == null) source.sendError(Text.literal("MythicLib player data is not loaded for " + player.getGameProfile().getName()));
        return data;
    }

    private static int healthScaleSet(ServerCommandSource source, ServerPlayerEntity player, double scale) { MythicLibHealthScale.setScale(player, scale); success(source, "Health scale for " + player.getGameProfile().getName() + " set to " + scale); return 1; }
    private static int healthScaleReset(ServerCommandSource source, ServerPlayerEntity player) { MythicLibHealthScale.resetScale(player); success(source, "Health scale for " + player.getGameProfile().getName() + " reset to config/default behavior."); return 1; }

    private static int dumpStats(ServerCommandSource source, ServerPlayerEntity player) {
        if (MythicLibStatMod.engine().instances(player.getUuid()).isEmpty()) { success(source, player.getGameProfile().getName() + " has no materialized stat instances."); return 1; }
        for (NativeStatEngine.StatInstance instance : MythicLibStatMod.engine().instances(player.getUuid())) success(source, instance.stat() + "=" + instance.formatFinal() + " modifiers=" + instance.modifiers().size());
        return 1;
    }

    private static int dumpAttributes(ServerCommandSource source, ServerPlayerEntity player) {
        success(source, "MAX_HEALTH=" + player.getAttributeValue(EntityAttributes.GENERIC_MAX_HEALTH));
        success(source, "MOVEMENT_SPEED=" + player.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED));
        success(source, "ATTACK_DAMAGE=" + player.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE));
        success(source, "ATTACK_SPEED=" + player.getAttributeValue(EntityAttributes.GENERIC_ATTACK_SPEED));
        success(source, "ARMOR=" + player.getAttributeValue(EntityAttributes.GENERIC_ARMOR));
        success(source, "ARMOR_TOUGHNESS=" + player.getAttributeValue(EntityAttributes.GENERIC_ARMOR_TOUGHNESS));
        success(source, "KNOCKBACK_RESISTANCE=" + player.getAttributeValue(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE));
        return 1;
    }

    private static boolean permitted(ServerCommandSource source, String permission) { Entity entity = source.getEntity(); return entity instanceof ServerPlayerEntity player ? MythicLibPermissionBridge.has(player, permission) : source.hasPermissionLevel(2); }

    private static ParsedModifier parseModifier(String encoded) {
        if (encoded == null || encoded.isEmpty()) throw new IllegalArgumentException("empty modifier");
        String input = encoded.trim(); char suffix = input.charAt(input.length() - 1);
        NativeStatEngine.ModifierType type = switch (suffix) { case '%', 'c', 'm' -> NativeStatEngine.ModifierType.RELATIVE; case 'a', 's' -> NativeStatEngine.ModifierType.ADDITIVE_MULTIPLIER; default -> NativeStatEngine.ModifierType.FLAT; };
        String number = type == NativeStatEngine.ModifierType.FLAT ? input : input.substring(0, input.length() - 1);
        return new ParsedModifier(type, Double.parseDouble(number));
    }

    private static String normalize(String value) { return value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_'); }
    private static long saturatingAdd(long left, long right) { return right > 0 && left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right; }
    private static void success(ServerCommandSource source, String message) { source.sendFeedback(() -> Text.literal(message), false); }
    private record ParsedModifier(NativeStatEngine.ModifierType type, double value) {}
}
