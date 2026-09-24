from pathlib import Path
p=Path('src/client/kotlin/io/github/aristheg201/svhub/client/nativeui/ArcadeScreen.kt')
p.write_text('''package io.github.aristheg201.svhub.client.nativeui

import com.google.gson.JsonObject
import io.github.aristheg201.svhub.ui.UiRect
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Files

sealed interface ArcadePage
class ArcadeHomeScreen : ArcadePage
class ArcadeGameDetailScreen(val game: String) : ArcadePage
class ArcadeRankedScreen : ArcadePage
class ArcadeProfileScreen : ArcadePage
class ArcadeSettingsScreen : ArcadePage

object ArcadeUiTheme {
    val background = 0xFF071622.toInt()
    val panel = 0xFF102B3B.toInt()
    val edge = 0xFF2E5263.toInt()
    val text = 0xFFEAF2F1.toInt()
    val muted = 0xFFA0B8C1.toInt()
    val cyan = 0xFF50CAD5.toInt()
    val gold = 0xFFE3BF69.toInt()
}

object ArcadePresentation {
    private val path get() = FabricLoader.getInstance().configDir.resolve("svhub-presentation.properties")
    var animation = runCatching { !Files.readString(path).contains("animation=false") }.getOrDefault(true)
        private set
    fun toggle() { animation = !animation; runCatching { Files.writeString(path, "animation=$animation\\n") } }
    var storeKind = "ARENA"
}

inline fun <T> GuiGraphics.withArcadeScissor(rect: UiRect, draw: () -> T): T {
    enableScissor(rect.x, rect.y, rect.right, rect.bottom)
    return try { draw() } finally { disableScissor() }
}

/** Full viewport composition with actual, server-backed controls and local presentation state. */
class ArcadeScreen {
    var page: ArcadePage = ArcadeHomeScreen()
    var selected = "tft"
    private var difficulty = "bot_normal"
    private var clock = "10+5"
    private var cardOffset = 0
    data class Hooks(val hit: (UiRect, () -> Unit) -> Unit, val intent: (String, JsonObject) -> Unit, val open: (String) -> Unit)
    fun render(gui: GuiGraphics, font: Font, width: Int, height: Int, state: JsonObject, mx: Int, my: Int, hooks: Hooks) {
        val theme = ArcadeUiTheme
        fun label(value: String, x: Int, y: Int, w: Int, color: Int = theme.text) {
            gui.drawString(font, font.plainSubstrByWidth(value, w.coerceAtLeast(1)), x, y, color, false)
        }
        fun panel(r: UiRect, border: Int = theme.edge) {
            gui.fill(r.x,r.y,r.right,r.bottom,border)
            gui.fill(r.x+1,r.y+1,r.right-1,r.bottom-1,theme.panel)
        }
        fun button(r: UiRect, value: String, active: Boolean = false, action: () -> Unit) {
            panel(r,if(active || r.contains(mx.toDouble(),my.toDouble())) theme.cyan else theme.edge)
            val shown=font.plainSubstrByWidth(value,(r.width-12).coerceAtLeast(1))
            gui.drawString(font,shown,r.x+(r.width-font.width(shown))/2,r.y+(r.height-8)/2,if(active)theme.gold else theme.text,false)
            hooks.hit(r,action)
        }
        fun art(r: UiRect, id: String) {
            val name=if(id=="pokecards") "pokedraft" else id
            val texture=ResourceLocation.fromNamespaceAndPath("svhub","textures/gui/arcade/hero_$name.png")
            // Preserve the artwork's native aspect ratio; UI labels are separate widgets.
            val h=minOf(r.height,r.width*324/868)
            gui.blit(texture,r.x,r.y+(r.height-h)/2,r.width,h,0f,0f,868,324,868,324)
        }
        fun paragraph(value: String, r: UiRect) {
            gui.withArcadeScissor(r) { font.split(Component.literal(value),r.width).take(r.height/13).forEachIndexed { i,line -> gui.drawString(font,line,r.x,r.y+i*13,theme.muted,false) } }
        }
        val games=state.getAsJsonArray("games")?.map { it.asJsonObject }.orEmpty()
        val game=games.find { it.str("id")==selected } ?: games.firstOrNull()
        if(game!=null) selected=game.str("id")
        fun detail(id: String) { selected=id;page=ArcadeGameDetailScreen(id) }
        fun ranks(r: UiRect, history: Boolean = false) {
            panel(r)
            label(if(history) "RECENT MATCHES" else "YOUR RANK",r.x+12,r.y+12,r.width-24,theme.gold)
            val ranking=state.getAsJsonObject("ranking") ?: JsonObject()
            var y=r.y+34
            games.forEach { g ->
                val rank=ranking.getAsJsonObject(g.str("id")) ?: return@forEach
                if(history) {
                    rank.getAsJsonArray("history")?.take(2)?.forEach { entry ->
                        if(y+24<r.bottom) { label(g.str("title"),r.x+12,y,r.width-24,theme.muted);label(entry.asString,r.x+12,y+12,r.width-24);y+=30 }
                    }
                } else if(y+26<r.bottom) {
                    label(g.str("title"),r.x+12,y,r.width-24)
                    label("${rank.str("tier","Unranked")}  ${rank.num("rating")} LP",r.x+12,y+12,r.width-24,theme.cyan);y+=32
                }
            }
            if(y==r.y+34) label(if(history) "No ranked games yet." else "Unranked",r.x+12,y,r.width-24,theme.muted)
        }
        gui.fill(0,0,width,height,theme.background)
        gui.fill(0,0,width,46,0xFF0B202E.toInt())
        gui.fill(0,45,width,46,theme.gold)
        val compact=width<650
        val brandWidth=if(compact) 98 else 174
        label("SV ARCADE",14,18,brandWidth-18,theme.gold)
        val tabs=listOf("HOME","PLAY","RANKED","PROFILE","SETTINGS")
        val navW=((width-brandWidth-12)/tabs.size).coerceAtLeast(32)
        tabs.forEachIndexed { i,title ->
            val active=when(i){0->page is ArcadeHomeScreen;1->page is ArcadeGameDetailScreen;2->page is ArcadeRankedScreen;3->page is ArcadeProfileScreen;else->page is ArcadeSettingsScreen}
            button(UiRect(brandWidth+i*navW,8,navW-4,30),title,active) { page=when(i){0->ArcadeHomeScreen();1->ArcadeGameDetailScreen(selected);2->ArcadeRankedScreen();3->ArcadeProfileScreen();else->ArcadeSettingsScreen()} }
        }
        val utility=listOf("GACHA" to "gacha","SKINS" to "skins","STORE" to "store","ARENA" to "store","TACTICIANS" to "store","WALLET" to "wallet")
        val utilityW=minOf(110,(width-28)/6)
        utility.forEachIndexed { i,(title,module) -> button(UiRect(width-14-(6-i)*utilityW,53,utilityW-4,22),title) { ArcadePresentation.storeKind=if(title=="TACTICIANS") "TACTICIAN" else "ARENA";hooks.open(module) } }
        val area=UiRect(14,88,width-28,(height-104).coerceAtLeast(100))
        if(game==null) { label("Waiting for the game catalog...",area.x,area.y,area.width);return }
        val sideW=if(width>=800) minOf(250,area.width/3) else 0
        val main=UiRect(area.x,area.y,area.width-if(sideW>0) sideW+12 else 0,area.height)
        val side=UiRect(main.right+12,area.y,sideW,area.height)
        val title=game.str("title")
        val description=when(selected) {
            "tft"->"Draft your team, build traits and evolve your lineup. Eight players. One champion."
            "chess"->"Every move matters. Outplay your opponent on a living Pokemon chessboard."
            "xiangqi"->"Cross the river, defend your general and take control of the board."
            "ludo"->"Roll the dice and race your Pokemon home."
            "tower_defense"->"Build your defense, upgrade your team and hold back the next wave."
            "uno"->"Match colors, play your hand and turn the table."
            else->"Draft cards and build a winning strategy."
        }
        fun hero(r: UiRect, home: Boolean) {
            panel(r,theme.edge)
            val textW=(r.width*45/100).coerceAtLeast(130)
            val artRect=UiRect(r.x+textW,r.y+8,r.width-textW-8,r.height-16)
            art(artRect,selected)
            label(if(home) "FEATURED GAME" else "SELECTED GAME",r.x+16,r.y+16,textW-30,theme.gold)
            paragraph(title,UiRect(r.x+16,r.y+40,textW-30,30))
            paragraph(description,UiRect(r.x+16,r.y+76,textW-30,(r.height-122).coerceAtLeast(26)))
            if(home) button(UiRect(r.x+16,r.bottom-38,minOf(150,textW-30),25),"PLAY",true) { detail(selected) }
        }
        when(page) {
            is ArcadeHomeScreen -> {
                val heroH=(main.height*56/100).coerceIn(160,260).coerceAtMost(main.height-126)
                hero(UiRect(main.x,main.y,main.width,heroH),true)
                val cardY=main.y+heroH+34
                label("GAMES",main.x,cardY-20,main.width,theme.gold)
                val count=(main.width/145).coerceIn(2,7).coerceAtMost(games.size)
                val cardW=(main.width-(count-1)*8)/count
                cardOffset=cardOffset.coerceIn(0,(games.size-count).coerceAtLeast(0))
                games.drop(cardOffset).take(count).forEachIndexed { i,g ->
                    val r=UiRect(main.x+i*(cardW+8),cardY,cardW,minOf(116,main.bottom-cardY-26))
                    panel(r,if(g.str("id")==selected) theme.cyan else theme.edge)
                    art(UiRect(r.x+3,r.y+3,r.width-6,r.height-38),g.str("id"))
                    gui.withArcadeScissor(r.inset(4)) { paragraph(g.str("title"),UiRect(r.x+8,r.bottom-32,r.width-16,28)) }
                    hooks.hit(r) { detail(g.str("id")) }
                }
                if(games.size>count) {
                    button(UiRect(main.right-108,main.bottom-22,50,20),"<") { cardOffset=(cardOffset-1).coerceAtLeast(0) }
                    button(UiRect(main.right-52,main.bottom-22,50,20),">") { cardOffset=(cardOffset+1).coerceAtMost(games.size-count) }
                }
                if(sideW>0) { val h=side.height*58/100;ranks(UiRect(side.x,side.y,side.width,h));ranks(UiRect(side.x,side.y+h+12,side.width,side.height-h-12),true) }
            }
            is ArcadeGameDetailScreen -> {
                val heroH=(main.height*48/100).coerceIn(145,230)
                hero(UiRect(main.x,main.y,main.width,heroH),false)
                val modes=game.getAsJsonArray("modes")?.map { it.asString }.orEmpty()
                val caps=game.getAsJsonObject("capabilities") ?: JsonObject()
                val choices=buildList { if("pvp" in modes)add("NORMAL" to "pvp");if("solo" in modes)add("SOLO" to "solo");if("ranked" in modes)add("RANKED" to "ranked");if(modes.any { it.startsWith("bot_") })add("VS AI" to (difficulty.takeIf { it in modes } ?: modes.first { it.startsWith("bot_") })) }
                var y=main.y+heroH+12
                val modeW=(main.width-(choices.size-1)*8)/choices.size.coerceAtLeast(1)
                choices.forEachIndexed { i,(name,mode) -> button(UiRect(main.x+i*(modeW+8),y,modeW,40),name) {
                    hooks.intent("start",JsonObject().apply { addProperty("game",selected);addProperty("mode",mode);if(caps.bool("supports_time_control"))addProperty("timeControl",clock) })
                } }
                y+=56
                if(caps.bool("supports_difficulty")) {
                    label("AI DIFFICULTY",main.x,y,110,theme.muted)
                    val options=modes.filter { it.startsWith("bot_") }
                    val w=((main.width-116)/options.size.coerceAtLeast(1)).coerceAtMost(100)
                    options.forEachIndexed { i,mode -> button(UiRect(main.x+116+i*w,y-5,w-4,22),mode.removePrefix("bot_").replaceFirstChar { it.uppercase() },difficulty==mode) { difficulty=mode } }
                    y+=32
                }
                if(caps.bool("supports_time_control")) {
                    label("TIME CONTROL",main.x,y,110,theme.muted)
                    val clocks=caps.getAsJsonArray("timeControls")?.map { it.asString }.orEmpty()
                    val w=((main.width-116)/clocks.size.coerceAtLeast(1)).coerceAtMost(100)
                    clocks.forEachIndexed { i,value -> button(UiRect(main.x+116+i*w,y-5,w-4,22),value,clock==value) { clock=value } };y+=32
                }
                if(caps.bool("supports_animation")) button(UiRect(main.x,y,minOf(220,main.width),24),"Game Animation: ${if(ArcadePresentation.animation) "ON" else "OFF"}",ArcadePresentation.animation) { ArcadePresentation.toggle() }
                if(game.bool("queued")) button(UiRect(main.x,main.bottom-26,main.width,24),"Searching for players - Cancel") { hooks.intent("cancel_queue",JsonObject()) }
                if(sideW>0) ranks(side)
            }
            is ArcadeRankedScreen -> { ranks(UiRect(main.x,main.y,main.width,main.height));if(sideW>0)ranks(side,true) }
            is ArcadeProfileScreen -> {
                ranks(main,true)
                if(sideW>0) ranks(side)
            }
            is ArcadeSettingsScreen -> {
                panel(main);label("PRESENTATION",main.x+18,main.y+20,main.width-36,theme.gold)
                button(UiRect(main.x+18,main.y+50,minOf(260,main.width-36),30),"Game Animation: ${if(ArcadePresentation.animation) "ON" else "OFF"}",ArcadePresentation.animation) { ArcadePresentation.toggle() }
                paragraph("Animation changes how moves look on this client. The server always controls the match.",UiRect(main.x+18,main.y+100,main.width-36,60))
            }
        }
        if(state.str("activeSession").isNotBlank()) button(UiRect(14,height-27,180,22),"RESUME ACTIVE MATCH",true) { hooks.intent("resume",JsonObject()) }
    }
}
''',encoding='utf-8')
p=Path('src/client/kotlin/io/github/aristheg201/svhub/client/nativeui/NativePlatformScreen.kt');s=p.read_text(encoding='utf-8');s=s.replace('private val storeUi = CosmeticStoreUi()','private val storeUi = CosmeticStoreUi().also { it.kind = ArcadePresentation.storeKind }\n    internal val arcadeUi = ArcadeScreen()')
s=s.replace('        currentGui = gui\n        val baseLayout', '''        currentGui = gui
        if (module == "arcade") {
            controls.clear(); sceneInputs.clear(); itemDropInputs.clear(); clip = null
            try {
                arcadeUi.render(gui, font, width, height, state, mouseX, mouseY, ArcadeScreen.Hooks(
                    hit = { rect, action -> addHit(rect, action = action) }, intent = ::intent, open = ::open))
                if (notice.isNotBlank()) gui.drawCenteredString(font, fit(notice, width-40), width/2, height-16, gold)
            } finally { currentGui = null }
            return
        }
        val baseLayout''')
s=s.replace('        when (module) {\n            "dashboard"', '        try { when (module) {\n            "dashboard"',1).replace('        gui.disableScissor(); clip = null','        } finally { gui.disableScissor(); clip = null }',1)
# Remove unreachable selector that invents fallback modes.
a=s.index('    private fun renderArcade(');b=s.index('    private fun renderCompanions(',a);s=s[:a]+s[b:];s=s.replace('            "arcade" -> renderArcade(gui, layout, mouseX, mouseY)\n','')
p.write_text(s,encoding='utf-8')
