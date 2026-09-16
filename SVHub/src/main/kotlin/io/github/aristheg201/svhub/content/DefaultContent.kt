package io.github.aristheg201.svhub.content

import com.google.gson.JsonObject

object DefaultContent {
    fun create(): HubContent {
        val themes = linkedMapOf(
            "pixel_classic" to HubTheme(
                id = "pixel_classic", backgroundAsset = "home_pixel", backgroundPreset = "pixel_sky", titleAnimation = "pixel_pop",
                palette = ThemePalette(
                    background = 0xFF0D1320.toInt(), panel = 0xE61A2436.toInt(), panelAlt = 0xE623324B.toInt(),
                    text = 0xFFF8FAFF.toInt(), mutedText = 0xFFAFC3DA.toInt(), accent = 0xFFFFD65C.toInt(),
                    accent2 = 0xFF67D9FF.toInt(), danger = 0xFFFF6B7A.toInt()
                )
            ),
            "pixel_wiki" to HubTheme("pixel_wiki", "wiki_pixel", "pixel_forest", titleAnimation = "shimmer"),
            "pixel_commands" to HubTheme("pixel_commands", "commands_pixel", "pixel_grid", titleAnimation = "typewriter"),
            "pixel_updates" to HubTheme("pixel_updates", "updates_pixel", "pixel_neon", titleAnimation = "glow_pulse")
        )

        val assets = linkedMapOf(
            "home_pixel" to HubAsset("home_pixel", "background", "resource", "svhub:textures/gui/generated/home_bg.png", 512, 288, tags = listOf("pixel", "home")),
            "wiki_pixel" to HubAsset("wiki_pixel", "background", "resource", "svhub:textures/gui/generated/wiki_bg.png", 512, 288, tags = listOf("pixel", "wiki")),
            "commands_pixel" to HubAsset("commands_pixel", "background", "resource", "svhub:textures/gui/generated/commands_bg.png", 512, 288, tags = listOf("pixel", "commands")),
            "updates_pixel" to HubAsset("updates_pixel", "background", "resource", "svhub:textures/gui/generated/updates_bg.png", 512, 288, tags = listOf("pixel", "updates")),
            "pixel_frame" to HubAsset("pixel_frame", "frame", "resource", "svhub:textures/gui/generated/pixel_frame.png", 64, 64),
            "sparkle_anim" to HubAsset("sparkle_anim", "animated_image", "resource", "svhub:textures/gui/generated/sparkle_strip.png", 1024, 64, tags = listOf("pixel", "animation"))
        )

        val home = HubPage(
            "home", "home", "home", LocalizedText(mapOf("vi_vn" to "SV HUB", "en_us" to "SV HUB")),
            LocalizedText(mapOf("vi_vn" to "Mọi thứ bạn cần biết về server", "en_us" to "Everything you need to know")),
            listOf("home", "trang chủ", "help"), "pixel_classic", "home", components = listOf(
                component("home_title", "animated_text", "text" to "CHÀO MỪNG ĐẾN VỚI SERVER", "animation" to "pixel_pop", "align" to "center", "scale" to 1.55),
                component("home_search", "search_box", "placeholder" to "Tìm Pokémon, Fakemon, lệnh, hướng dẫn..."),
                component("home_intro", "notice", "variant" to "info", "text" to "Không biết bắt đầu từ đâu? Mở Cách chơi hoặc Lệnh cần biết."),
                button("how_play_btn", "🎮  CÁCH CHƠI", "guide/how-to-play", "Hướng dẫn bắt đầu cho người mới"),
                button("pokemon_btn", "🐾  POKÉMON", "wiki/pokemon", "Tra cứu trực tiếp dữ liệu và model Cobblemon"),
                button("fakemon_btn", "✨  FAKEMON", "wiki/fakemon", "Các Pokémon custom/aspect của server"),
                button("essential_btn", "⚡  LỆNH CẦN BIẾT", "commands/essential", "Những lệnh dùng hằng ngày"),
                button("commands_btn", "⌨  TẤT CẢ LỆNH", "commands", "Tìm lệnh theo tên hoặc chức năng"),
                button("updates_btn", "🆕  CẬP NHẬT MỚI", "updates", "Có gì mới trên server"),
                button("mods_btn", "🧩  MODS & TÍNH NĂNG", "mods", "Xem mod nào có ở server/client")
            )
        )

        val howToPlay = HubPage(
            "how_to_play", "guide/how-to-play", "guide", LocalizedText.of("Cách chơi"),
            LocalizedText.of("Bắt đầu nhanh, không cần đoán server hoạt động thế nào"),
            listOf("cách chơi", "hướng dẫn", "beginner", "starter"), "pixel_wiki", "guide", components = listOf(
                component("htp_title", "animated_text", "text" to "CÁCH CHƠI", "animation" to "typewriter", "scale" to 1.45),
                component("htp_1", "heading", "text" to "1. Làm quen với Hub"),
                component("htp_1_text", "text", "text" to "Nhấn H để mở SVHub bất kỳ lúc nào. Thanh tìm kiếm luôn ở phía trên để bạn không phải nhớ menu hay ID kỹ thuật."),
                component("htp_2", "heading", "text" to "2. Tra cứu Pokémon & Fakemon"),
                component("htp_2_text", "text", "text" to "Mục Pokémon/Fakemon lấy dữ liệu trực tiếp từ Cobblemon. Trang chi tiết sử dụng model thật của resource pack hiện tại."),
                component("htp_3", "heading", "text" to "3. Dùng lệnh cần thiết"),
                component("htp_3_text", "text", "text" to "Mục Lệnh cần biết gom các lệnh quan trọng cho di chuyển, tiện ích và gameplay."),
                button("htp_commands", "Mở Lệnh cần biết", "commands/essential", "")
            )
        )

        val pokemon = HubPage(
            "pokemon", "wiki/pokemon", "wiki", LocalizedText.of("Pokémon"), LocalizedText.of("Pokédex động lấy trực tiếp từ Cobblemon"),
            listOf("pokemon", "pokédex", "pokedex", "wiki"), "pixel_wiki", "pokemon", components = listOf(
                component("pokemon_title", "animated_text", "text" to "POKÉMON INFORMATION", "animation" to "shimmer", "scale" to 1.35),
                component("pokemon_info", "notice", "variant" to "info", "text" to "Danh sách bên dưới được tạo tự động từ species registry của Cobblemon trên client."),
                component("pokemon_grid", "grid", "provider" to "cobblemon:pokemon", "columns" to 5, "pageSize" to 30)
            )
        )

        val fakemon = HubPage(
            "fakemon", "wiki/fakemon", "wiki", LocalizedText.of("Fakemon"), LocalizedText.of("Species custom và aspect đặc biệt của server"),
            listOf("fakemon", "custom pokemon", "wiki"), "pixel_wiki", "fakemon", components = listOf(
                component("fakemon_title", "animated_text", "text" to "FAKEMON INFORMATION", "animation" to "pixel_flicker", "scale" to 1.35),
                component("fakemon_info", "notice", "variant" to "accent", "text" to "Model dùng trực tiếp cobblemon:pokemon_model; không cần maintain bộ ảnh Pokémon riêng."),
                component("fakemon_grid", "grid", "provider" to "cobblemon:fakemon", "columns" to 5, "pageSize" to 30)
            )
        )

        val essential = HubPage(
            "essential_commands", "commands/essential", "commands", LocalizedText.of("Lệnh cần biết"), LocalizedText.of("Những lệnh nên nhớ khi chơi server"),
            listOf("lệnh", "commands", "essential", "cần biết"), "pixel_commands", "bolt", components = listOf(
                component("essential_title", "animated_text", "text" to "ESSENTIAL COMMANDS", "animation" to "typewriter", "scale" to 1.35),
                commandCard("cmd_spawn", "/spawn", "Quay về khu spawn chính.", "/spawn"),
                commandCard("cmd_hub", "/hub", "Mở SVHub.", "/hub"),
                commandCard("cmd_wiki", "/wiki", "Mở nhanh Wiki/Hub.", "/wiki")
            )
        )

        val commands = HubPage(
            "commands", "commands", "commands", LocalizedText.of("Tất cả lệnh"), LocalizedText.of("Tìm theo tên lệnh hoặc chức năng"),
            listOf("commands", "lệnh"), "pixel_commands", "terminal", components = listOf(
                component("commands_title", "animated_text", "text" to "COMMANDS", "animation" to "glow_pulse", "scale" to 1.4),
                component("commands_search", "search_box", "placeholder" to "Tìm lệnh..."),
                button("commands_essential", "⚡ Lệnh cần biết", "commands/essential", "Danh sách rút gọn cho người chơi"),
                component("commands_dynamic", "grid", "provider" to "environment:commands", "pageSize" to 42)
            )
        )

        val updates = HubPage(
            "updates", "updates", "updates", LocalizedText.of("Cập nhật mới"), LocalizedText.of("Tin mới, thay đổi gameplay và nội dung vừa thêm"),
            listOf("updates", "changelog", "news", "cập nhật"), "pixel_updates", "sparkle", components = listOf(
                component("updates_title", "animated_text", "text" to "CẬP NHẬT MỚI", "animation" to "glow_pulse", "scale" to 1.5),
                component("updates_anim", "animated_image", "asset" to "sparkle_anim", "frames" to 8, "fps" to 8, "height" to 48),
                component("updates_hint", "notice", "variant" to "accent", "text" to "Admin có thể tạo update card, banner pixel và animated title trực tiếp trong Editor."),
                component("updates_empty", "text", "text" to "Chưa có bản tin nào được publish. Mở Editor để thêm update đầu tiên.")
            )
        )

        val mods = HubPage(
            "mods", "mods", "system", LocalizedText.of("Mods & tính năng"), LocalizedText.of("Phân biệt mod Server, Client và mod có ở cả hai phía"),
            listOf("mods", "server mods", "client mods", "tính năng"), "pixel_commands", "mods", components = listOf(
                component("mods_title", "animated_text", "text" to "MOD ENVIRONMENT", "animation" to "shimmer", "scale" to 1.35),
                component("mods_info", "notice", "variant" to "info", "text" to "SVHub không giả định danh sách mod server và client giống nhau. Danh sách dưới đây được negotiate khi kết nối."),
                component("mods_grid", "grid", "provider" to "environment:mods")
            )
        )

        return HubContent(
            schema = HUB_SCHEMA_VERSION, revision = 0L, defaultLocale = "vi_vn", defaultTheme = "pixel_classic",
            themes = themes, assets = assets,
            pages = listOf(home, howToPlay, pokemon, fakemon, essential, commands, updates, mods),
            cobblemonWiki = CobblemonWikiConfig(true, true, setOf("fakemon", "server", "svmon"))
        )
    }

    private fun component(id: String, type: String, vararg props: Pair<String, Any>): HubComponent =
        HubComponent(id = id, type = type, props = json(*props))

    private fun button(id: String, label: String, target: String, description: String): HubComponent = HubComponent(
        id = id, type = "button", props = json("label" to label, "description" to description),
        action = HubActionSpec("$id:open", "open_page", target)
    )

    private fun commandCard(id: String, command: String, description: String, executable: String): HubComponent = HubComponent(
        id = id, type = "command_card", props = json("command" to command, "description" to description),
        action = HubActionSpec("$id:run", "run_command", executable, cooldownMs = 750L)
    )

    private fun json(vararg pairs: Pair<String, Any>): JsonObject = JsonObject().apply {
        pairs.forEach { (key, value) ->
            when (value) {
                is String -> addProperty(key, value)
                is Number -> addProperty(key, value)
                is Boolean -> addProperty(key, value)
                else -> addProperty(key, value.toString())
            }
        }
    }
}
