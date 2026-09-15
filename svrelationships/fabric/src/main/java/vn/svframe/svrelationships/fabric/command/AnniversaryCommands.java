package vn.svframe.svrelationships.fabric.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.svrelationships.fabric.config.AnniversaryDefinitionService;
import vn.svframe.svrelationships.fabric.localization.MessageService;
import vn.svframe.svrelationships.fabric.runtime.RuntimeCoordinator;

import java.util.UUID;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class AnniversaryCommands {
    private AnniversaryCommands(){}
    public static void register(AnniversaryDefinitionService definitions,RuntimeCoordinator runtime,MessageService messages){
        CommandRegistrationCallback.EVENT.register((dispatcher,registryAccess,environment)->{
            PokemonReferenceResolver references=new PokemonReferenceResolver(runtime.relationships());
            dispatcher.register(literal("svrel").then(literal("anniversary")
                    .then(argument("pokemon",StringArgumentType.word()).suggests((context,builder)->{ServerPlayerEntity player=context.getSource().getPlayer();if(player!=null)references.suggestions(player).forEach(s->builder.suggest(s.value()));return builder.buildFuture();})
                            .then(argument("anniversary",StringArgumentType.word()).suggests((context,builder)->{definitions.snapshot().definitions().keySet().stream().sorted().forEach(builder::suggest);return builder.buildFuture();})
                                    .executes(context->{
                                        ServerPlayerEntity player=context.getSource().getPlayer();if(player==null){context.getSource().sendError(messages.text("command.player.required"));return 0;}
                                        UUID pokemon=references.resolve(player,StringArgumentType.getString(context,"pokemon")).orElse(null);if(pokemon==null){context.getSource().sendError(messages.text("command.pokemon.unknown"));return 0;}
                                        var result=runtime.anniversaries().claim(player,pokemon,StringArgumentType.getString(context,"anniversary"),System.currentTimeMillis());
                                        if(result.status()==vn.svframe.svrelationships.fabric.relationship.AnniversaryService.Status.SUCCESS){context.getSource().sendFeedback(()->messages.text(result.messageKey()),false);return 1;}
                                        String key=switch(result.status()){
                                            case UNKNOWN_DEFINITION -> "command.argument.invalid";
                                            case NOT_ELIGIBLE,NOT_DUE -> "command.reward.not_eligible";
                                            case ALREADY_CLAIMED -> "command.reward.already_claimed";
                                            case REWARD_FAILED -> "command.reward.delivery_failed";
                                            default -> "command.argument.invalid";
                                        };
                                        context.getSource().sendError(messages.text(key));return 0;
                                    }))))) ;
        });
    }
}
