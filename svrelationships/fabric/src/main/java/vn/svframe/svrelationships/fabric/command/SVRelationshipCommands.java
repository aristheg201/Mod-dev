package vn.svframe.svrelationships.fabric.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import vn.svframe.svrelationships.fabric.config.ConfigService;
import vn.svframe.svrelationships.fabric.config.GameplayDefinitionService;
import vn.svframe.svrelationships.fabric.household.HouseholdService;
import vn.svframe.svrelationships.fabric.localization.MessageService;
import vn.svframe.svrelationships.gameplay.PartnerCapacityResolver;
import vn.svframe.svrelationships.integration.IntegrationRegistry;
import vn.svframe.svrelationships.integration.ProviderHub;

import java.util.Map;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class SVRelationshipCommands {
    private SVRelationshipCommands() {
    }

    public static void register(
            ConfigService config,
            GameplayDefinitionService gameplay,
            MessageService messages,
            HouseholdService households,
            IntegrationRegistry integrations,
            ProviderHub providers
    ) {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(literal("svrel")
                    .then(literal("status").executes(context -> {
                        long active = integrations.snapshot().stream().filter(value -> value.state().name().equals("ACTIVE")).count();
                        int available = providers.economyIds().size()
                                + (providers.permissionProvider().isPresent() ? 1 : 0)
                                + (providers.pokemonProvider().isPresent() ? 1 : 0);
                        context.getSource().sendFeedback(() -> messages.text("command.status.summary", Map.of(
                                "active", active,
                                "available", available
                        )), false);
                        return 1;
                    }))
                    .then(literal("gameplay").executes(context -> {
                        var snapshot = gameplay.snapshot();
                        int capacity = resolveCapacity(context.getSource(), gameplay, providers);
                        context.getSource().sendFeedback(() -> messages.text("command.gameplay.summary", Map.of(
                                "tracks", snapshot.progressionTracks().size(),
                                "routes", snapshot.routes().size(),
                                "rewards", snapshot.rewardProfiles().size(),
                                "capacity", capacity
                        )), false);
                        return 1;
                    }))
                    .then(literal("integration")
                            .then(literal("inspect")
                                    .then(argument("id", StringArgumentType.word())
                                            .suggests((context, builder) -> {
                                                integrations.snapshot().forEach(value -> builder.suggest(value.id()));
                                                return builder.buildFuture();
                                            })
                                            .executes(context -> {
                                                String id = StringArgumentType.getString(context, "id");
                                                var descriptor = integrations.get(id);
                                                if (descriptor.isEmpty()) {
                                                    context.getSource().sendError(messages.text("command.integration.unknown", Map.of("id", id)));
                                                    return 0;
                                                }
                                                var value = descriptor.get();
                                                String state = config.snapshot().messages().getOrDefault(value.detailKey(), value.state().name());
                                                context.getSource().sendFeedback(() -> messages.text("command.integration.info", Map.of(
                                                        "id", value.id(),
                                                        "state", state
                                                )), false);
                                                return 1;
                                            }))))
                    .then(literal("config")
                            .requires(source -> canAdmin(source, config, providers))
                            .then(literal("reload").executes(context -> {
                                var gameplayResult = gameplay.reload();
                                if (!gameplayResult.success()) {
                                    context.getSource().sendError(messages.text("config.reload.failure", Map.of("detail", gameplayResult.detail())));
                                    return 0;
                                }
                                var result = config.reload();
                                if (result.success()) {
                                    context.getSource().sendFeedback(() -> messages.text("config.reload.success"), false);
                                    return 1;
                                }
                                context.getSource().sendError(messages.text("config.reload.failure", Map.of("detail", result.detail())));
                                return 0;
                            }))));

            dispatcher.register(literal("household")
                    .then(literal("set").executes(context -> {
                        var player = context.getSource().getPlayerOrThrow();
                        var state = households.setAtPlayer(player);
                        context.getSource().sendFeedback(() -> messages.text("household.set.success", Map.of(
                                "dimension", state.anchor().dimensionId(),
                                "x", state.anchor().x(),
                                "y", state.anchor().y(),
                                "z", state.anchor().z(),
                                "profile", state.profileId()
                        )), false);
                        return 1;
                    }))
                    .then(literal("info").executes(context -> {
                        var player = context.getSource().getPlayerOrThrow();
                        var state = households.get(player.getUuid());
                        if (state.isEmpty()) {
                            context.getSource().sendError(messages.text("household.info.none"));
                            return 0;
                        }
                        var value = state.get();
                        context.getSource().sendFeedback(() -> messages.text("household.info.value", Map.of(
                                "dimension", value.anchor().dimensionId(),
                                "x", value.anchor().x(),
                                "y", value.anchor().y(),
                                "z", value.anchor().z(),
                                "profile", value.profileId(),
                                "radius", households.activeRadius(value),
                                "deactivation_radius", households.deactivationRadius(value)
                        )), false);
                        return 1;
                    }))
                    .then(literal("clear").executes(context -> {
                        var player = context.getSource().getPlayerOrThrow();
                        households.clear(player.getUuid());
                        context.getSource().sendFeedback(() -> messages.text("household.clear.success"), false);
                        return 1;
                    })));
        });
    }

    private static int resolveCapacity(ServerCommandSource source, GameplayDefinitionService gameplay, ProviderHub providers) {
        var definition = gameplay.snapshot().partnerCapacity();
        var player = source.getPlayer();
        if (player == null || providers.permissionProvider().isEmpty()) {
            return definition.fallback();
        }
        return new PartnerCapacityResolver().resolve(definition,
                permission -> providers.permissionProvider().get().hasPermission(player.getUuid(), permission));
    }

    private static boolean canAdmin(ServerCommandSource source, ConfigService config, ProviderHub providers) {
        if (source.getEntity() == null) {
            return true;
        }
        var player = source.getPlayer();
        if (player != null && providers.permissionProvider().isPresent()) {
            return providers.permissionProvider().get().hasPermission(player.getUuid(), config.snapshot().adminPermission());
        }
        return source.hasPermissionLevel(2);
    }
}
