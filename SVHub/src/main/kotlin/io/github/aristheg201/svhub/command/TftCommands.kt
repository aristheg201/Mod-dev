package io.github.aristheg201.svhub.command

import com.mojang.brigadier.arguments.StringArgumentType
import io.github.aristheg201.svhub.native.game.tft.TftSetRegistry
import io.github.aristheg201.svhub.native.game.tft.PokemonAnimationSemantic
import io.github.aristheg201.svhub.native.game.tft.PokemonPresentationDiagnostics
import io.github.aristheg201.svhub.permission.SVHubPermissions
import io.github.aristheg201.svhub.native.network.NativePlatformNetwork
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component

/** Live TFT content inspection. Suggestions are rebuilt from the immutable active snapshot. */
object TftCommands {
    fun register() = CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
        val admin = Commands.literal("tft").requires { SVHubPermissions.check(it, SVHubPermissions.ADMIN_DEBUG, 3) }
            .then(Commands.literal("config")
                .then(Commands.literal("scan-aspects").executes { ctx ->
                    ctx.source.sendSuccess({ Component.literal(TftSetRegistry.knownAspects().joinToString(", ")) }, false); 1
                })
                .then(Commands.literal("list-aspects").then(speciesArgument().executes { ctx ->
                    val species = canonicalSpecies(StringArgumentType.getString(ctx, "species"))
                    ctx.source.sendSuccess({ Component.literal("$species: ${TftSetRegistry.knownAspects(species).joinToString(", ")}") }, false); 1
                }))
                .then(Commands.literal("preview-aspect").then(speciesArgument().then(Commands.argument("aspects", StringArgumentType.greedyString()).suggests { ctx, b ->
                    TftSetRegistry.knownAspects(canonicalSpecies(StringArgumentType.getString(ctx, "species"))).forEach(b::suggest); b.buildFuture()
                }.executes { ctx ->
                    val species = canonicalSpecies(StringArgumentType.getString(ctx, "species")); val requested = StringArgumentType.getString(ctx, "aspects").split(' ').filter(String::isNotBlank).toSet()
                    val unknown = requested - TftSetRegistry.knownAspects(species)
                    if (unknown.isNotEmpty()) { ctx.source.sendFailure(Component.literal("Unresolved aspects for $species: $unknown")); 0 }
                    else {
                        val identity=io.github.aristheg201.svhub.native.game.tft.PokemonPresentationIdentity(species=species,aspects=requested)
                        val report=PokemonPresentationDiagnostics.resolve(identity)
                        if(report.rejectionReason!=null){ctx.source.sendFailure(Component.literal(report.describe()));0}
                        else{ctx.source.sendSuccess({Component.literal(report.describe())},false);1}
                    }
                })))
                .then(Commands.literal("validate").then(Commands.literal("team").then(teamArgument().executes { ctx ->
                    val id = StringArgumentType.getString(ctx, "team"); val team = TftSetRegistry.active().teams.firstOrNull { it.id == id }
                    if (team == null) { ctx.source.sendFailure(Component.literal("Unknown TFT team $id")); 0 }
                    else { ctx.source.sendSuccess({ Component.literal("Valid ${team.id}: board=${team.members.size}, bench=${team.bench.size}, arena=${team.arena}") }, false); 1 }
                })))
                .then(Commands.literal("generate").then(Commands.literal("team").then(Commands.argument("id", StringArgumentType.word()).executes { ctx ->
                    val id = StringArgumentType.getString(ctx, "id"); ctx.source.sendSuccess({ Component.literal("{\"id\":\"svhub:$id\",\"name\":\"$id\",\"members\":[],\"bench\":[],\"aiProfile\":\"normal\"}") }, false); 1
                })))
                .then(Commands.literal("reload").executes { ctx ->
                    val source=ctx.source;TftSetRegistry.reloadAsync().whenComplete{set,error->source.server.execute{if(error==null)source.sendSuccess({Component.literal("TFT ${set.id} atomically reloaded")},true)else source.sendFailure(Component.literal("TFT reload rejected; previous snapshot retained: ${error.message}"))}};1
                }))
            .then(Commands.literal("preview")
                .then(Commands.literal("unit").then(unitArgument().executes { ctx ->
                    val id=StringArgumentType.getString(ctx,"unit"); val u=TftSetRegistry.active().units.first{it.id==id}; val report=PokemonPresentationDiagnostics.resolve(u.presentation);ctx.source.sendSuccess({Component.literal("${u.id}: ${report.describe()} • role=${u.role} • ability=${u.ability.name}")},false);1
                }))
                .then(Commands.literal("team").then(teamArgument().executes { ctx -> val id=StringArgumentType.getString(ctx,"team");val t=TftSetRegistry.active().teams.first{it.id==id};ctx.source.sendSuccess({Component.literal("${t.name}: ${t.members.joinToString{it.unit}} • ${t.arena}")},false);1 }))
                .then(Commands.literal("arena").then(Commands.argument("arena",StringArgumentType.word()).suggests{_,b->TftSetRegistry.active().rules.arenas.forEach(b::suggest);b.buildFuture()}.executes{ctx->ctx.source.sendSuccess({Component.literal("Arena ${StringArgumentType.getString(ctx,"arena")} is available for preview")},false);1})))
                .then(Commands.literal("animation").then(unitArgument().then(Commands.argument("semantic",StringArgumentType.word()).suggests{_,b->PokemonAnimationSemantic.entries.forEach{b.suggest(it.name)};b.buildFuture()}.executes{ctx->
                    val id=StringArgumentType.getString(ctx,"unit");val semantic=runCatching{PokemonAnimationSemantic.valueOf(StringArgumentType.getString(ctx,"semantic").uppercase())}.getOrNull()
                    if(semantic==null){ctx.source.sendFailure(Component.literal("Unknown semantic"));0}else{
                        val player=ctx.source.player
                        if(player==null){ctx.source.sendFailure(Component.literal("Client poser preview requires a player source"));0}else{
                            val unit=TftSetRegistry.active().units.first{it.id==id}
                            NativePlatformNetwork.requestTftPreview(player,unit.id,unit.presentation.species,unit.presentation.resolverAspects(),semantic.name)
                            ctx.source.sendSuccess({Component.literal("Requested live client poser preview for ${unit.id} ${semantic.name}")},false)
                            1
                        }
                    }
                    }
                )))
        dispatcher.register(Commands.literal("svhub").then(admin))
    }

    private fun speciesArgument() = Commands.argument("species", StringArgumentType.word()).suggests { _, b -> TftSetRegistry.active().units.map { it.presentation.species }.distinct().sorted().forEach(b::suggest); b.buildFuture() }
    private fun teamArgument() = Commands.argument("team", StringArgumentType.word()).suggests { _, b -> TftSetRegistry.active().teams.forEach { b.suggest(it.id) }; b.buildFuture() }
    private fun unitArgument() = Commands.argument("unit", StringArgumentType.word()).suggests { _, b -> TftSetRegistry.active().units.forEach { b.suggest(it.id) }; b.buildFuture() }
    private fun canonicalSpecies(value: String) = if (':' in value) value else "cobblemon:$value"
}
