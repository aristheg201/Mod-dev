package io.github.aristheg201.svhub.action

import io.github.aristheg201.svhub.SVHubRuntime
import io.github.aristheg201.svhub.api.SVHubApi
import io.github.aristheg201.svhub.permission.SVHubPermissions
import io.github.aristheg201.svhub.network.SVHubNetwork
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer

object ServerActionDispatcher {
    private val limiter = ActionRateLimiter()

    fun clear(player: ServerPlayer) = limiter.clear(player.uuid)

    fun execute(player: ServerPlayer, actionId: String) {
        val match = SVHubRuntime.store.snapshot().content.pages.asSequence()
            .flatMap { page -> page.components.asSequence().map { component -> Triple(page, component, component.action) } }
            .firstOrNull { (_, _, action) -> action?.id == actionId }
            ?: return
        val (page, component, nullableAction) = match
        val action = nullableAction ?: return

        // Never trust an action id from the client. Re-check the same server-owned
        // visibility gates used when projecting content to the player.
        if (page.visibility.editorOnly || component.visibility.editorOnly) return
        for (visibility in listOf(page.visibility, component.visibility)) {
            if (visibility.permission != null && !SVHubPermissions.has(player, visibility.permission, 0)) return
            if (visibility.serverMod != null && !FabricLoader.getInstance().isModLoaded(visibility.serverMod)) return
            if (visibility.clientMod != null && !SVHubNetwork.clientHasMod(player, visibility.clientMod)) return
        }
        if (action.permission != null && !SVHubPermissions.has(player, action.permission, 0)) {
            player.sendSystemMessage(Component.literal("Bạn không có quyền dùng thao tác này."))
            return
        }
        if (!limiter.allow(player.uuid, action.id, action.cooldownMs.coerceAtLeast(100L))) return

        when (action.type) {
            "run_command" -> {
                val command = action.value.trim().removePrefix("/")
                if (command.isBlank() || command.length > 256 || command.any { it == '\n' || it == '\r' || it == ';' }) return
                player.server.commands.performPrefixedCommand(player.createCommandSourceStack(), command)
            }
            "open_page", "copy_text", "open_url", "close", "back" -> Unit // client presentation actions
            else -> SVHubApi.dispatchCustomAction(player, action)
        }
    }
}
