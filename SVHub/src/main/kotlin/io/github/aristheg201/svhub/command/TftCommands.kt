package io.github.aristheg201.svhub.command

import com.mojang.brigadier.arguments.StringArgumentType
import io.github.aristheg201.svhub.native.game.tft.TftSetRegistry
import io.github.aristheg201.svhub.permission.SVHubPermissions
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
                    else { ctx.source.sendSuccess({ Component.literal("Resolved $species aspects=${requested.sorted()}; provider resolution occurs client-side without fallback") }, false); 1 }
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
                    TftSetRegistry.reload().fold(onSuccess = { ctx.source.sendSuccess({ Component.literal("TFT ${it.id} atomically reloaded") }, true); 1 }, onFailure = { ctx.source.sendFailure(Component.literal("TFT reload rejected; previous snapshot retained: ${it.message}")); 0 })
                }))
            .then(Commands.literal("preview")
                .then(Commands.literal("unit").then(unitArgument().executes { ctx ->
                    val id=StringArgumentType.getString(ctx,"unit"); val u=TftSetRegistry.active().units.first{it.id==id}; ctx.source.sendSuccess({Component.literal("${u.id}: ${u.presentation.species} ${u.presentation.resolverAspects()} • ${u.role} • ${u.ability.name}")},false);1
                }))
                .then(Commands.literal("team").then(teamArgument().executes { ctx -> val id=StringArgumentType.getString(ctx,"team");val t=TftSetRegistry.active().teams.first{it.id==id};ctx.source.sendSuccess({Component.literal("${t.name}: ${t.members.joinToString{it.unit}} • ${t.arena}")},false);1 }))
                .then(Commands.literal("arena").then(Commands.argument("arena",StringArgumentType.word()).suggests{_,b->TftSetRegistry.active().rules.arenas.forEach(b::suggest);b.buildFuture()}.executes{ctx->ctx.source.sendSuccess({Component.literal("Arena ${StringArgumentType.getString(ctx,"arena")} is available for preview")},false);1})))
        dispatcher.register(Commands.literal("svhub").then(admin))
    }

    private fun speciesArgument() = Commands.argument("species", StringArgumentType.word()).suggests { _, b -> TftSetRegistry.active().units.map { it.presentation.species }.distinct().sorted().forEach(b::suggest); b.buildFuture() }
    private fun teamArgument() = Commands.argument("team", StringArgumentType.word()).suggests { _, b -> TftSetRegistry.active().teams.forEach { b.suggest(it.id) }; b.buildFuture() }
    private fun unitArgument() = Commands.argument("unit", StringArgumentType.word()).suggests { _, b -> TftSetRegistry.active().units.forEach { b.suggest(it.id) }; b.buildFuture() }
    private fun canonicalSpecies(value: String) = if (':' in value) value else "cobblemon:$value"
}
