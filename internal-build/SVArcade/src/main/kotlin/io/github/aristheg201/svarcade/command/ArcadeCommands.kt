package io.github.aristheg201.svarcade.command
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.commands.Commands
import io.github.aristheg201.svarcade.native.NativePlatform
import io.github.aristheg201.svarcade.permission.SVArcadePermissions
object ArcadeCommands {
    fun register() { CommandRegistrationCallback.EVENT.register { dispatcher,_,_ ->
        listOf("arcade","svarcade").forEach { name -> dispatcher.register(Commands.literal(name).requires { SVArcadePermissions.check(it,SVArcadePermissions.OPEN,0) }.executes { ctx -> NativePlatform.open(ctx.source.playerOrException,"arcade");1 }) }
    } }
}
