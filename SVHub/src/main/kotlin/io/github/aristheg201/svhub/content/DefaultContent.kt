package io.github.aristheg201.svhub.content

import com.google.gson.JsonArray
import com.google.gson.JsonObject

/**
 * Player-facing seed content. Keep this useful without requiring an editor pass:
 * it documents the actual server flows and commands instead of implementation details.
 */
object DefaultContent {
    fun create(): HubContent {
        val themes = linkedMapOf(
            "clean_dark" to HubTheme(
                id = "clean_dark",
                backgroundAsset = null,
                backgroundPreset = "clean",
                panelStyle = "clean",
                buttonStyle = "clean",
                titleAnimation = "none",
                palette = ThemePalette(
                    background = 0xFF0D1117.toInt(),
                    panel = 0xF0161D27.toInt(),
                    panelAlt = 0xF01E2936.toInt(),
                    text = 0xFFF2F5F7.toInt(),
                    mutedText = 0xFFA9B4C0.toInt(),
                    accent = 0xFFE8C45A.toInt(),
                    accent2 = 0xFF6CB6B1.toInt(),
                    danger = 0xFFE06C75.toInt()
                ),
                motionStrength = 0f
            ),
            "clean_wiki" to HubTheme(
                id = "clean_wiki",
                backgroundPreset = "clean",
                panelStyle = "clean",
                buttonStyle = "clean",
                titleAnimation = "none",
                palette = ThemePalette(
                    background = 0xFF0B1114.toInt(),
                    panel = 0xF0141D22.toInt(),
                    panelAlt = 0xF01B292E.toInt(),
                    text = 0xFFF1F6F4.toInt(),
                    mutedText = 0xFFA5B7B2.toInt(),
                    accent = 0xFFD9C36A.toInt(),
                    accent2 = 0xFF67B8A7.toInt(),
                    danger = 0xFFE07171.toInt()
                ),
                motionStrength = 0f
            )
        )

        val home = HubPage(
            id = "home",
            route = "home",
            category = "home",
            title = LocalizedText.of("SV Hub"),
            subtitle = LocalizedText.of("Tra cứu nhanh những thứ cần dùng khi chơi server."),
            tags = listOf("home", "trang chủ", "help", "tro giup", "cam nang"),
            theme = "clean_dark",
            components = listOf(
                component("home_start", "heading", "text" to "Bắt đầu từ đây"),
                component("home_intro", "text", "text" to "Mới vào server: mở Hướng dẫn chơi. Cần lệnh: mở Lệnh người chơi. Muốn mua, bán hoặc đổi Pokémon: mở Shop Pokémon & Giao dịch."),
                button("home_guide", "Hướng dẫn chơi", "guide/how-to-play", "Lộ trình từ starter đến GTS, raid và đấu trường."),
                button("home_commands", "Lệnh người chơi", "commands", "TPA, home, claim, kinh tế, GTS, hoạt động và battle."),
                button("home_shop", "Shop Pokémon & Giao dịch", "shop/pokemon", "GTS, Key Shop, Wonder Trade, STS và tiền tệ."),
                button("home_pokedex", "Pokédex", "wiki/pokemon", "Tra Pokémon, chỉ số, ability, tiến hóa và dữ liệu chiến đấu."),
                button("home_fakedex", "Fakédex", "wiki/fakemon", "Fakemon, form/aspect custom và dữ liệu từ pack đang cài."),
                button("home_activities", "Hoạt động", "activities", "Hunt, raid, Battle Tower, Ranked, Research và các hệ thống khác."),
                button("home_faq", "Câu hỏi nhanh", "faq", "Heal, warp, home, claim và giao dịch Pokémon."),
                button("home_updates", "Cập nhật", "updates", "Thông báo gameplay dành cho người chơi.")
            )
        )

        val howToPlay = HubPage(
            id = "how_to_play",
            route = "guide/how-to-play",
            category = "guide",
            title = LocalizedText.of("Hướng dẫn chơi"),
            subtitle = LocalizedText.of("Một lộ trình cụ thể để không phải tự đoán server vận hành thế nào."),
            tags = listOf("cách chơi", "hướng dẫn", "beginner", "starter", "new player"),
            theme = "clean_dark",
            components = listOf(
                component("guide_first", "heading", "text" to "1. Chọn starter và chuẩn bị party"),
                component("guide_first_text", "text", "text" to "Chọn starter của bạn. Chuỗi hướng dẫn đầu server thưởng 1,000,000 CobbleDollars và 12 Poké Ball sau bước mở đầu."),
                component("guide_resources", "heading", "text" to "2. Đi Resources và bắt Pokémon"),
                component("guide_resources_text", "text", "text" to "Dùng /warps, chọn khu Resources, sau đó bắt 3 Pokémon hoang dã. Pokémon Center hồi máu miễn phí khi cần."),
                commandRun("guide_warps", "/warps", "Mở danh sách điểm đến của server."),
                component("guide_train", "heading", "text" to "3. Train, battle và tiến hóa"),
                component("guide_train_text", "text", "text" to "Đánh ít nhất 2 trận Pokémon, thắng ít nhất 1 trận; đưa một Pokémon lên khoảng Lv.15 và hoàn thành một lần tiến hóa để làm quen vòng progression đầu game."),
                component("guide_money", "heading", "text" to "4. Kiếm tiền và dùng shop"),
                component("guide_money_text", "text", "text" to "CobbleDollars là tiền gameplay thông thường. Mở /shop để mua hoặc bán item. Chuỗi hướng dẫn tiếp tục bằng việc tự kiếm CobbleDollars rồi thực hiện ít nhất một giao dịch mua và một giao dịch bán."),
                commandRun("guide_shop", "/shop", "Mở shop item của server."),
                component("guide_gts", "heading", "text" to "5. Mua bán Pokémon qua GTS"),
                component("guide_gts_text", "text", "text" to "Mở /gts. Hãy thử đăng bán một Pokémon và mua một Pokémon để hiểu chợ người chơi. GTS là nơi chính để giao dịch Pokémon giữa player."),
                commandRun("guide_gts_open", "/gts", "Mở Global Trade Station."),
                component("guide_breed", "heading", "text" to "6. Breeding"),
                component("guide_breed_text", "text", "text" to "Mở hệ breeding, tạo một trứng rồi ấp trứng. Đây là bước tiếp theo của guided campaign trước khi chuyển sang hoạt động combat."),
                component("guide_pve", "heading", "text" to "7. Hunt và Raid"),
                component("guide_pve_text", "text", "text" to "Dùng /hunts để xem Hunt. Sau đó xem danh sách raid bằng /raid list, tham gia và hoàn thành ít nhất một raid."),
                commandRun("guide_hunts", "/hunts", "Xem các mục tiêu Hunt đang hoạt động."),
                commandRun("guide_raids", "/raid list", "Xem raid đang có."),
                component("guide_comp", "heading", "text" to "8. Battle Tower, Ranked và PvP"),
                component("guide_comp_text", "text", "text" to "Sau PvE, guided campaign chuyển sang Battle Tower, Ranked và trận Pokémon với player khác. Các trainer Chapter I được thiết kế ở Lv.100, có IV/EV build, Tera và có thể dùng Mega."),
                component("guide_more", "heading", "text" to "9. Các hệ thống còn lại"),
                component("guide_more_text", "text", "text" to "Research, Wonder Trade, STS, Expedition, Showcase và Daily Rewards đều nằm trong progression sau đó. Wonder Trade đổi Pokémon lấy một Pokémon ngẫu nhiên; STS bán Pokémon không dùng nữa. Cả hai giao dịch đều là quyết định cuối cùng."),
                button("guide_commands", "Xem toàn bộ lệnh người chơi", "commands", "")
            )
        )

        val quickCommands = HubPage(
            id = "essential_commands",
            route = "commands/essential",
            category = "commands",
            title = LocalizedText.of("Lệnh nhanh"),
            subtitle = LocalizedText.of("Những lệnh dùng thường xuyên nhất."),
            tags = listOf("lệnh", "commands", "essential", "lenh", "trogiup"),
            theme = "clean_dark",
            components = listOf(
                commandRun("quick_lenh", "/lenh", "Mở cẩm nang lệnh của server."),
                commandRun("quick_spawn", "/spawn", "Về spawn."),
                commandRun("quick_warps", "/warps", "Mở danh sách warp."),
                commandRun("quick_homes", "/homes", "Mở danh sách home cá nhân."),
                commandRun("quick_shop", "/shop", "Mở shop."),
                commandRun("quick_gts", "/gts", "Mở chợ Pokémon GTS."),
                commandRun("quick_hunts", "/hunts", "Xem Hunt."),
                commandRun("quick_bp", "/battlepass", "Mở Battle Pass."),
                button("quick_all", "Xem tất cả lệnh", "commands", "")
            )
        )

        val commands = HubPage(
            id = "commands",
            route = "commands",
            category = "commands",
            title = LocalizedText.of("Lệnh người chơi"),
            subtitle = LocalizedText.of("Danh sách curate theo đúng các hệ thống player dùng trên server."),
            tags = listOf("commands", "lệnh", "lenh", "trogiup", "camnanglenh", "camnang"),
            theme = "clean_dark",
            components = listOf(
                commandRun("cmd_lenh", "/lenh", "Mở cẩm nang lệnh. Alias: /trogiup, /camnanglenh, /camnang."),
                component("cmd_travel_h", "heading", "text" to "Dịch chuyển"),
                commandTemplate("cmd_tpa", "/tpa <tên>", "Gửi yêu cầu dịch chuyển tới player."),
                commandTemplate("cmd_tpahere", "/tpahere <tên>", "Mời player dịch chuyển tới bạn."),
                commandRun("cmd_tpaccept", "/tpaccept", "Chấp nhận yêu cầu TPA."),
                commandRun("cmd_tpdeny", "/tpdeny", "Từ chối yêu cầu TPA."),
                commandRun("cmd_tpcancel", "/tpcancel", "Hủy yêu cầu TPA đang gửi."),
                commandRun("cmd_back", "/back", "Quay lại vị trí trước."),
                commandRun("cmd_spawn", "/spawn", "Về spawn."),
                commandRun("cmd_warps", "/warps", "Mở menu warp."),
                commandTemplate("cmd_warp", "/warp <name>", "Đi tới một warp theo tên."),
                component("cmd_home_h", "heading", "text" to "Home"),
                commandRun("cmd_homes", "/homes", "Mở danh sách home cá nhân."),
                commandRun("cmd_home", "/home", "Về home mặc định."),
                commandRun("cmd_sethome", "/sethome", "Đặt home mặc định tại vị trí hiện tại."),
                commandTemplate("cmd_sethome_named", "/sethome <tên>", "Tạo home có tên. Giới hạn mặc định của server: 5 home."),
                commandTemplate("cmd_home_named", "/home <tên>", "Dịch chuyển tới home có tên."),
                commandTemplate("cmd_delhome", "/delhome <tên>", "Xóa home có tên."),
                component("cmd_claim_h", "heading", "text" to "Claim đất"),
                commandRun("cmd_flan_menu", "/flan menu", "Mở menu claim."),
                commandRun("cmd_flan_list", "/flan list", "Xem claim của bạn."),
                commandRun("cmd_flan_add", "/flan addclaims", "Thêm claim blocks nếu hệ thống cho phép."),
                commandRun("cmd_flan_delete", "/flan delete", "Xóa claim đang thao tác."),
                commandTemplate("cmd_trust", "/trust <tên>", "Cho player quyền trong claim."),
                commandTemplate("cmd_untrust", "/untrust <tên>", "Gỡ quyền player khỏi claim."),
                component("cmd_economy_h", "heading", "text" to "Kinh tế"),
                commandRun("cmd_money_top", "/cobbledollars leaderboard", "Xem bảng xếp hạng CobbleDollars."),
                commandTemplate("cmd_pay", "/cobbledollars pay <tên> <số tiền>", "Chuyển CobbleDollars cho player khác."),
                commandRun("cmd_dep_beast", ServerHelpText.get("DefaultContent_0"), ServerHelpText.get("DefaultContent_1")),
                commandRun("cmd_dep_hunter", ServerHelpText.get("DefaultContent_2"), ServerHelpText.get("DefaultContent_3")),
                commandTemplate("cmd_with_beast", ServerHelpText.get("DefaultContent_4"), ServerHelpText.get("DefaultContent_5")),
                commandTemplate("cmd_with_hunter", ServerHelpText.get("DefaultContent_6"), ServerHelpText.get("DefaultContent_7")),
                component("cmd_gts_h", "heading", "text" to "GTS"),
                commandRun("cmd_gts", "/gts", "Mở Global Trade Station."),
                commandRun("cmd_gts_sell_p", "/gts sell pokemon", "Đăng bán Pokémon."),
                commandRun("cmd_gts_sell_i", "/gts sell item", "Đăng bán item."),
                commandRun("cmd_gts_manage", "/gts manage", "Quản lý listing của bạn."),
                commandRun("cmd_gts_expired", "/gts expired", "Xem listing hết hạn."),
                commandRun("cmd_gts_history", "/gts history", "Xem lịch sử giao dịch."),
                component("cmd_activity_h", "heading", "text" to "Hoạt động"),
                commandRun("cmd_hunts", "/hunts", "Mở Hunt."),
                commandRun("cmd_raid", "/raid list", "Xem raid đang có."),
                commandRun("cmd_daily", "/dailyrewards", "Mở Daily Rewards."),
                commandRun("cmd_battlepass", "/battlepass", "Mở Battle Pass. Alias phổ biến: /bp."),
                commandRun("cmd_bf", "/battlefactory open", "Mở Battle Factory."),
                commandRun("cmd_bf_status", "/battlefactory status", "Xem trạng thái Battle Factory."),
                commandRun("cmd_bf_shop", "/battlefactory open_shop", "Mở shop Battle Factory."),
                component("cmd_shop_h", "heading", "text" to "Shop"),
                commandRun("cmd_shop", "/shop", "Mở shop chính. Alias: /cuahang."),
                commandRun("cmd_keyshop", "/keyshop", "Mở Gacha Key Shop. Alias: /gachashop, /muachia.")
            )
        )

        val pokemonShop = HubPage(
            id = "pokemon_shop",
            route = "shop/pokemon",
            category = "shop",
            title = LocalizedText.of("Shop Pokémon & Giao dịch"),
            subtitle = LocalizedText.of("Mua bán Pokémon, key và các kênh đổi Pokémon trên server."),
            tags = listOf("shop pokemon", "gts", "gacha", "keyshop", "wondertrade", "sts", ServerHelpText.get("DefaultContent_8"), ServerHelpText.get("DefaultContent_9")),
            theme = "clean_dark",
            components = listOf(
                component("shop_gts_h", "heading", "text" to "GTS — chợ Pokémon giữa người chơi"),
                component("shop_gts_text", "text", "text" to "Dùng GTS khi bạn muốn mua hoặc bán một Pokémon cụ thể với người chơi khác. Bạn có thể đăng Pokémon, theo dõi listing, lấy lại listing hết hạn và xem lịch sử giao dịch."),
                commandRun("shop_gts_open", "/gts", "Mở GTS."),
                commandRun("shop_gts_sell", "/gts sell pokemon", "Đăng Pokémon đang chọn/bị hệ thống yêu cầu chọn lên GTS."),
                commandRun("shop_gts_manage", "/gts manage", "Quản lý listing của bạn."),
                component("shop_key_h", "heading", "text" to "Gacha Key Shop"),
                component("shop_key_text", "text", "text" to ServerHelpText.get("DefaultContent_10")),
                table("shop_key_prices", listOf(
                    listOf("Loại key", "Gói", "Giá"),
                    listOf("Starter", "10 key", ServerHelpText.get("DefaultContent_11")),
                    listOf("Starter", "5 key", ServerHelpText.get("DefaultContent_12")),
                    listOf("Legendary", "10 key", ServerHelpText.get("DefaultContent_13")),
                    listOf("Legendary", "1 key", ServerHelpText.get("DefaultContent_14")),
                    listOf("Legendary Shiny", "10 key", ServerHelpText.get("DefaultContent_15")),
                    listOf("Skin", "10 key", ServerHelpText.get("DefaultContent_16")),
                    listOf("Ultra & Paradox", "10 key", ServerHelpText.get("DefaultContent_17"))
                )),
                commandRun("shop_key_open", "/keyshop", "Mở Key Shop. Alias: /gachashop, /muachia."),
                component("shop_currency_h", "heading", "text" to "Tiền tệ"),
                table("shop_currency", listOf(
                    listOf("Tiền", "Dùng cho"),
                    listOf("CobbleDollars", "Tiền gameplay thông thường; shop item và kinh tế cơ bản."),
                    listOf(ServerHelpText.get("DefaultContent_18"), "Tiền gameplay đặc biệt; dùng ở một số shop/key và skin."),
                    listOf(ServerHelpText.get("DefaultContent_19"), "Tiền premium; không đổi trực tiếp từ CobbleDollars.")
                )),
                component("shop_exchange", "notice", "text" to ServerHelpText.get("DefaultContent_20")),
                component("shop_other_h", "heading", "text" to "Wonder Trade và STS"),
                component("shop_other_text", "text", "text" to "Wonder Trade đổi một Pokémon của bạn lấy Pokémon ngẫu nhiên. STS dùng để bán Pokémon không còn cần. Đây đều là giao dịch cuối cùng; kiểm tra kỹ Pokémon trước khi xác nhận."),
                button("shop_to_pokedex", "Tra Pokémon trước khi giao dịch", "wiki/pokemon", "")
            )
        )

        val pokemon = HubPage(
            id = "pokemon",
            route = "wiki/pokemon",
            category = "wiki",
            title = LocalizedText.of("Pokédex"),
            subtitle = LocalizedText.of("Dữ liệu species và model đang thực sự có trên client."),
            tags = listOf("pokemon", "pokédex", "pokedex", "wiki", "ability", "stats", "evolution", "moves"),
            theme = "clean_wiki",
            components = listOf(
                component("pokemon_info", "text", "text" to "Chọn một Pokémon để xem type, base stats, ability, catch rate, egg group, EV yield, tiến hóa, moves, drops và mô tả Pokédex khi dữ liệu đó có trong species."),
                component("pokemon_grid", "grid", "provider" to "cobblemon:pokemon", "columns" to 4, "pageSize" to 20)
            )
        )

        val fakemon = HubPage(
            id = "fakemon",
            route = "wiki/fakemon",
            category = "wiki",
            title = LocalizedText.of("Fakédex"),
            subtitle = LocalizedText.of("Fakemon và form/aspect custom đang có trong các pack của server."),
            tags = listOf("fakemon", "fakedex", "custom pokemon", "eldorian", "bloodmoon", "myths"),
            theme = "clean_wiki",
            components = listOf(
                component("fakemon_info", "text", "text" to "Danh sách nhận diện custom species và custom form/aspect từ species registry, không dựa riêng vào namespace."),
                component("fakemon_grid", "grid", "provider" to "cobblemon:fakemon", "columns" to 4, "pageSize" to 20)
            )
        )

        val activities = HubPage(
            id = "activities",
            route = "activities",
            category = "guide",
            title = LocalizedText.of("Hoạt động"),
            subtitle = LocalizedText.of("Những hệ thống nên mở sau khi đã ổn định party và kinh tế."),
            tags = listOf("hunt", "raid", "tower", "ranked", "research", "expedition", "showcase", "daily"),
            theme = "clean_dark",
            components = listOf(
                component("act_hunt", "heading", "text" to "Hunt"),
                component("act_hunt_t", "text", "text" to "Theo dõi mục tiêu Hunt và hoàn thành một Hunt trong guided campaign."),
                commandRun("act_hunts", "/hunts", "Mở Hunt."),
                component("act_raid", "heading", "text" to "Raid"),
                component("act_raid_t", "text", "text" to "Xem raid đang có, tham gia và hoàn thành raid để tiếp tục progression."),
                commandRun("act_raids", "/raid list", "Xem danh sách raid."),
                component("act_comp", "heading", "text" to "Battle Tower, Ranked và PvP"),
                component("act_comp_t", "text", "text" to "Đây là phần combat sau PvE. Trainer Chapter I được cấu hình Lv.100 với IV/EV build, Tera và có thể dùng Mega; đừng vào với party chưa chuẩn bị."),
                component("act_other", "heading", "text" to "Research, Expedition, Showcase"),
                component("act_other_t", "text", "text" to "Guided campaign còn dẫn qua Research task, Expedition progress và Showcase progress. Đây là các nhánh progression riêng, không cần làm tất cả ngay ngày đầu."),
                component("act_daily", "heading", "text" to "Daily Rewards & Battle Pass"),
                commandRun("act_daily_cmd", "/dailyrewards", "Nhận/xem Daily Rewards."),
                commandRun("act_bp_cmd", "/battlepass", "Mở Battle Pass.")
            )
        )

        val faq = HubPage(
            id = "faq",
            route = "faq",
            category = "guide",
            title = LocalizedText.of("Câu hỏi nhanh"),
            subtitle = LocalizedText.of("Các câu hỏi player mới thường cần câu trả lời ngay."),
            tags = listOf("faq", "help", "heal", "warp", "home", "claim", "trade"),
            theme = "clean_dark",
            components = listOf(
                component("faq_heal_h", "heading", "text" to "Hồi máu Pokémon ở đâu?"),
                component("faq_heal_t", "text", "text" to "Pokémon Center hồi máu miễn phí."),
                component("faq_travel_h", "heading", "text" to "Đi khu khác bằng gì?"),
                component("faq_travel_t", "text", "text" to "Dùng /warps cho các điểm đến của server. Dùng /homes cho các vị trí cá nhân bạn đã lưu."),
                component("faq_buy_h", "heading", "text" to "Mua Pokémon ở đâu?"),
                component("faq_buy_t", "text", "text" to "Dùng /gts để mua Pokémon từ listing của player. Key cho Gacha nằm ở /keyshop."),
                component("faq_random_h", "heading", "text" to "Wonder Trade là gì?"),
                component("faq_random_t", "text", "text" to "Bạn đưa một Pokémon và nhận lại một Pokémon ngẫu nhiên. Giao dịch là cuối cùng."),
                component("faq_sts_h", "heading", "text" to "STS dùng để làm gì?"),
                component("faq_sts_t", "text", "text" to "STS mua Pokémon bạn không còn dùng. Việc bán là cuối cùng; kiểm tra trước khi xác nhận."),
                component("faq_claim_h", "heading", "text" to "Claim đất thế nào?"),
                component("faq_claim_t", "text", "text" to "Bắt đầu bằng /flan menu. Dùng /trust <tên> và /untrust <tên> để quản lý người được phép trong claim."),
                component("faq_commands_h", "heading", "text" to "Quên lệnh thì sao?"),
                component("faq_commands_t", "text", "text" to "Dùng /lenh hoặc mở mục Lệnh người chơi trong SV Hub.")
            )
        )

        val updates = HubPage(
            id = "updates",
            route = "updates",
            category = "updates",
            title = LocalizedText.of("Cập nhật"),
            subtitle = LocalizedText.of("Thông báo thay đổi gameplay dành cho người chơi."),
            tags = listOf("updates", "changelog", "news", "cập nhật"),
            theme = "clean_dark",
            components = listOf(
                component("updates_empty", "text", "text" to "Chưa có thông báo mới được đăng trong SV Hub.")
            )
        )

        return HubContent(
            schema = HUB_SCHEMA_VERSION,
            revision = 0L,
            defaultLocale = "vi_vn",
            defaultTheme = "clean_dark",
            themes = themes,
            assets = emptyMap(),
            pages = listOf(home, howToPlay, quickCommands, commands, pokemonShop, pokemon, fakemon, activities, faq, updates),
            cobblemonWiki = CobblemonWikiConfig(
                autoPokemon = true,
                autoFakemonNamespaces = true,
                fakemonNamespaces = setOf("fakemon", "server", "svmon")
            )
        )
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

    /** Commands with required arguments are copied instead of executed with invalid placeholders. */
    private fun commandTemplate(id: String, command: String, description: String): HubComponent = HubComponent(
        id = id,
        type = "command_card",
        props = json("command" to command, "description" to "$description Nhấn để copy mẫu lệnh."),
        action = HubActionSpec("$id:copy", "copy_text", command, cooldownMs = 0L)
    )

    private fun table(id: String, rows: List<List<String>>): HubComponent {
        val array = JsonArray()
        rows.forEach { row ->
            array.add(JsonArray().also { out -> row.forEach(out::add) })
        }
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
