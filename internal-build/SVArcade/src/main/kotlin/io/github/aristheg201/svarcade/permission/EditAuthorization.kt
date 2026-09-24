package io.github.aristheg201.svarcade.permission

import io.github.aristheg201.svarcade.content.HubContent
import net.minecraft.server.level.ServerPlayer

/** Server-side defense in depth for partial editors. */
object EditAuthorization {
    fun rejectReason(player: ServerPlayer, current: HubContent, candidate: HubContent): String? {
        if (SVArcadePermissions.has(player, SVArcadePermissions.EDITOR_ALL, 2)) return null

        if (current.defaultLocale != candidate.defaultLocale || current.defaultTheme != candidate.defaultTheme ||
            current.cobblemonWiki != candidate.cobblemonWiki) {
            if (!SVArcadePermissions.has(player, SVArcadePermissions.EDITOR_SETTINGS, 2)) {
                return "Bạn không có quyền sửa Hub settings/Cobblemon Wiki config."
            }
        }
        if (current.themes != candidate.themes || current.assets != candidate.assets) {
            if (!SVArcadePermissions.has(player, SVArcadePermissions.EDITOR_ASSETS, 2)) {
                return "Bạn không có quyền sửa theme/assets."
            }
        }

        val before = current.pages.associateBy { it.id }
        val after = candidate.pages.associateBy { it.id }
        val changedIds = (before.keys + after.keys).filter { before[it] != after[it] }.toSet()
        for (id in changedIds) {
            if (id !in before && !SVArcadePermissions.has(player, SVArcadePermissions.EDITOR_CREATE, 2)) {
                return "Bạn không có quyền tạo page '$id'."
            }
            if (!SVArcadePermissions.has(player, SVArcadePermissions.pageEditor(id), 2)) {
                return "Bạn không có quyền sửa page '$id'."
            }
        }
        if (current.pages.map { it.id } != candidate.pages.map { it.id } &&
            !SVArcadePermissions.has(player, SVArcadePermissions.EDITOR_SETTINGS, 2)) {
            return "Bạn không có quyền đổi thứ tự/cấu trúc page."
        }
        return null
    }
}
