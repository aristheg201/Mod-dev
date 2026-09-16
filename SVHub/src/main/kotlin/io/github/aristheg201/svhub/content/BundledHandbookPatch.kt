package io.github.aristheg201.svhub.content

import com.google.gson.JsonObject

/**
 * One-time corrections to SVHub's bundled player handbook.
 * This is deliberately restricted to revision 0/1 seed content so a later
 * administrator edit is never silently re-populated.
 */
object BundledHandbookPatch {
    fun apply(content: HubContent): HubContent? {
        if (content.revision !in 0L..1L) return null
        if (content.defaultTheme != "clean_dark") return null
        val commands = content.page("commands") ?: return null
        val quick = content.page("commands/essential") ?: return null
        val shop = content.page("shop/pokemon") ?: return null
        if (commands.components.none { it.id == "cmd_lenh" }) return null
        if (shop.components.none { it.id == "shop_other_h" }) return null

        var changed = false
        fun withComponents(page: HubPage, additions: List<HubComponent>): HubPage {
            val existing = page.components.mapTo(hashSetOf()) { it.id }
            val missing = additions.filter { it.id !in existing }
            if (missing.isEmpty()) return page
            changed = true
            return page.copy(components = page.components + missing)
        }

        val patchedQuick = withComponents(
            quick,
            listOf(
                run("quick_hub", "/hub", "Mở SV Hub."),
                run("quick_wiki", "/wiki", "Mở SV Hub/Wiki."),
                run("quick_wt", "/wt", "Mở Wonder Trade."),
                run("quick_sts", "/sts", "Mở STS để bán Pokémon không còn dùng.")
            )
        )
        val patchedCommands = withComponents(
            commands,
            listOf(
                heading("cmd_pokemon_services_h", "Dịch vụ Pokémon"),
                run("cmd_wt", "/wt", "Mở Wonder Trade. Hệ thống cũng nhận diện lệnh /wondertrade."),
                run("cmd_sts", "/sts", "Mở STS để bán Pokémon không còn dùng."),
                run("cmd_hub", "/hub", "Mở SV Hub."),
                run("cmd_wiki", "/wiki", "Mở SV Hub/Wiki.")
            )
        )
        val patchedShop = withComponents(
            shop,
            listOf(
                run("shop_wt", "/wt", "Mở Wonder Trade. Bạn đưa một Pokémon và nhận lại một Pokémon ngẫu nhiên."),
                run("shop_sts", "/sts", "Mở STS. Kiểm tra kỹ Pokémon trước khi xác nhận bán.")
            )
        )

        if (!changed) return null
        val pages = content.pages.map { page ->
            when (page.id) {
                quick.id -> patchedQuick
                commands.id -> patchedCommands
                shop.id -> patchedShop
                else -> page
            }
        }
        return content.copy(revision = content.revision + 1, pages = pages)
    }

    private fun heading(id: String, text: String) = HubComponent(
        id = id,
        type = "heading",
        props = JsonObject().apply { addProperty("text", text) }
    )

    private fun run(id: String, command: String, description: String) = HubComponent(
        id = id,
        type = "command_card",
        props = JsonObject().apply {
            addProperty("command", command)
            addProperty("description", description)
        },
        action = HubActionSpec("$id:run", "run_command", command, cooldownMs = 750L)
    )
}
