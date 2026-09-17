package io.github.aristheg201.svhub.content

object V030ContentPatch {
    private const val MARKER = "svhub_v030"
    fun apply(content: HubContent): HubContent? {
        if (content.page(MARKER) != null) return null
        val home = content.page("home") ?: return null
        if (home.components.none { it.id == "home_pokedex" }) return null
        val theme = HubTheme(
            id = "sv_hub",
            primary = "#9EC5FF",
            secondary = "#84DCC6",
            accent = "#FFD166",
            background = "#08111F",
            panel = "#101B2B",
            text = "#F5F8FC",
            muted = "#94A4B8"
        )
        val cleanHome = home.copy(
            title = LocalizedText("SV Hub", mapOf("vi_vn" to "SV Hub")),
            subtitle = LocalizedText("Play. Collect. Explore.", mapOf("vi_vn" to "Chơi. Sưu tầm. Khám phá.")),
            components = home.components.filterNot { it.id.startsWith("v011_") || it.id.startsWith("v020_") } + listOf(
                HubComponent.ButtonGrid(
                    id = "home_native_launchers",
                    title = LocalizedText("Game Center", mapOf("vi_vn" to "Game Center")),
                    columns = 3,
                    buttons = listOf(
                        HubButton("Gacha", LocalizedText("Gacha", mapOf("vi_vn" to "Gacha")), HubAction("native_open", "gacha")),
                        HubButton("Arcade", LocalizedText("Arcade", mapOf("vi_vn" to "Arcade")), HubAction("native_open", "arcade")),
                        HubButton("Skins", LocalizedText("Skins", mapOf("vi_vn" to "Trang phục")), HubAction("native_open", "skins")),
                        HubButton("Companions", LocalizedText("Companions", mapOf("vi_vn" to "Linh Thú")), HubAction("native_open", "companions")),
                        HubButton("Wallet", LocalizedText("Wallet", mapOf("vi_vn" to "Ví")), HubAction("native_open", "wallet"))
                    )
                )
            )
        )
        val marker = HubPage(MARKER, LocalizedText("SVHub 0.3"), "system/v030", emptyList(), emptyList())
        return content.copy(defaultTheme = theme.id, themes = (content.themes.filterNot { it.id == theme.id } + theme), pages = content.pages.filterNot { it.id == "home" } + cleanHome + marker)
    }
}
