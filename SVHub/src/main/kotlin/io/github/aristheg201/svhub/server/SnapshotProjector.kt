package io.github.aristheg201.svhub.server

import io.github.aristheg201.svhub.content.HubContent
import io.github.aristheg201.svhub.content.VisibilitySpec
import io.github.aristheg201.svhub.permission.SVHubPermissions
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.server.level.ServerPlayer

object SnapshotProjector {
    fun forPlayer(content: HubContent, player: ServerPlayer, editor: Boolean, clientMods: Set<String> = emptySet()): HubContent {
        // Full editors need the complete base snapshot because publish is an optimistic
        // whole-document transaction. Server-side EditAuthorization still enforces scope.
        if (editor && SVHubPermissions.has(player, SVHubPermissions.EDITOR_ALL, 2)) return content

        val pages = content.pages.mapNotNull { page ->
            if (!visible(page.visibility, player, editor, clientMods)) return@mapNotNull null
            page.copy(components = page.components.filter { visible(it.visibility, player, editor, clientMods) })
        }
        return SnapshotPruner.prune(content, pages)
    }

    private fun visible(spec: VisibilitySpec, player: ServerPlayer, editor: Boolean, clientMods: Set<String>): Boolean {
        if (spec.editorOnly && !editor) return false
        if (spec.permission != null && !SVHubPermissions.has(player, spec.permission, 0)) return false
        if (spec.serverMod != null && !FabricLoader.getInstance().isModLoaded(spec.serverMod)) return false
        if (spec.clientMod != null && spec.clientMod !in clientMods) return false
        return true
    }
}
