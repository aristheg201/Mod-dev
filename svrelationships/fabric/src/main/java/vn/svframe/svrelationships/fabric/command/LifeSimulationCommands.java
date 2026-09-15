package vn.svframe.svrelationships.fabric.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.svrelationships.fabric.config.DialogueDefinitionService;
import vn.svframe.svrelationships.fabric.localization.MessageService;
import vn.svframe.svrelationships.fabric.relationship.DialogueService;
import vn.svframe.svrelationships.fabric.runtime.RuntimeCoordinator;

import java.util.Map;
import java.util.UUID;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class LifeSimulationCommands {
    private LifeSimulationCommands() {}

    public static void register(DialogueDefinitionService dialogues, RuntimeCoordinator runtime, MessageService messages) {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            PokemonReferenceResolver references = new PokemonReferenceResolver(runtime.relationships());
            dispatcher.register(literal("svrel").then(dialogueRoot(dialogues, runtime, messages, references)));
            dispatcher.register(literal("svrel").then(scheduleRoot(runtime, messages, references)));
        });
    }

    private static LiteralArgumentBuilder<ServerCommandSource> dialogueRoot(
            DialogueDefinitionService dialogues, RuntimeCoordinator runtime,
            MessageService messages, PokemonReferenceResolver references) {
        RequiredArgumentBuilder<ServerCommandSource, String> dialogue = argument("dialogue", StringArgumentType.word())
                .suggests((context, builder) -> {
                    dialogues.snapshot().definitions().keySet().stream().sorted().forEach(builder::suggest);
                    return builder.buildFuture();
                })
                .executes(context -> executeDialogue(
                        context.getSource(), references, runtime, messages,
                        StringArgumentType.getString(context, "pokemon"),
                        StringArgumentType.getString(context, "dialogue")
                ));
        return literal("dialogue").then(pokemonArgument("pokemon", references).then(dialogue));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> scheduleRoot(
            RuntimeCoordinator runtime, MessageService messages, PokemonReferenceResolver references) {
        return literal("schedule").then(pokemonArgument("pokemon", references)
                .executes(context -> executeSchedule(
                        context.getSource(), references, runtime, messages,
                        StringArgumentType.getString(context, "pokemon")
                )));
    }

    private static int executeDialogue(ServerCommandSource source, PokemonReferenceResolver references,
                                       RuntimeCoordinator runtime, MessageService messages,
                                       String pokemonReference, String dialogueId) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return missingPlayer(source, messages);
        UUID pokemonId = references.resolve(player, pokemonReference).orElse(null);
        if (pokemonId == null) return missingPokemon(source, messages);

        DialogueService.Result result = runtime.dialogues().select(player.getUuid(), pokemonId, dialogueId, System.currentTimeMillis());
        if (result.status() == DialogueService.Status.SUCCESS) {
            source.sendFeedback(() -> messages.text(result.messageKey(), Map.of("pokemon", pokemonId)), false);
            return 1;
        }
        String messageKey = switch (result.status()) {
            case UNKNOWN_DEFINITION -> "command.argument.invalid";
            case COOLDOWN -> "command.dialogue.cooldown";
            case REQUIREMENTS -> "command.dialogue.requirements";
            case SUCCESS -> "command.argument.invalid";
        };
        source.sendError(messages.text(messageKey));
        return 0;
    }

    private static int executeSchedule(ServerCommandSource source, PokemonReferenceResolver references,
                                       RuntimeCoordinator runtime, MessageService messages,
                                       String pokemonReference) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return missingPlayer(source, messages);
        UUID pokemonId = references.resolve(player, pokemonReference).orElse(null);
        if (pokemonId == null) return missingPokemon(source, messages);

        var activity = runtime.schedules().resolve(player.getUuid(), pokemonId, player.getServerWorld().getTimeOfDay());
        if (activity.isEmpty()) {
            source.sendError(messages.text("command.schedule.unresolved"));
            return 0;
        }
        var value = activity.get();
        source.sendFeedback(() -> messages.text(value.messageKey(), Map.of(
                "pokemon", pokemonId,
                "activity", value.activityId()
        )), false);
        return 1;
    }

    private static RequiredArgumentBuilder<ServerCommandSource, String> pokemonArgument(String name, PokemonReferenceResolver references) {
        return argument(name, StringArgumentType.word()).suggests((context, builder) -> {
            ServerPlayerEntity player = context.getSource().getPlayer();
            if (player != null) references.suggestions(player).forEach(value -> builder.suggest(value.value()));
            return builder.buildFuture();
        });
    }

    private static int missingPlayer(ServerCommandSource source, MessageService messages) {
        source.sendError(messages.text("command.player.required"));
        return 0;
    }

    private static int missingPokemon(ServerCommandSource source, MessageService messages) {
        source.sendError(messages.text("command.pokemon.unknown"));
        return 0;
    }
}
