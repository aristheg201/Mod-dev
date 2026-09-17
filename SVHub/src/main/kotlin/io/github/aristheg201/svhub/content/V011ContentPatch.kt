package io.github.aristheg201.svhub.content

import com.google.gson.JsonArray
import com.google.gson.JsonObject

/**
 * Installs the 0.1.1 player-facing modules on top of the exact bundled SVHub handbook.
 * Admin-authored hubs are deliberately left alone: the patch requires well-known
 * bundled home component ids and leaves a hidden marker page after the first upgrade.
 */
object V011ContentPatch {
    private const val MARKER_PAGE = "svhub_v011"

    fun apply(content: HubContent): HubContent? {
        if (content.pages.any { it.id == MARKER_PAGE }) return null
        val home = content.page("home") ?: return null
        val bundledHome = home.components.mapTo(HashSet()) { it.id }
        if ("home_pokedex" !in bundledHome || "home_shop" !in bundledHome) return null

        val arcadeTheme = HubTheme(
            id = "sv_arcade",
            backgroundPreset = "pixel_neon",
            panelStyle = "clean",
            buttonStyle = "clean",
            titleAnimation = "glow_pulse",
            palette = ThemePalette(
                background = 0xFF080B14.toInt(),
                panel = 0xF0101727.toInt(),
                panelAlt = 0xF0172238.toInt(),
                text = 0xFFF5F7FF.toInt(),
                mutedText = 0xFF9EAECC.toInt(),
                accent = 0xFF5DE2FF.toInt(),
                accent2 = 0xFFFFC857.toInt(),
                danger = 0xFFFF657A.toInt()
            ),
            motionStrength = 1.0f
        )

        val launchPad = listOf(
            component("v011_title", "animated_text", "text" to "<bold>SV WORLD • 0.1.1</bold>", "animation" to "glow_pulse", "align" to "center", "scale" to 1.45),
            component("v011_intro", "notice", "text" to "Gacha CS:GO, Skin Showcase và Linh Thú đã được gom vào cùng SVHub. Tất cả giao dịch vẫn do server xác thực."),
            button("v011_gacha", "GACHA • Skin Roulette", "gacha", "Mở khu roulette, xem pool và đi tới Key Shop."),
            button("v011_skins", "SKIN SHOWCASE • Exclusive", "skins/showcase", "DBZ BeastCoin, Naruto và PokeLegends HunterCoin."),
            button("v011_companions", "LINH THÚ • Vanilla", "companions", "Chọn mob Minecraft vanilla đi cùng bạn."),
            component("v011_separator", "separator")
        )
        val patchedHome = home.copy(theme = "sv_arcade", components = launchPad + home.components)

        val gacha = HubPage(
            id = "gacha",
            route = "gacha",
            category = "gacha",
            title = LocalizedText.of("Skin Roulette"),
            subtitle = LocalizedText.of("Roulette phong cách CS:GO, key-based và server-authoritative."),
            tags = listOf("gacha", "crate", "csgo", "roulette", "skin", "key"),
            theme = "sv_arcade",
            icon = "minecraft:tripwire_hook",
            components = listOf(
                component("gacha_title", "animated_text", "text" to "<gold><bold>SKIN ROULETTE</bold></gold>", "animation" to "shimmer", "align" to "center", "scale" to 1.35),
                component("gacha_rules", "notice", "text" to "Skin Crate dùng animation CS:GO 60 bước. Kết quả và phần thưởng được quyết định ở server; client chỉ hiển thị."),
                table("gacha_info", listOf(
                    listOf("Hệ", "Thiết lập"),
                    listOf("Animation", "CS:GO roulette"),
                    listOf("Mở crate", "Key"),
                    listOf("Pool", "Pokémon + skin custom"),
                    listOf("Key shop", "HunterCoin")
                )),
                commandRun("gacha_shop", "/gachashop", "Mở Key Shop để mua key premium."),
                button("gacha_to_skins", "Xem Skin Showcase", "skins/showcase", "So sánh các bộ skin trước khi mua."),
                component("gacha_security", "text", "text" to "Không có roll client-side, không tin giá/currency do client gửi và không chạy command tùy ý từ GUI.")
            )
        )

        val skins = HubPage(
            id = "skin_showcase",
            route = "skins/showcase",
            category = "skins",
            title = LocalizedText.of("Skin Showcase"),
            subtitle = LocalizedText.of("Một nơi cho toàn bộ wardrobe và shop độc quyền."),
            tags = listOf("skin", "showcase", "dbz", "naruto", "pokelegends", "huntercoin", "beastcoin"),
            theme = "sv_arcade",
            icon = "cobblemon:pokemon_model",
            components = listOf(
                component("skins_title", "animated_text", "text" to "<aqua><bold>EXCLUSIVE COLLECTION</bold></aqua>", "animation" to "wave", "align" to "center", "scale" to 1.25),
                component("skins_summary", "notice", "text" to "0.1.1 bổ sung 658 skin: 8 DBZ, 6 Naruto tuyển chọn và 644 PokeLegends. Skin độc quyền là cosmetic aspect riêng, không mượn aspect gameplay như Mega."),
                component("skins_hunter", "heading", "text" to "Hunter Exclusive"),
                component("skins_hunter_text", "text", "text" to "Naruto + PokeLegends bán bằng HunterCoin. Mỗi lượt mua kèm Pokémon cùng species với 3 IV ngẫu nhiên đạt 31."),
                table("skins_price", listOf(
                    listOf("Nhóm", "Giá chuẩn"),
                    listOf("HunterCoin", "1 HC = 25.000"),
                    listOf("Exclusive thường", "4–6 HC"),
                    listOf("Legendary/Mythical premium", ">= 6 HC / 150.000"),
                    listOf("Legendary + model/animation", "8+ HC")
                )),
                commandRun("skins_hunter_shop", "/svhub-hunter", "Mở quầy Hunter Exclusive; 24 trang, 650 mẫu."),
                component("skins_dbz", "heading", "text" to "Dragon Ball • BeastCoin"),
                component("skins_dbz_text", "text", "text" to "DBZ là bộ riêng dùng BeastCoin, gồm Annihilape, Blastoise, Infernape, Mewtwo, Rayquaza, Sawk và hai Snorlax Buu."),
                commandRun("skins_dbz_shop", "/svhub-dbz", "Mở quầy DBZ BeastCoin."),
                commandRun("skins_inventory", "/trangphuc", "Mở tủ skin đã sở hữu."),
                button("skins_to_gacha", "Đi tới Skin Roulette", "gacha", "Mở khu gacha và Key Shop.")
            )
        )

        val companions = HubPage(
            id = "companions",
            route = "companions",
            category = "companions",
            title = LocalizedText.of("Linh Thú"),
            subtitle = LocalizedText.of("Mob vanilla Minecraft, một linh thú hoạt động cho mỗi player."),
            tags = listOf("pet", "companion", "linh thu", "vanilla", "mob"),
            theme = "sv_arcade",
            icon = "minecraft:lead",
            components = buildList {
                add(component("pet_title", "animated_text", "text" to "<yellow><bold>VANILLA COMPANIONS</bold></yellow>", "animation" to "gentle_bounce", "align" to "center", "scale" to 1.25))
                add(component("pet_note", "notice", "text" to "Chọn một linh thú. Khi đổi lựa chọn, entity cũ được dọn trước khi spawn entity mới; selection được lưu server-side."))
                add(companionButton("pet_allay", "Allay", "allay", "Bay cạnh người chơi; nhỏ và dễ nhìn."))
                add(companionButton("pet_axolotl", "Axolotl", "axolotl", "Linh thú nước."))
                add(companionButton("pet_bee", "Bee", "bee", "Ong đồng hành."))
                add(companionButton("pet_cat", "Cat", "cat", "Mèo vanilla."))
                add(companionButton("pet_fox", "Fox", "fox", "Cáo vanilla."))
                add(companionButton("pet_frog", "Frog", "frog", "Ếch vanilla."))
                add(companionButton("pet_parrot", "Parrot", "parrot", "Vẹt vanilla."))
                add(companionButton("pet_rabbit", "Rabbit", "rabbit", "Thỏ vanilla."))
                add(companionButton("pet_wolf", "Wolf", "wolf", "Sói vanilla."))
                add(companionButton("pet_armadillo", "Armadillo", "armadillo", "Armadillo vanilla."))
                add(companionButton("pet_sniffer", "Sniffer", "sniffer", "Linh thú cỡ lớn."))
                add(companionButton("pet_none", "Cất Linh Thú", "none", "Dọn linh thú hiện tại nhưng giữ Hub sạch."))
            }
        )

        val marker = HubPage(
            id = MARKER_PAGE,
            route = "system/v011",
            category = "system",
            title = LocalizedText.of("SVHub 0.1.1"),
            theme = "sv_arcade",
            showInNavigation = false,
            components = listOf(component("v011_marker", "text", "text" to "Bundled 0.1.1 modules installed."))
        )

        val pages = content.pages.map { if (it.id == home.id) patchedHome else it } + listOf(gacha, skins, companions, marker)
        return content.copy(themes = content.themes + (arcadeTheme.id to arcadeTheme), pages = pages)
    }

    private fun component(id: String, type: String, vararg props: Pair<String, Any>): HubComponent =
        HubComponent(id = id, type = type, props = json(*props))

    private fun button(id: String, label: String, target: String, description: String): HubComponent = HubComponent(
        id = id,
        type = "button",
        props = json("label" to label, "description" to description),
        action = HubActionSpec("$id:open", "open_page", target)
    )

    private fun commandRun(id: String, command: String, description: String): HubComponent = HubComponent(
        id = id,
        type = "command_card",
        props = json("command" to command, "description" to description),
        action = HubActionSpec("$id:run", "run_command", command, cooldownMs = 750L)
    )

    private fun companionButton(id: String, label: String, entity: String, description: String): HubComponent = HubComponent(
        id = id,
        type = "button",
        props = json("label" to label, "description" to description),
        action = HubActionSpec("$id:select", "companion_select", entity, cooldownMs = 750L)
    )

    private fun table(id: String, rows: List<List<String>>): HubComponent {
        val array = JsonArray()
        rows.forEach { row -> array.add(JsonArray().also { out -> row.forEach(out::add) }) }
        return HubComponent(id = id, type = "table", props = JsonObject().apply { add("rows", array) })
    }

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
