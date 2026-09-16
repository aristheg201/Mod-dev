package io.github.aristheg201.svhub.server

import io.github.aristheg201.svhub.content.HubContent
import io.github.aristheg201.svhub.content.VisibilitySpec
import io.github.aristheg201.svhub.permission.SVHubPermissions
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.server.level.ServerPlayer

object SnapshotProjector {
    fun forPlayer(content: HubContent, player: ServerPlayer, editor: Boolean): HubContent {
        if (editor && SVHubPermissions.has(player, SVHubPermissions.EDITOR, 2)) return content
        val pages = content.pages.mapNotNull { page ->
            if (!visible(page.visibility, player, editor)) return@mapNotNull null
            page.copy(components = page.components.filter { visible(it.visibility, player, editor) })
        }
        return content.copy(pages = pages)
    }

    private fun visible(spec: VisibilitySpec, player: ServerPlayer, editor: Boolean): Boolean {
        if (spec.editorOnly && !editor) return false
        if (spec.permission != null && !SVHubPermissions.has(player, spec.permission, 0)) return false
        if (spec.serverMod != null && !FabricLoader.getInstance().isModLoaded(spec.serverMod)) return false
        // clientMod is intentionally left for client-side capability filtering.
        return true
    }
}
