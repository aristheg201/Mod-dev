package vn.svframe.svrelationships.fabric.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.svrelationships.fabric.config.DialogueDefinitionService;
import vn.svframe.svrelationships.fabric.localization.MessageService;
import vn.svframe.svrelationships.fabric.runtime.RuntimeCoordinator;

import java.util.Map;
import java.util.UUID;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class LifeSimulationCommands {
    private LifeSimulationCommands(){}
    public static void register(DialogueDefinitionService dialogues,RuntimeCoordinator runtime,MessageService messages){CommandRegistrationCallback.EVENT.register((dispatcher,registryAccess,environment)->{PokemonReferenceResolver refs=new PokemonReferenceResolver(runtime.relationships());
        dispatcher.register(literal("svrel").then(literal("dialogue").then(pokemon("pokemon",refs).then(argument("dialogue",StringArgumentType.word()).suggests((c,b)->{dialogues.snapshot().definitions().keySet().stream().sorted().forEach(b::suggest);return b.buildFuture();}).executes(c->{ServerPlayerEntity p=c.getSource().getPlayer();if(p==null){c.getSource().sendError(messages.text("command.player.required"));return 0;}UUID id=refs.resolve(p,StringArgumentType.getString(c,"pokemon")).orElse(null);if(id==null){c.getSource().sendError(messages.text("command.pokemon.unknown"));return 0;}var r=runtime.dialogues().select(p.getUuid(),id,StringArgumentType.getString(c,"dialogue"),System.currentTimeMillis());if(r.status()==vn.svframe.svrelationships.fabric.relationship.DialogueService.Status.SUCCESS){c.getSource().sendFeedback(()->messages.text(r.messageKey(),Map.of("pokemon",id)),false);return 1;}c.getSource().sendError(messages.text(r.status()==vn.svframe.svrelationships.fabric.relationship.DialogueService.Status.COOLDOWN?"command.dialogue.cooldown":r.status()==vn.svframe.svrelationships.fabric.relationship.DialogueService.Status.UNKNOWN_DEFINITION?"command.argument.invalid":"command.dialogue.requirements"));return 0;}))))));
        dispatcher.register(literal("svrel").then(literal("schedule").then(pokemon("pokemon",refs).executes(c->{ServerPlayerEntity p=c.getSource().getPlayer();if(p==null){c.getSource().sendError(messages.text("command.player.required"));return 0;}UUID id=refs.resolve(p,StringArgumentType.getString(c,"pokemon")).orElse(null);if(id==null){c.getSource().sendError(messages.text("command.pokemon.unknown"));return 0;}var activity=runtime.schedules().resolve(p.getUuid(),id,p.getServerWorld().getTimeOfDay());if(activity.isEmpty()){c.getSource().sendError(messages.text("command.schedule.unresolved"));return 0;}c.getSource().sendFeedback(()->messages.text(activity.get().messageKey(),Map.of("pokemon",id,"activity",activity.get().activityId())),false);return 1;})))));});}
    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<net.minecraft.server.command.ServerCommandSource,String> pokemon(String name,PokemonReferenceResolver refs){return argument(name,StringArgumentType.word()).suggests((c,b)->{ServerPlayerEntity p=c.getSource().getPlayer();if(p!=null)refs.suggestions(p).forEach(s->b.suggest(s.value()));return b.buildFuture();});}
}
