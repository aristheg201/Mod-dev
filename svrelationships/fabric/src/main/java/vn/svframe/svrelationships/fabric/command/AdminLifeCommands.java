package vn.svframe.svrelationships.fabric.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import vn.svframe.svrelationships.fabric.config.ConfigService;
import vn.svframe.svrelationships.fabric.config.ScheduleDefinitionService;
import vn.svframe.svrelationships.fabric.household.HouseholdService;
import vn.svframe.svrelationships.fabric.localization.MessageService;
import vn.svframe.svrelationships.fabric.runtime.RuntimeCoordinator;
import vn.svframe.svrelationships.integration.ProviderHub;

import java.util.Map;
import java.util.UUID;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class AdminLifeCommands {
    private AdminLifeCommands() {}

    public static void register(ConfigService config, ScheduleDefinitionService schedules, MessageService messages,
                                HouseholdService households, ProviderHub providers, RuntimeCoordinator runtime) {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            PokemonReferenceResolver refs = new PokemonReferenceResolver(runtime.relationships());
            LiteralArgumentBuilder<ServerCommandSource> admin = literal("admin").requires(source -> canAdmin(source, config, providers));
            admin.then(householdRoot(config, messages, households));
            admin.then(scheduleRoot(schedules, messages, runtime, refs));
            dispatcher.register(literal("svrel").then(admin));
        });
    }

    private static LiteralArgumentBuilder<ServerCommandSource> householdRoot(ConfigService config, MessageService messages, HouseholdService households) {
        var root = literal("household");
        root.then(literal("set").then(playerArgument("player").executes(c -> {
            ServerPlayerEntity target = target(c, "player");
            if (target == null) return missingPlayer(c.getSource(), messages);
            var state = households.setAtPlayer(target);
            c.getSource().sendFeedback(() -> messages.text("command.admin.household.set", Map.of("player", target.getGameProfile().getName(), "profile", state.profileId())), false);
            return 1;
        })));
        root.then(literal("clear").then(playerArgument("player").executes(c -> {
            ServerPlayerEntity target = target(c, "player");
            if (target == null) return missingPlayer(c.getSource(), messages);
            boolean removed = households.clear(target.getUuid());
            c.getSource().sendFeedback(() -> messages.text(removed ? "command.admin.household.cleared" : "command.admin.household.none", Map.of("player", target.getGameProfile().getName())), false);
            return removed ? 1 : 0;
        })));
        root.then(literal("inspect").then(playerArgument("player").executes(c -> inspectHousehold(c, messages, households))));
        root.then(literal("profile").then(playerArgument("player").then(argument("profile", StringArgumentType.word())
                .suggests((c,b) -> { config.snapshot().householdProfiles().keySet().stream().sorted().forEach(b::suggest); return b.buildFuture(); })
                .executes(c -> {
                    ServerPlayerEntity target = target(c, "player");
                    if (target == null) return missingPlayer(c.getSource(), messages);
                    String profile = StringArgumentType.getString(c, "profile");
                    try {
                        if (!households.setProfile(target.getUuid(), profile)) { c.getSource().sendError(messages.text("command.admin.household.none", Map.of("player", target.getGameProfile().getName()))); return 0; }
                    } catch (IllegalArgumentException exception) { c.getSource().sendError(messages.text("command.argument.invalid")); return 0; }
                    c.getSource().sendFeedback(() -> messages.text("command.admin.household.profile", Map.of("player", target.getGameProfile().getName(), "profile", profile)), false);
                    return 1;
                }))));
        return root;
    }

    private static int inspectHousehold(CommandContext<ServerCommandSource> c, MessageService messages, HouseholdService households) {
        ServerPlayerEntity target = target(c, "player");
        if (target == null) return missingPlayer(c.getSource(), messages);
        var state = households.get(target.getUuid()).orElse(null);
        if (state == null) { c.getSource().sendError(messages.text("command.admin.household.none", Map.of("player", target.getGameProfile().getName()))); return 0; }
        var a = state.anchor();
        c.getSource().sendFeedback(() -> messages.text("command.admin.household.inspect", Map.of("player", target.getGameProfile().getName(), "profile", state.profileId(), "dimension", a.dimensionId(), "x", a.x(), "y", a.y(), "z", a.z(), "radius", households.activeRadius(state), "capacity", households.maxMaterializedPartners(state))), false);
        return 1;
    }

    private static LiteralArgumentBuilder<ServerCommandSource> scheduleRoot(ScheduleDefinitionService schedules, MessageService messages, RuntimeCoordinator runtime, PokemonReferenceResolver refs) {
        var root = literal("schedule");
        root.then(literal("set").then(playerArgument("player").then(pokemonArgument("pokemon", "player", refs).then(argument("profile", StringArgumentType.word())
                .suggests((c,b) -> { schedules.snapshot().profiles().keySet().stream().sorted().forEach(b::suggest); return b.buildFuture(); })
                .executes(c -> mutateSchedule(c, messages, runtime, refs, true))))));
        root.then(literal("clear").then(playerArgument("player").then(pokemonArgument("pokemon", "player", refs)
                .executes(c -> mutateSchedule(c, messages, runtime, refs, false)))));
        root.then(literal("inspect").then(playerArgument("player").then(pokemonArgument("pokemon", "player", refs)
                .executes(c -> inspectSchedule(c, messages, runtime, refs)))));
        return root;
    }

    private static int mutateSchedule(CommandContext<ServerCommandSource> c, MessageService messages, RuntimeCoordinator runtime, PokemonReferenceResolver refs, boolean set) {
        ServerPlayerEntity target = target(c, "player");
        if (target == null) return missingPlayer(c.getSource(), messages);
        UUID pokemon = refs.resolve(target, StringArgumentType.getString(c, "pokemon")).orElse(null);
        if (pokemon == null) { c.getSource().sendError(messages.text("command.pokemon.unknown")); return 0; }
        String profile = set ? StringArgumentType.getString(c, "profile") : null;
        try { runtime.schedules().setProfile(target.getUuid(), pokemon, profile); }
        catch (IllegalArgumentException exception) { c.getSource().sendError(messages.text("command.argument.invalid")); return 0; }
        c.getSource().sendFeedback(() -> messages.text(set ? "command.admin.schedule.set" : "command.admin.schedule.cleared", Map.of("player", target.getGameProfile().getName(), "pokemon", pokemon, "profile", profile == null ? "" : profile)), false);
        return 1;
    }

    private static int inspectSchedule(CommandContext<ServerCommandSource> c, MessageService messages, RuntimeCoordinator runtime, PokemonReferenceResolver refs) {
        ServerPlayerEntity target = target(c, "player");
        if (target == null) return missingPlayer(c.getSource(), messages);
        UUID pokemon = refs.resolve(target, StringArgumentType.getString(c, "pokemon")).orElse(null);
        if (pokemon == null) { c.getSource().sendError(messages.text("command.pokemon.unknown")); return 0; }
        var resolved = runtime.schedules().resolve(target.getUuid(), pokemon, target.getServerWorld().getTimeOfDay()).orElse(null);
        if (resolved == null) { c.getSource().sendError(messages.text("command.schedule.unresolved")); return 0; }
        c.getSource().sendFeedback(() -> messages.text("command.admin.schedule.inspect", Map.of("player", target.getGameProfile().getName(), "pokemon", pokemon, "profile", resolved.profileId(), "activity", resolved.activityId(), "materialize", resolved.materialize())), false);
        return 1;
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<ServerCommandSource, String> playerArgument(String name) {
        return argument(name, StringArgumentType.word()).suggests((c,b) -> { c.getSource().getServer().getPlayerManager().getPlayerList().forEach(p -> b.suggest(p.getGameProfile().getName())); return b.buildFuture(); });
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<ServerCommandSource, String> pokemonArgument(String name, String playerArg, PokemonReferenceResolver refs) {
        return argument(name, StringArgumentType.word()).suggests((c,b) -> {
            ServerPlayerEntity target = c.getSource().getServer().getPlayerManager().getPlayer(StringArgumentType.getString(c, playerArg));
            if (target != null) refs.suggestions(target).forEach(s -> b.suggest(s.value(), Text.literal(s.label())));
            return b.buildFuture();
        });
    }

    private static ServerPlayerEntity target(CommandContext<ServerCommandSource> c, String argumentName) { return c.getSource().getServer().getPlayerManager().getPlayer(StringArgumentType.getString(c, argumentName)); }
    private static int missingPlayer(ServerCommandSource source, MessageService messages) { source.sendError(messages.text("command.player.required")); return 0; }
    private static boolean canAdmin(ServerCommandSource source, ConfigService config, ProviderHub providers) {
        if (source.getEntity() == null) return true;
        ServerPlayerEntity player = source.getPlayer();
        if (player != null && providers.permissionProvider().isPresent()) return providers.permissionProvider().get().hasPermission(player.getUuid(), config.snapshot().adminPermission());
        return source.hasPermissionLevel(2);
    }
}
