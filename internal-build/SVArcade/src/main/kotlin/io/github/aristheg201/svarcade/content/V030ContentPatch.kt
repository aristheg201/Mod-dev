package io.github.aristheg201.svarcade.content

import com.google.gson.JsonObject

/** Responsive visual cleanup for the 0.3+ hub shell. */
object V030ContentPatch {
    private const val MARKER = "svarcade_v030"

    fun apply(content: HubContent): HubContent? {
        if (content.page(MARKER) != null) return null
        val home = content.page("home") ?: return null
        if (home.components.none { it.id == "home_pokedex" }) return null

        val theme = HubTheme(
            id = "sv_hub",
            backgroundPreset = "clean",
            panelStyle = "clean",
            buttonStyle = "clean",
            titleAnimation = "none",
            palette = ThemePalette(
                background = 0xFF08111F.toInt(),
                panel = 0xFF101B2B.toInt(),
                panelAlt = 0xFF162438.toInt(),
                text = 0xFFF5F8FC.toInt(),
                mutedText = 0xFF94A4B8.toInt(),
                accent = 0xFF84DCC6.toInt(),
                accent2 = 0xFFFFD166.toInt(),
                danger = 0xFFE36C5C.toInt()
            ),
            motionStrength = 0.35f
        )

        val cleaned = home.components.filterNot {
            it.id.startsWith("v011_") || it.id.startsWith("v020_") || it.id.startsWith("v030_")
        }
        val launchers = listOf(
            nativeButton("v030_gacha", "Gacha", "gacha"),
            nativeButton("v030_arcade", "Arcade", "arcade"),
            nativeButton("v030_skins", "Trang phục", "skins"),
            nativeButton("v030_companions", "Linh Thú", "companions"),
            nativeButton("v030_wallet", "Ví", "wallet")
        )
        val patchedHome = home.copy(
            title = text("SV Hub", "SV Hub"),
            subtitle = text("Play. Collect. Explore.", "Chơi. Sưu tầm. Khám phá."),
            theme = theme.id,
            components = launchers + cleaned
        )
        val marker = HubPage(
            id = MARKER,
            route = "system/v030",
            category = "system",
            title = text("SVArcade 0.3+", "SVArcade 0.3+"),
            theme = theme.id,
            showInNavigation = false,
            components = emptyList()
        )
        val pages = content.pages.filterNot { it.id == home.id || it.id == MARKER } + patchedHome + marker
        return content.copy(
            defaultTheme = theme.id,
            themes = content.themes + (theme.id to theme),
            pages = pages
        )
    }

    private fun nativeButton(id: String, label: String, module: String): HubComponent = HubComponent(
        id = id,
        type = "button",
        props = JsonObject().apply {
            addProperty("label", label)
            addProperty("description", "")
        },
        action = HubActionSpec("$id:open", "native_open", module, cooldownMs = 250L)
    )

    private fun text(en: String, vi: String) = LocalizedText(mapOf("en_us" to en, "vi_vn" to vi))
}
