package vn.svframe.svrelationships.fabric.command;

import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import vn.svframe.svrelationships.fabric.config.ConfigService;
import vn.svframe.svrelationships.fabric.config.GameplayDefinitionService;
import vn.svframe.svrelationships.fabric.household.HouseholdService;
import vn.svframe.svrelationships.fabric.localization.MessageService;
import vn.svframe.svrelationships.fabric.runtime.RuntimeCoordinator;
import vn.svframe.svrelationships.integration.IntegrationRegistry;
import vn.svframe.svrelationships.integration.ProviderHub;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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
            ProviderHub providers,
            RuntimeCoordinator runtime
    ) {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            PokemonReferenceResolver references = new PokemonReferenceResolver(runtime.relationships());

            dispatcher.register(literal("svrel")
                    .executes(context -> openMain(context.getSource(), runtime, messages))
                    .then(literal("open").executes(context -> openMain(context.getSource(), runtime, messages)))
                    .then(literal("status").executes(context -> status(context.getSource(), integrations, providers, messages)))
                    .then(literal("pokemon")
                            .then(pokemonArgument("pokemon", references)
                                    .executes(context -> openPokemon(
                                            context.getSource(), references, runtime, messages,
                                            StringArgumentType.getString(context, "pokemon")
                                    ))))
                    .then(literal("partners").executes(context -> partnerSummary(context.getSource(), runtime, messages)))
                    .then(literal("interact")
                            .then(pokemonArgument("pokemon", references)
                                    .then(argument("interaction", StringArgumentType.word())
                                            .suggests((context, builder) -> {
                                                gameplay.snapshot().interactions().keySet().stream().sorted().forEach(builder::suggest);
                                                return builder.buildFuture();
                                            })
                                            .executes(context -> interaction(
                                                    context.getSource(), references, runtime, messages,
                                                    StringArgumentType.getString(context, "pokemon"),
                                                    StringArgumentType.getString(context, "interaction")
                                            )))))
                    .then(literal("gift")
                            .then(pokemonArgument("pokemon", references)
                                    .then(argument("gift", StringArgumentType.word())
                                            .suggests((context, builder) -> {
                                                gameplay.snapshot().gifts().keySet().stream().sorted().forEach(builder::suggest);
                                                return builder.buildFuture();
                                            })
                                            .executes(context -> gift(
                                                    context.getSource(), references, runtime, messages,
                                                    StringArgumentType.getString(context, "pokemon"),
                                                    StringArgumentType.getString(context, "gift")
                                            )))))
                    .then(literal("romance")
                            .then(pokemonArgument("pokemon", references)
                                    .then(argument("milestone", StringArgumentType.word())
                                            .suggests((context, builder) -> {
                                                runtime.rules().snapshot().partnership().milestones().keySet().stream().sorted().forEach(builder::suggest);
                                                return builder.buildFuture();
                                            })
                                            .executes(context -> advancePartnership(
                                                    context.getSource(), references, runtime, messages,
                                                    StringArgumentType.getString(context, "pokemon"),
                                                    StringArgumentType.getString(context, "milestone")
                                            )))))
                    .then(literal("reward")
                            .then(pokemonArgument("pokemon", references)
                                    .then(argument("profile", StringArgumentType.word())
                                            .suggests((context, builder) -> {
                                                gameplay.snapshot().rewardProfiles().keySet().stream().sorted().forEach(builder::suggest);
                                                return builder.buildFuture();
                                            })
                                            .executes(context -> claimReward(
                                                    context.getSource(), references, runtime, messages,
                                                    StringArgumentType.getString(context, "pokemon"),
                                                    StringArgumentType.getString(context, "profile")
                                            )))))
                    .then(literal("daycare")
                            .then(literal("list").executes(context -> daycareList(context.getSource(), runtime, messages)))
                            .then(literal("start")
                                    .then(argument("definition", StringArgumentType.word())
                                            .suggests((context, builder) -> {
                                                gameplay.snapshot().daycareDefinitions().keySet().stream().sorted().forEach(builder::suggest);
                                                return builder.buildFuture();
                                            })
                                            .then(pokemonArgument("pokemon1", references)
                                                    .executes(context -> daycareStart(
                                                            context.getSource(), references, runtime, messages,
                                                            StringArgumentType.getString(context, "definition"),
                                                            List.of(StringArgumentType.getString(context, "pokemon1"))
                                                    ))
                                                    .then(pokemonArgument("pokemon2", references)
                                                            .executes(context -> daycareStart(
                                                                    context.getSource(), references, runtime, messages,
                                                                    StringArgumentType.getString(context, "definition"),
                                                                    List.of(
                                                                            StringArgumentType.getString(context, "pokemon1"),
                                                                            StringArgumentType.getString(context, "pokemon2")
                                                                    )
                                                            )))))))
                    .then(literal("lineage")
                            .then(pokemonArgument("pokemon", references)
                                    .executes(context -> lineage(
                                            context.getSource(), references, runtime, messages,
                                            StringArgumentType.getString(context, "pokemon")
                                    ))))
                    .then(literal("integration")
                            .then(literal("inspect")
                                    .then(argument("id", StringArgumentType.word())
                                            .suggests((context, builder) -> {
                                                integrations.snapshot().forEach(value -> builder.suggest(value.id()));
                                                return builder.buildFuture();
                                            })
                                            .executes(context -> integrationInspect(
                                                    context.getSource(), config, messages, integrations,
                                                    StringArgumentType.getString(context, "id")
                                            ))))
                    .then(literal("config")
                            .requires(source -> canAdmin(source, config, providers))
                            .then(literal("reload").executes(context -> reload(context.getSource(), runtime, messages))))
                    .then(literal("admin")
                            .requires(source -> canAdmin(source, config, providers))
                            .then(adminProgression(gameplay, runtime, messages, references))
                            .then(adminRoute(gameplay, runtime, messages, references))
                            .then(adminPartner(runtime, messages, references))
                            .then(adminPersonality(gameplay, runtime, messages, references))
                            .then(adminDaycare(runtime, messages))
                            .then(literal("capacity")
                                    .then(playerArgument("player")
                                            .executes(context -> adminCapacity(
                                                    context.getSource(), runtime, messages,
                                                    StringArgumentType.getString(context, "player")
                                            ))))
                            .then(literal("debug")
                                    .then(literal("trace")
                                            .then(playerArgument("player")
                                                    .then(argument("pokemon_uuid", StringArgumentType.word())
                                                            .suggests((context, builder) -> adminPokemonSuggestions(context.getSource(), StringArgumentType.getString(context, "player"), references, builder))
                                                            .executes(context -> debugTrace(
                                                                    context.getSource(), runtime, messages,
                                                                    StringArgumentType.getString(context, "player"),
                                                                    StringArgumentType.getString(context, "pokemon_uuid")
                                                            ))))))));

            dispatcher.register(literal("household")
                    .then(literal("set").executes(context -> householdSet(context.getSource(), households, messages)))
                    .then(literal("info").executes(context -> householdInfo(context.getSource(), households, messages)))
                    .then(literal("clear").executes(context -> householdClear(context.getSource(), households, messages))));
        });
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<ServerCommandSource, String> pokemonArgument(
            String name, PokemonReferenceResolver references
    ) {
        return argument(name, StringArgumentType.word()).suggests((context, builder) -> {
            ServerPlayerEntity player = context.getSource().getPlayer();
            if (player != null) {
                references.suggestions(player).forEach(suggestion -> builder.suggest(suggestion.value(), Text.literal(suggestion.label())));
            }
            return builder.buildFuture();
        });
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<ServerCommandSource, String> playerArgument(String name) {
        return argument(name, StringArgumentType.word()).suggests((context, builder) -> {
            context.getSource().getServer().getPlayerManager().getPlayerList().forEach(player -> builder.suggest(player.getGameProfile().getName()));
            return builder.buildFuture();
        });
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> adminProgression(
            GameplayDefinitionService gameplay, RuntimeCoordinator runtime, MessageService messages, PokemonReferenceResolver references
    ) {
        return literal("progression")
                .then(literal("get")
                        .then(playerArgument("player")
                                .then(adminPokemonArgument("pokemon", "player", references)
                                        .then(trackArgument(gameplay)
                                                .executes(context -> {
                                                    ServerPlayerEntity target = target(context.getSource(), StringArgumentType.getString(context, "player"));
                                                    if (target == null) return playerMissing(context.getSource(), messages);
                                                    UUID pokemon = resolve(target, references, StringArgumentType.getString(context, "pokemon"));
                                                    if (pokemon == null) return pokemonMissing(context.getSource(), messages);
                                                    String track = StringArgumentType.getString(context, "track");
                                                    long value = runtime.relationships().progression(target.getUuid(), pokemon, track);
                                                    context.getSource().sendFeedback(() -> messages.text("command.admin.progression.value", Map.of(
                                                            "player", target.getGameProfile().getName(), "pokemon", pokemon, "track", track, "value", value
                                                    )), false);
                                                    return 1;
                                                })))))
                .then(literal("set")
                        .then(playerArgument("player")
                                .then(adminPokemonArgument("pokemon", "player", references)
                                        .then(trackArgument(gameplay)
                                                .then(argument("value", LongArgumentType.longArg())
                                                        .executes(context -> mutateProgression(context.getSource(), runtime, messages, references,
                                                                StringArgumentType.getString(context, "player"),
                                                                StringArgumentType.getString(context, "pokemon"),
                                                                StringArgumentType.getString(context, "track"),
                                                                LongArgumentType.getLong(context, "value"), false)))))))
                .then(literal("add")
                        .then(playerArgument("player")
                                .then(adminPokemonArgument("pokemon", "player", references)
                                        .then(trackArgument(gameplay)
                                                .then(argument("value", LongArgumentType.longArg())
                                                        .executes(context -> mutateProgression(context.getSource(), runtime, messages, references,
                                                                StringArgumentType.getString(context, "player"),
                                                                StringArgumentType.getString(context, "pokemon"),
                                                                StringArgumentType.getString(context, "track"),
                                                                LongArgumentType.getLong(context, "value"), true)))))));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<ServerCommandSource, String> trackArgument(GameplayDefinitionService gameplay) {
        return argument("track", StringArgumentType.word()).suggests((context, builder) -> {
            gameplay.snapshot().progressionTracks().keySet().stream().sorted().forEach(builder::suggest);
            return builder.buildFuture();
        });
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<ServerCommandSource, String> adminPokemonArgument(
            String name, String playerArgument, PokemonReferenceResolver references
    ) {
        return argument(name, StringArgumentType.word()).suggests((context, builder) ->
                adminPokemonSuggestions(context.getSource(), StringArgumentType.getString(context, playerArgument), references, builder));
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> adminPokemonSuggestions(
            ServerCommandSource source, String playerName, PokemonReferenceResolver references,
            com.mojang.brigadier.suggestion.SuggestionsBuilder builder
    ) {
        ServerPlayerEntity player = target(source, playerName);
        if (player != null) references.suggestions(player).forEach(s -> builder.suggest(s.value(), Text.literal(s.label())));
        return builder.buildFuture();
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> adminRoute(
            GameplayDefinitionService gameplay, RuntimeCoordinator runtime, MessageService messages, PokemonReferenceResolver references
    ) {
        return literal("route")
                .then(literal("get")
                        .then(playerArgument("player")
                                .then(adminPokemonArgument("pokemon", "player", references)
                                        .then(argument("route", StringArgumentType.word())
                                                .suggests((context, builder) -> {
                                                    gameplay.snapshot().routes().keySet().stream().sorted().forEach(builder::suggest);
                                                    return builder.buildFuture();
                                                })
                                                .executes(context -> {
                                                    ServerPlayerEntity target = target(context.getSource(), StringArgumentType.getString(context, "player"));
                                                    if (target == null) return playerMissing(context.getSource(), messages);
                                                    UUID pokemon = resolve(target, references, StringArgumentType.getString(context, "pokemon"));
                                                    if (pokemon == null) return pokemonMissing(context.getSource(), messages);
                                                    String route = StringArgumentType.getString(context, "route");
                                                    String state = runtime.relationships().currentRoute(target.getUuid(), pokemon, route);
                                                    context.getSource().sendFeedback(() -> messages.text("command.admin.route.value", Map.of(
                                                            "player", target.getGameProfile().getName(), "pokemon", pokemon, "route", route, "state", state
                                                    )), false);
                                                    return 1;
                                                })))))
                .then(literal("force")
                        .then(playerArgument("player")
                                .then(adminPokemonArgument("pokemon", "player", references)
                                        .then(argument("route", StringArgumentType.word())
                                                .suggests((context, builder) -> {
                                                    gameplay.snapshot().routes().keySet().stream().sorted().forEach(builder::suggest);
                                                    return builder.buildFuture();
                                                })
                                                .then(argument("state", StringArgumentType.word())
                                                        .suggests((context, builder) -> {
                                                            String route = StringArgumentType.getString(context, "route");
                                                            var definition = gameplay.snapshot().routes().get(route);
                                                            if (definition != null) definition.states().keySet().stream().sorted().forEach(builder::suggest);
                                                            return builder.buildFuture();
                                                        })
                                                        .executes(context -> {
                                                            ServerPlayerEntity target = target(context.getSource(), StringArgumentType.getString(context, "player"));
                                                            if (target == null) return playerMissing(context.getSource(), messages);
                                                            UUID pokemon = resolve(target, references, StringArgumentType.getString(context, "pokemon"));
                                                            if (pokemon == null) return pokemonMissing(context.getSource(), messages);
                                                            String route = StringArgumentType.getString(context, "route");
                                                            String state = StringArgumentType.getString(context, "state");
                                                            runtime.relationships().forceRoute(target.getUuid(), pokemon, route, state);
                                                            context.getSource().sendFeedback(() -> messages.text("command.admin.route.updated", Map.of(
                                                                    "player", target.getGameProfile().getName(), "pokemon", pokemon, "route", route, "state", state
                                                            )), false);
                                                            return 1;
                                                        }))))));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> adminPartner(
            RuntimeCoordinator runtime, MessageService messages, PokemonReferenceResolver references
    ) {
        return literal("partner")
                .then(literal("add")
                        .then(playerArgument("player")
                                .then(adminPokemonArgument("pokemon", "player", references)
                                        .executes(context -> adminPartnerMutate(context.getSource(), runtime, messages, references,
                                                StringArgumentType.getString(context, "player"), StringArgumentType.getString(context, "pokemon"), true)))))
                .then(literal("remove")
                        .then(playerArgument("player")
                                .then(adminPokemonArgument("pokemon", "player", references)
                                        .executes(context -> adminPartnerMutate(context.getSource(), runtime, messages, references,
                                                StringArgumentType.getString(context, "player"), StringArgumentType.getString(context, "pokemon"), false)))))
                .then(literal("list")
                        .then(playerArgument("player").executes(context -> adminCapacity(
                                context.getSource(), runtime, messages, StringArgumentType.getString(context, "player")
                        ))));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> adminPersonality(
            GameplayDefinitionService gameplay, RuntimeCoordinator runtime, MessageService messages, PokemonReferenceResolver references
    ) {
        return literal("personality")
                .then(literal("set")
                        .then(playerArgument("player")
                                .then(adminPokemonArgument("pokemon", "player", references)
                                        .then(argument("personality", StringArgumentType.word())
                                                .suggests((context, builder) -> {
                                                    gameplay.snapshot().personalities().keySet().stream().sorted().forEach(builder::suggest);
                                                    return builder.buildFuture();
                                                })
                                                .executes(context -> {
                                                    ServerPlayerEntity target = target(context.getSource(), StringArgumentType.getString(context, "player"));
                                                    if (target == null) return playerMissing(context.getSource(), messages);
                                                    UUID pokemon = resolve(target, references, StringArgumentType.getString(context, "pokemon"));
                                                    if (pokemon == null) return pokemonMissing(context.getSource(), messages);
                                                    String personality = StringArgumentType.getString(context, "personality");
                                                    runtime.relationships().setPersonality(target.getUuid(), pokemon, personality);
                                                    context.getSource().sendFeedback(() -> messages.text("command.admin.personality.updated", Map.of(
                                                            "player", target.getGameProfile().getName(), "pokemon", pokemon, "personality", personality
                                                    )), false);
                                                    return 1;
                                                }))))) ;
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> adminDaycare(
            RuntimeCoordinator runtime, MessageService messages
    ) {
        return literal("daycare")
                .then(literal("complete")
                        .then(argument("session", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    runtime.daycare().ifPresent(service -> context.getSource().getServer().getPlayerManager().getPlayerList().forEach(player ->
                                            service.sessions(player.getUuid()).stream().filter(session -> "ACTIVE".equals(session.status())).forEach(session -> builder.suggest(session.sessionId().toString()))
                                    ));
                                    return builder.buildFuture();
                                })
                                .executes(context -> {
                                    var service = runtime.daycare();
                                    if (service.isEmpty()) return runtimeUnavailable(context.getSource(), messages);
                                    try {
                                        boolean result = service.get().completeNow(UUID.fromString(StringArgumentType.getString(context, "session")), System.currentTimeMillis());
                                        context.getSource().sendFeedback(() -> messages.text(result ? "command.admin.daycare.completed" : "command.admin.daycare.not_completed"), false);
                                        return result ? 1 : 0;
                                    } catch (IllegalArgumentException exception) {
                                        context.getSource().sendError(messages.text("command.argument.invalid"));
                                        return 0;
                                    }
                                })));
    }

    private static int openMain(ServerCommandSource source, RuntimeCoordinator runtime, MessageService messages) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return playerMissing(source, messages);
        if (runtime.guis().isEmpty()) return runtimeUnavailable(source, messages);
        return runtime.guis().get().openMain(player) ? 1 : 0;
    }

    private static int openPokemon(ServerCommandSource source, PokemonReferenceResolver references, RuntimeCoordinator runtime,
                                   MessageService messages, String reference) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return playerMissing(source, messages);
        UUID pokemon = resolve(player, references, reference);
        if (pokemon == null) return pokemonMissing(source, messages);
        if (runtime.guis().isEmpty()) return runtimeUnavailable(source, messages);
        return runtime.guis().get().openRelationship(player, pokemon) ? 1 : 0;
    }

    private static int partnerSummary(ServerCommandSource source, RuntimeCoordinator runtime, MessageService messages) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return playerMissing(source, messages);
        int current = runtime.relationships().partners(player.getUuid()).size();
        int capacity = runtime.relationships().capacity(player.getUuid());
        source.sendFeedback(() -> messages.text("command.partners.summary", Map.of(
                "current", current, "capacity", capacity, "remaining", Math.max(0, capacity - current)
        )), false);
        return 1;
    }

    private static int interaction(ServerCommandSource source, PokemonReferenceResolver references, RuntimeCoordinator runtime,
                                   MessageService messages, String reference, String interaction) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return playerMissing(source, messages);
        UUID pokemon = resolve(player, references, reference);
        if (pokemon == null) return pokemonMissing(source, messages);
        var result = runtime.interactions().interact(player.getUuid(), pokemon, interaction, System.currentTimeMillis());
        return sendEnumResult(source, messages, "command.interaction.", result.name());
    }

    private static int gift(ServerCommandSource source, PokemonReferenceResolver references, RuntimeCoordinator runtime,
                            MessageService messages, String reference, String gift) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return playerMissing(source, messages);
        UUID pokemon = resolve(player, references, reference);
        if (pokemon == null) return pokemonMissing(source, messages);
        var result = runtime.interactions().gift(player, pokemon, gift, System.currentTimeMillis());
        return sendEnumResult(source, messages, "command.gift.", result.name());
    }

    private static int advancePartnership(ServerCommandSource source, PokemonReferenceResolver references, RuntimeCoordinator runtime,
                                          MessageService messages, String reference, String milestone) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return playerMissing(source, messages);
        UUID pokemon = resolve(player, references, reference);
        if (pokemon == null) return pokemonMissing(source, messages);
        var result = runtime.partnerships().advance(player.getUuid(), pokemon, milestone);
        return sendEnumResult(source, messages, "command.partnership.", result.name());
    }

    private static int claimReward(ServerCommandSource source, PokemonReferenceResolver references, RuntimeCoordinator runtime,
                                   MessageService messages, String reference, String profile) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return playerMissing(source, messages);
        UUID pokemon = resolve(player, references, reference);
        if (pokemon == null) return pokemonMissing(source, messages);
        var result = runtime.rewards().claim(player, pokemon, profile, System.currentTimeMillis());
        return sendEnumResult(source, messages, "command.reward.", result.name());
    }

    private static int daycareStart(ServerCommandSource source, PokemonReferenceResolver references, RuntimeCoordinator runtime,
                                    MessageService messages, String definition, List<String> refs) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return playerMissing(source, messages);
        if (runtime.daycare().isEmpty()) return runtimeUnavailable(source, messages);
        List<UUID> pokemon = new ArrayList<>();
        for (String ref : refs) {
            UUID resolved = resolve(player, references, ref);
            if (resolved == null) return pokemonMissing(source, messages);
            pokemon.add(resolved);
        }
        var result = runtime.daycare().get().start(player.getUuid(), definition, pokemon, System.currentTimeMillis());
        return sendEnumResult(source, messages, "command.daycare.", result.name());
    }

    private static int daycareList(ServerCommandSource source, RuntimeCoordinator runtime, MessageService messages) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return playerMissing(source, messages);
        if (runtime.daycare().isEmpty()) return runtimeUnavailable(source, messages);
        var sessions = runtime.daycare().get().sessions(player.getUuid());
        long active = sessions.stream().filter(session -> "ACTIVE".equals(session.status())).count();
        source.sendFeedback(() -> messages.text("command.daycare.summary", Map.of("total", sessions.size(), "active", active)), false);
        return 1;
    }

    private static int lineage(ServerCommandSource source, PokemonReferenceResolver references, RuntimeCoordinator runtime,
                               MessageService messages, String reference) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return playerMissing(source, messages);
        UUID pokemon = resolve(player, references, reference);
        if (pokemon == null) return pokemonMissing(source, messages);
        var record = runtime.lineage().get(pokemon);
        if (record.isEmpty()) {
            source.sendError(messages.text("command.lineage.none"));
            return 0;
        }
        var value = record.get();
        source.sendFeedback(() -> messages.text("command.lineage.value", Map.of(
                "pokemon", pokemon, "generation", value.generation(), "parents", value.parentPokemonIds().size(), "profile", value.inheritanceProfile()
        )), false);
        return 1;
    }

    private static int status(ServerCommandSource source, IntegrationRegistry integrations, ProviderHub providers, MessageService messages) {
        long active = integrations.snapshot().stream().filter(value -> value.state().name().equals("ACTIVE")).count();
        int available = providers.economyIds().size() + (providers.permissionProvider().isPresent() ? 1 : 0) + (providers.pokemonProvider().isPresent() ? 1 : 0);
        source.sendFeedback(() -> messages.text("command.status.summary", Map.of("active", active, "available", available)), false);
        return 1;
    }

    private static int integrationInspect(ServerCommandSource source, ConfigService config, MessageService messages,
                                          IntegrationRegistry integrations, String id) {
        var descriptor = integrations.get(id);
        if (descriptor.isEmpty()) {
            source.sendError(messages.text("command.integration.unknown", Map.of("id", id)));
            return 0;
        }
        var value = descriptor.get();
        String state = config.snapshot().messages().getOrDefault(value.detailKey(), value.state().name());
        source.sendFeedback(() -> messages.text("command.integration.info", Map.of("id", value.id(), "state", state)), false);
        return 1;
    }

    private static int reload(ServerCommandSource source, RuntimeCoordinator runtime, MessageService messages) {
        var result = runtime.reloadAll();
        if (!result.success()) {
            source.sendError(messages.text("config.reload.failure", Map.of("detail", result.detail())));
            return 0;
        }
        source.sendFeedback(() -> messages.text("config.reload.success"), false);
        return 1;
    }

    private static int mutateProgression(ServerCommandSource source, RuntimeCoordinator runtime, MessageService messages,
                                         PokemonReferenceResolver references, String playerName, String pokemonRef,
                                         String track, long value, boolean add) {
        ServerPlayerEntity target = target(source, playerName);
        if (target == null) return playerMissing(source, messages);
        UUID pokemon = resolve(target, references, pokemonRef);
        if (pokemon == null) return pokemonMissing(source, messages);
        long result = add ? runtime.relationships().addProgression(target.getUuid(), pokemon, track, value)
                : runtime.relationships().setProgression(target.getUuid(), pokemon, track, value);
        source.sendFeedback(() -> messages.text("command.admin.progression.updated", Map.of(
                "player", target.getGameProfile().getName(), "pokemon", pokemon, "track", track, "value", result
        )), false);
        return 1;
    }

    private static int adminPartnerMutate(ServerCommandSource source, RuntimeCoordinator runtime, MessageService messages,
                                          PokemonReferenceResolver references, String playerName, String pokemonRef, boolean add) {
        ServerPlayerEntity target = target(source, playerName);
        if (target == null) return playerMissing(source, messages);
        UUID pokemon = resolve(target, references, pokemonRef);
        if (pokemon == null) return pokemonMissing(source, messages);
        boolean result = runtime.relationships().setPartner(target.getUuid(), pokemon, add, true);
        if (!result) return sendEnumResult(source, messages, "command.partner.", "FAILED");
        source.sendFeedback(() -> messages.text(add ? "command.partner.added" : "command.partner.removed", Map.of(
                "player", target.getGameProfile().getName(), "pokemon", pokemon
        )), false);
        return 1;
    }

    private static int adminCapacity(ServerCommandSource source, RuntimeCoordinator runtime, MessageService messages, String playerName) {
        ServerPlayerEntity target = target(source, playerName);
        if (target == null) return playerMissing(source, messages);
        int current = runtime.relationships().partners(target.getUuid()).size();
        int capacity = runtime.relationships().capacity(target.getUuid());
        source.sendFeedback(() -> messages.text("command.admin.capacity.value", Map.of(
                "player", target.getGameProfile().getName(), "current", current, "capacity", capacity,
                "remaining", Math.max(0, capacity - current), "over", Math.max(0, current - capacity)
        )), false);
        return 1;
    }

    private static int debugTrace(ServerCommandSource source, RuntimeCoordinator runtime, MessageService messages,
                                  String playerName, String pokemonReference) {
        ServerPlayerEntity target = target(source, playerName);
        if (target == null) return playerMissing(source, messages);
        PokemonReferenceResolver references = new PokemonReferenceResolver(runtime.relationships());
        UUID pokemon = resolve(target, references, pokemonReference);
        if (pokemon == null) return pokemonMissing(source, messages);
        var state = runtime.relationships().state(target.getUuid(), pokemon);
        source.sendFeedback(() -> messages.text("command.debug.trace", Map.of(
                "player", target.getGameProfile().getName(), "pokemon", pokemon,
                "bond", state.progression("bond"), "romance", state.progression("romance"),
                "partner", state.partner(), "capacity", runtime.relationships().capacity(target.getUuid()),
                "route", runtime.relationships().currentRoute(target.getUuid(), pokemon, runtime.rules().snapshot().partnership().routeId()),
                "personality", state.personalityId() == null ? "" : state.personalityId()
        )), false);
        return 1;
    }

    private static int householdSet(ServerCommandSource source, HouseholdService households, MessageService messages) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return playerMissing(source, messages);
        var state = households.setAtPlayer(player);
        source.sendFeedback(() -> messages.text("household.set.success", Map.of(
                "dimension", state.anchor().dimensionId(), "x", state.anchor().x(), "y", state.anchor().y(), "z", state.anchor().z(), "profile", state.profileId()
        )), false);
        return 1;
    }

    private static int householdInfo(ServerCommandSource source, HouseholdService households, MessageService messages) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return playerMissing(source, messages);
        var state = households.get(player.getUuid());
        if (state.isEmpty()) {
            source.sendError(messages.text("household.info.none"));
            return 0;
        }
        var value = state.get();
        source.sendFeedback(() -> messages.text("household.info.value", Map.of(
                "dimension", value.anchor().dimensionId(), "x", value.anchor().x(), "y", value.anchor().y(), "z", value.anchor().z(),
                "profile", value.profileId(), "radius", households.activeRadius(value), "deactivation_radius", households.deactivationRadius(value)
        )), false);
        return 1;
    }

    private static int householdClear(ServerCommandSource source, HouseholdService households, MessageService messages) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return playerMissing(source, messages);
        households.clear(player.getUuid());
        source.sendFeedback(() -> messages.text("household.clear.success"), false);
        return 1;
    }

    private static int sendEnumResult(ServerCommandSource source, MessageService messages, String prefix, String enumName) {
        String key = prefix + enumName.toLowerCase(java.util.Locale.ROOT);
        if ("SUCCESS".equals(enumName) || "DELIVERED".equals(enumName) || "STARTED".equals(enumName)) {
            source.sendFeedback(() -> messages.text(key), false);
            return 1;
        }
        source.sendError(messages.text(key));
        return 0;
    }

    private static ServerPlayerEntity target(ServerCommandSource source, String name) {
        return source.getServer().getPlayerManager().getPlayer(name);
    }

    private static UUID resolve(ServerPlayerEntity player, PokemonReferenceResolver references, String reference) {
        return references.resolve(player, reference).orElse(null);
    }

    private static int playerMissing(ServerCommandSource source, MessageService messages) {
        source.sendError(messages.text("command.player.required"));
        return 0;
    }

    private static int pokemonMissing(ServerCommandSource source, MessageService messages) {
        source.sendError(messages.text("command.pokemon.unknown"));
        return 0;
    }

    private static int runtimeUnavailable(ServerCommandSource source, MessageService messages) {
        source.sendError(messages.text("command.runtime.unavailable"));
        return 0;
    }

    private static boolean canAdmin(ServerCommandSource source, ConfigService config, ProviderHub providers) {
        if (source.getEntity() == null) return true;
        var player = source.getPlayer();
        if (player != null && providers.permissionProvider().isPresent()) {
            return providers.permissionProvider().get().hasPermission(player.getUuid(), config.snapshot().adminPermission());
        }
        return source.hasPermissionLevel(2);
    }
}
