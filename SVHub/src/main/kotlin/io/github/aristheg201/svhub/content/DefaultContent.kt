package io.github.aristheg201.svhub.content

import com.google.gson.JsonObject

object DefaultContent {
    fun create(): HubContent {
        val theme = HubTheme("pixel_classic", backgroundPreset = "pixel_sky")
        fun p(id: String, route: String, title: String, category: String, vararg components: HubComponent) =
            HubPage(id, route, category, LocalizedText.of(title), theme = theme.id, components = components.toList())
        fun heading(id: String, text: String) = HubComponent(id, "heading", JsonObject().apply { addProperty("text", text) })
        fun text(id: String, text: String) = HubComponent(id, "text", JsonObject().apply { addProperty("text", text) })
        fun grid(id: String, provider: String) = HubComponent(id, "grid", JsonObject().apply { addProperty("provider", provider) })
        return HubContent(
            revision = 1,
            themes = mapOf(theme.id to theme),
            pages = listOf(
                p("home", "home", "SV HUB", "home", heading("welcome", "CHÀO MỪNG ĐẾN SV HUB"), text("intro", "Wiki, Pokémon, Fakemon, lệnh và hướng dẫn của server.")),
                p("how_to_play", "guide/how-to-play", "Cách chơi", "guide", heading("title", "CÁCH CHƠI"), text("body", "Nội dung được admin chỉnh trực tiếp bằng SVHub Editor.")),
                p("pokemon", "pokemon", "Pokémon", "wiki", heading("title", "POKÉMON INFORMATION"), grid("pokemon_grid", "cobblemon:pokemon")),
                p("fakemon", "fakemon", "Fakemon", "wiki", heading("title", "FAKEMON INFORMATION"), grid("fakemon_grid", "cobblemon:fakemon")),
                p("commands", "commands", "Lệnh", "commands", heading("title", "COMMANDS"), grid("commands_grid", "svhub:commands")),
                p("essential_commands", "commands/essential", "Lệnh cần biết", "commands", heading("title", "ESSENTIAL COMMANDS")),
                p("updates", "updates", "Cập nhật mới", "updates", heading("title", "CẬP NHẬT MỚI"))
            )
        )
    }
}
