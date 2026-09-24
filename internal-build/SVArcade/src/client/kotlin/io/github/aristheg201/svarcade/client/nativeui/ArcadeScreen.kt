package io.github.aristheg201.svarcade.client.nativeui

import com.google.gson.JsonObject
import io.github.aristheg201.svarcade.ui.UiRect
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
    private val path get() = FabricLoader.getInstance().configDir.resolve("svarcade-presentation.properties")
    var animation = runCatching { !Files.readString(path).contains("animation=false") }.getOrDefault(true)
        private set
    fun toggle() { animation = !animation; runCatching { Files.writeString(path, "animation=$animation\n") } }
    var storeKind = "ARENA"
}

inline fun <T> GuiGraphics.withArcadeScissor(rect: UiRect, draw: () -> T): T {
    val matrix = pose().last().pose()
    val first = matrix.transformPosition(org.joml.Vector3f(rect.x.toFloat(), rect.y.toFloat(), 0f))
    val last = matrix.transformPosition(org.joml.Vector3f(rect.right.toFloat(), rect.bottom.toFloat(), 0f))
    enableScissor(kotlin.math.floor(first.x).toInt(), kotlin.math.floor(first.y).toInt(), kotlin.math.ceil(last.x).toInt(), kotlin.math.ceil(last.y).toInt())
    return try { draw() } finally { disableScissor() }
}

/** Full viewport composition with actual, server-backed controls and local presentation state. */
class ArcadeScreen {
    var page: ArcadePage = ArcadeHomeScreen()
    var selected = "chess"
    private var difficulty = "bot_normal"
    private var clock = "10+5"
    private var cardOffset = 0
    data class Hooks(val hit: (UiRect, String, () -> Unit) -> Unit, val intent: (String, JsonObject) -> Unit, val open: (String) -> Unit)
    fun render(gui: GuiGraphics, font: Font, width: Int, height: Int, state: JsonObject, mx: Int, my: Int, hooks: Hooks) {
        val theme = ArcadeUiTheme
        fun label(value:String,x:Int,y:Int,w:Int,color:Int=theme.text,heading:Boolean=false,scale:Float=1f) {
            if(w<=0)return
            gui.withArcadeScissor(UiRect(x,y-2,w,(19*scale).toInt())) {
                gui.pose().pushPose()
                try { gui.pose().translate(x.toFloat(),y.toFloat(),0f);gui.pose().scale(scale,scale,1f);gui.drawString(font,ArcadeUiPainter.text(value,heading),0,0,color,false) }
                finally { gui.pose().popPose() }
            }
        }
        fun panel(r:UiRect,border:Int=theme.edge)=ArcadeUiPainter.frame(gui,r,border)
        fun button(r:UiRect,value:String,active:Boolean=false,primary:Boolean=false,action:()->Unit) {
            val hover=r.contains(mx.toDouble(),my.toDouble())
            if(primary) {
                gui.fillGradient(r.x+2,r.y+2,r.right-2,r.bottom-2,if(hover)0xFFC39B48.toInt() else 0xFF987235.toInt(),0xFF513919.toInt())
                ArcadeUiPainter.frame(gui,r,theme.gold,false)
                ArcadeUiPainter.frame(gui,r.inset(3),0xFFB69957.toInt(),false)
            } else {
                gui.fillGradient(r.x+1,r.y+1,r.right-1,r.bottom-1,if(active||hover)0xFF174456.toInt() else 0xFF102C3B.toInt(),0xFF061B29.toInt())
                ArcadeUiPainter.frame(gui,r,if(active||hover)theme.cyan else theme.edge,false)
            }
            val component=ArcadeUiPainter.text(value,primary)
            val textWidth=font.width(component)
            label(value,r.x+(r.width-textWidth).coerceAtLeast(12)/2,r.y+(r.height-11)/2,r.width-10,if(active||primary)0xFFFFE5A7.toInt() else theme.text,primary)
            hooks.hit(r,value,action)
        }
        fun art(r:UiRect,id:String) {
            val name=if(id=="pokecards") "pokedraft" else id
            val texture=ResourceLocation.fromNamespaceAndPath("svarcade","textures/gui/arcade/hero_$name.png")
            val h=minOf(r.height,r.width*324/868)
            gui.blit(texture,r.x,r.y,r.width,h,0f,0f,868,324,868,324)
        }
        fun paragraph(value:String,r:UiRect,color:Int=theme.muted) {
            gui.withArcadeScissor(UiRect(r.x,r.y-4,r.width,r.height+6)) { font.split(ArcadeUiPainter.text(value),r.width).take(r.height/14).forEachIndexed { i,line -> gui.drawString(font,line,r.x,r.y+i*14,color,false) } }
        }
        val order=listOf("chess","xiangqi","ludo","tower_defense","tft","uno","pokecards")
        val games=state.getAsJsonArray("games")?.map { it.asJsonObject }?.sortedBy { order.indexOf(it.str("id")) }.orEmpty()
        val game=games.find { it.str("id")==selected } ?: games.firstOrNull()
        if(game!=null)selected=game.str("id")
        fun detail(id:String) {selected=id;page=ArcadeGameDetailScreen(id)}
        fun ranks(r:UiRect,history:Boolean=false,leaderboard:Boolean=false) {
            panel(r)
            ArcadeUiPainter.icon(gui,if(history)"clock" else "ranked",r.x+12,r.y+10,15,theme.muted)
            label(if(leaderboard)"LEADERBOARD" else if(history)"RECENT MATCHES" else "YOUR RANK",r.x+37,r.y+11,r.width-48,theme.gold,true)
            gui.fill(r.x+12,r.y+33,r.right-12,r.y+34,0xFF203F4D.toInt())
            val ranking=state.getAsJsonObject("ranking") ?: JsonObject()
            var y=r.y+43
            games.forEach { g ->
                val rank=ranking.getAsJsonObject(g.str("id")) ?: return@forEach
                if(history||leaderboard) {
                    rank.getAsJsonArray(if(leaderboard)"leaderboard" else "history")?.take(3)?.forEach { entry ->
                        if(y+30<r.bottom) { label(g.str("title"),r.x+14,y,r.width-28);label(entry.asString,r.x+14,y+14,r.width-28,theme.cyan);y+=36 }
                    }
                } else if(y+24<r.bottom) {
                    val short=when(g.str("id")){"chess"->"Chess";"xiangqi"->"Xiangqi";"ludo"->"Ludo";"tft"->"TFT";else->g.str("title")}
                    ArcadeUiPainter.icon(gui,"badge",r.x+14,y-1,18,theme.gold)
                    label(short,r.x+42,y,r.width/3)
                    val tier=rank.str("tier","Unranked")
                    label(tier,r.x+r.width/2,y,r.width/2-55,theme.muted)
                    label("${rank.num("rating")} LP",r.right-48,y,40,theme.text)
                    gui.fill(r.x+13,y+23,r.right-13,y+24,0x552E5263);y+=32
                }
            }
            if(y==r.y+43) paragraph(if(leaderboard)"Complete a ranked match to join the leaderboard." else if(history)"Your next match starts a new story." else "Your rank will appear here.",UiRect(r.x+14,y,r.width-28,minOf(42,r.bottom-y-10)))
        }
        gui.fill(0,0,width,height,0xFF030D16.toInt())
        val environment=ResourceLocation.fromNamespaceAndPath("svarcade","textures/gui/arcade/hero_environment.png")
        gui.blit(environment,0,0,width,height,0f,0f,2172,724,2172,724)
        gui.fill(0,0,width,height,0xE8031521.toInt())
        gui.fillGradient(0,0,width,46,0xE50C202D.toInt(),0xFF03111C.toInt())
        gui.fill(12,45,width-12,46,0xFF8F7740.toInt())
        ArcadeUiPainter.icon(gui,"brand",16,7,29,theme.gold)
        label("SV ARCADE",58,14,160,theme.gold,true,1.35f)
        val brandW=220
        val navW=minOf(83,(width-brandW-40)/5)
        listOf("HOME","PLAY","RANKED","PROFILE","SETTINGS").forEachIndexed { i,title ->
            val x=brandW+i*navW
            val active=when(i){0->page is ArcadeHomeScreen;1->page is ArcadeGameDetailScreen;2->page is ArcadeRankedScreen;3->page is ArcadeProfileScreen;else->page is ArcadeSettingsScreen}
            val r=UiRect(x,4,navW,41)
            if(active||r.contains(mx.toDouble(),my.toDouble()))gui.fillGradient(x,5,x+navW,44,0x053CD3DD,0x554B665B)
            val tw=font.width(ArcadeUiPainter.text(title,true))
            label(title,x+(navW-tw)/2,18,navW-3,if(active)0xFFFFE4A0.toInt() else theme.text,true)
            if(active) {gui.fill(x+5,43,x+navW-5,45,theme.gold);ArcadeUiPainter.line(gui,x+navW/2-3,42,x+navW/2,39,theme.gold);ArcadeUiPainter.line(gui,x+navW/2,39,x+navW/2+3,42,theme.gold)}
            hooks.hit(r,title) {page=when(i){0->ArcadeHomeScreen();1->ArcadeGameDetailScreen(selected);2->ArcadeRankedScreen();3->ArcadeProfileScreen();else->ArcadeSettingsScreen()}}
        }
        if(width>920)label("PLAY  ·  COMPETE  ·  ENJOY",width-200,19,184,0xFF6C8A99.toInt(),false,.8f)
        val utility=listOf("GACHA" to "gacha","SKINS" to "skins","STORE" to "store","ARENA" to "store","TACTICIANS" to "store","WALLET" to "wallet")
        val utilityW=90
        label("THE ARCADE COLLECTION",18,60,(width-utilityW*6-40).coerceAtLeast(0),theme.muted,true,.8f)
        utility.forEachIndexed { i,(title,module) ->button(UiRect(width-18-(6-i)*utilityW,52,utilityW-5,24),title) {ArcadePresentation.storeKind=if(title=="TACTICIANS")"TACTICIAN" else "ARENA";hooks.open(module)} }
        val area=UiRect(18,88,width-36,height-106)
        if(game==null){label("Connecting to the Arcade...",area.x,area.y,area.width);return}
        val sideW=if(width>=860)(area.width*28/100).coerceIn(235,290) else 0
        val main=UiRect(area.x,area.y,area.width-if(sideW>0)sideW+12 else 0,area.height)
        val side=UiRect(main.right+12,area.y,sideW,area.height)
        val description=when(selected){"tft"->"Draft your team, build powerful traits and evolve your lineup. Eight players. One champion.";"chess"->"Build your strategy, outthink your opponent, and command your favorite Pokemon on the chessboard.";"xiangqi"->"Cross the river, defend your general and take control of the board.";"ludo"->"Roll the dice, race your rivals and bring your Pokemon home.";"tower_defense"->"Build your defense, upgrade your team and hold back the next wave.";"uno"->"Match colors, play your hand and turn the table.";else->"Draft cards, discover combinations and build a winning strategy."}
        val subtitle=when(selected){"chess"->"Strategic 1v1 board battles";"xiangqi"->"A timeless battle of strategy";"ludo"->"A race worth rolling for";"tower_defense"->"Defend and evolve";"tft"->"Build a team. Outscale the lobby.";"uno"->"Classic cards. Endless rivalries.";else->"Collect and build"}
        fun hero(r:UiRect,home:Boolean) {
            gui.withArcadeScissor(r) {
                gui.blit(environment,r.x,r.y,r.width,r.height,0f,0f,2172,724,2172,724)
                // Local gradient keeps live type readable without baking any UI into artwork.
                for(i in 0 until r.width*55/100) {val a=(230*(1f-i.toFloat()/(r.width*.55f))).toInt().coerceIn(0,230);gui.fill(r.x+i,r.y,r.x+i+1,r.bottom,(a shl 24) or 0x031522)}
                val compact=r.height<210
                val textW=r.width*48/100
                label(if(home)"FEATURED GAME" else "READY TO PLAY",r.x+20,r.y+if(compact)10 else 17,textW-20,theme.gold,true,.8f)
                val titleScale=if(compact)1.45f else 1.85f
                val titleLines=font.split(ArcadeUiPainter.text(game.str("title"),true),(textW/titleScale).toInt())
                gui.pose().pushPose()
                try {gui.pose().translate((r.x+20).toFloat(),(r.y+if(compact)30 else 42).toFloat(),0f);gui.pose().scale(titleScale,titleScale,1f);titleLines.take(2).forEachIndexed { i,line ->gui.drawString(font,line,0,i*14,0xFFFFDDA0.toInt(),false)}} finally{gui.pose().popPose()}
                val textY=r.y+(if(compact)36 else 48)+minOf(2,titleLines.size)*(if(compact)20 else 26)
                paragraph(subtitle,UiRect(r.x+20,textY,textW-24,30),theme.text)
                paragraph(description,UiRect(r.x+20,textY+if(compact)22 else 32,textW-30,(r.bottom-textY-if(home)65 else 44).coerceAtLeast(14)))
                if(home)button(UiRect(r.x+20,r.bottom-36,minOf(172,textW-28),27),"PLAY  >",primary=true){detail(selected)}
                val species=when(selected){"tft"->"eevee";"ludo"->"rapidash";"xiangqi"->"lucario";else->"pikachu"}
                val view=io.github.aristheg201.svarcade.client.cobblemon.PokemonView(key="arcade-featured:$species",route="",speciesId="cobblemon:$species",aspects=emptySet(),displayName=species,dexNumber=0,fakemon=false)
                val rendered=io.github.aristheg201.svarcade.client.cobblemon.PokemonModelRenderer.render(gui,view,r.x+r.width*72/100,r.y+r.height*25/100,(r.height*1.15f).toInt(),25f,1.7f,12f)
                if(!rendered)art(UiRect(r.x+r.width*54/100,r.y+r.height/3,r.width*43/100,r.height/2),selected)
            }
            ArcadeUiPainter.frame(gui,r,0xFF8B7648.toInt(),false)
        }
        fun gameCards(r:UiRect) {
            label("GAMES",r.x,r.y,r.width,theme.gold,true,1.08f)
            val count=(r.width/112).coerceIn(3,7).coerceAtMost(games.size)
            val cardW=(r.width-(count-1)*8)/count
            cardOffset=cardOffset.coerceIn(0,(games.size-count).coerceAtLeast(0))
            games.drop(cardOffset).take(count).forEachIndexed { i,g ->
                val box=UiRect(r.x+i*(cardW+8),r.y+24,cardW,r.height-25)
                panel(box,if(g.str("id")==selected)theme.cyan else theme.edge)
                art(UiRect(box.x+2,box.y+2,box.width-4,box.height-26),g.str("id"))
                gui.fillGradient(box.x+2,box.y+box.width*324/868-10,box.right-2,box.bottom-1,0x00102330,0xFF041724.toInt())
                val title=g.str("title")
                val first=if(g.str("id")=="tower_defense") title.substringBeforeLast(" ") else title
                label(first,box.x+7,box.bottom-31,box.width-14,theme.text,false,.78f)
                if(g.str("id")=="tower_defense")label("Defense",box.x+7,box.bottom-18,box.width-14,theme.muted,false,.75f)
                else label(when(g.str("id")){"chess","xiangqi"->"Board Strategy";"ludo"->"Board Game";"tft"->"Auto Battler";"uno"->"Card Game";else->"Collect and Build"},box.x+7,box.bottom-17,box.width-14,theme.muted,false,.68f)
                hooks.hit(box,g.str("title")){detail(g.str("id"))}
            }
            if(games.size>count){button(UiRect(r.right-60,r.y-2,26,20),"<"){cardOffset=(cardOffset-1).coerceAtLeast(0)};button(UiRect(r.right-28,r.y-2,26,20),">"){cardOffset=(cardOffset+1).coerceAtMost(games.size-count)}}
        }
        when(page) {
            is ArcadeHomeScreen -> {
                val cardH=117
                val sectionH=area.height-cardH-16
                val heroH=sectionH-94
                hero(UiRect(main.x,main.y,main.width,heroH),true)
                val modes=game.getAsJsonArray("modes")?.map { it.asString }.orEmpty()
                val choices=buildList{if("pvp" in modes)add("NORMAL" to "pvp");if("solo" in modes)add("SOLO" to "solo");if("ranked" in modes)add("RANKED" to "ranked");if(modes.any{it.startsWith("bot_")})add("VS AI" to (difficulty.takeIf{it in modes} ?: modes.first{it.startsWith("bot_")}))}
                val caps=game.getAsJsonObject("capabilities") ?: JsonObject()
                val timed=caps.bool("supports_time_control")
                val modeW=(main.width-(choices.size-1)*8)/choices.size.coerceAtLeast(1)
                choices.forEachIndexed { i,(name,mode) ->
                    val r=UiRect(main.x+i*(modeW+8),main.y+heroH+7,modeW,52)
                    panel(r,if(r.contains(mx.toDouble(),my.toDouble()))theme.cyan else theme.edge)
                    ArcadeUiPainter.icon(gui,if(mode=="ranked")"ranked" else if(mode.startsWith("bot"))"ai" else "normal",r.x+10,r.y+12,26,if(mode=="ranked")theme.gold else theme.cyan)
                    label(name,r.x+44,r.y+9,r.width-50,theme.text,true)
                    label(when(mode){"ranked"->"Compete and climb";"pvp"->"Play at your pace";"solo"->"Build your defense";else->"Practice your strategy"},r.x+44,r.y+29,r.width-50,theme.muted,false,.85f)
                    hooks.hit(r,name){hooks.intent("start",JsonObject().apply{addProperty("game",selected);addProperty("mode",mode);if(timed)addProperty("timeControl",clock)})}
                }
                val optionsY=main.y+heroH+66
                if(timed) {
                    label("TIME CONTROL",main.x+5,optionsY+7,100,theme.muted,true,.8f)
                    caps.getAsJsonArray("timeControls")?.forEachIndexed{i,value->button(UiRect(main.x+104+i*54,optionsY,50,25),value.asString,clock==value.asString){clock=value.asString}}
                }
                if(caps.bool("supports_animation"))button(UiRect(main.right-176,optionsY,176,25),"Animation: ${if(ArcadePresentation.animation)"ON" else "OFF"}",ArcadePresentation.animation){ArcadePresentation.toggle()}
                gameCards(UiRect(area.x,area.bottom-cardH,area.width,cardH))
                if(sideW>0){val top=minOf(188,sectionH*68/100);ranks(UiRect(side.x,side.y,side.width,top));ranks(UiRect(side.x,side.y+top+10,side.width,sectionH-top-10),true)}
            }
            is ArcadeGameDetailScreen -> {
                val caps=game.getAsJsonObject("capabilities") ?: JsonObject()
                val timed=caps.bool("supports_time_control")
                val heroH=main.height-if(timed)177 else 150
                hero(UiRect(main.x,main.y,main.width,heroH),false)
                val modes=game.getAsJsonArray("modes")?.map { it.asString }.orEmpty()
                val choices=buildList{if("pvp" in modes)add("NORMAL" to "pvp");if("solo" in modes)add("SOLO" to "solo");if("ranked" in modes)add("RANKED" to "ranked");if(modes.any{it.startsWith("bot_")})add("VS AI" to (difficulty.takeIf{it in modes} ?: modes.first{it.startsWith("bot_")}))}
                var y=main.y+heroH+10
                val modeW=(main.width-(choices.size-1)*8)/choices.size.coerceAtLeast(1)
                choices.forEachIndexed { i,(name,mode) ->
                    val r=UiRect(main.x+i*(modeW+8),y,modeW,65)
                    panel(r,if(r.contains(mx.toDouble(),my.toDouble()))theme.cyan else theme.edge)
                    ArcadeUiPainter.icon(gui,if(mode=="ranked")"ranked" else if(mode.startsWith("bot"))"ai" else "normal",r.x+12,r.y+19,27,if(mode=="ranked")theme.gold else theme.cyan)
                    label(name,r.x+51,r.y+13,r.width-58,theme.text,true)
                    paragraph(when(mode){"ranked"->"Compete and climb the leaderboard.";"pvp"->"Casual games, play at your pace.";"solo"->"Build a defense of your own.";else->"Practice against AI at your skill level."},UiRect(r.x+51,r.y+31,r.width-59,29))
                    hooks.hit(r,name){hooks.intent("start",JsonObject().apply{addProperty("game",selected);addProperty("mode",mode);if(timed)addProperty("timeControl",clock)})}
                }
                y+=76
                if(caps.bool("supports_difficulty")) {
                    label("AI DIFFICULTY",main.x+5,y+4,108,theme.muted,true,.8f)
                    val options=modes.filter{it.startsWith("bot_")};val w=minOf(78,(main.width-116)/options.size.coerceAtLeast(1))
                    options.forEachIndexed{i,mode->button(UiRect(main.x+116+i*(w+4),y,w,22),mode.removePrefix("bot_").replaceFirstChar{it.uppercase()},difficulty==mode){difficulty=mode}};y+=30
                }
                if(timed){
                    label("TIME CONTROL",main.x+5,y+4,108,theme.muted,true,.8f)
                    val clocks=caps.getAsJsonArray("timeControls")?.map{it.asString}.orEmpty();val w=minOf(78,(main.width-116)/clocks.size.coerceAtLeast(1))
                    clocks.forEachIndexed{i,value->button(UiRect(main.x+116+i*(w+4),y,w,22),value,clock==value){clock=value}};y+=30
                }
                if(caps.bool("supports_animation"))button(UiRect(main.x+4,y,minOf(220,main.width-8),25),"Game Animation: ${if(ArcadePresentation.animation)"ON" else "OFF"}",ArcadePresentation.animation){ArcadePresentation.toggle()}
                if(game.bool("queued"))button(UiRect(main.right-210,main.bottom-25,210,23),"Searching for players - Cancel"){hooks.intent("cancel_queue",JsonObject())}
                if(sideW>0){ranks(UiRect(side.x,side.y,side.width,side.height*55/100));ranks(UiRect(side.x,side.y+side.height*55/100+10,side.width,side.height*45/100-10),true)}
            }
            is ArcadeRankedScreen->{ranks(main,leaderboard=true);if(sideW>0)ranks(side)}
            is ArcadeProfileScreen->{ranks(main,true);if(sideW>0)ranks(side)}
            is ArcadeSettingsScreen->{panel(main);label("PRESENTATION",main.x+20,main.y+22,main.width-40,theme.gold,true,1.3f);button(UiRect(main.x+20,main.y+60,260,30),"Game Animation: ${if(ArcadePresentation.animation)"ON" else "OFF"}",ArcadePresentation.animation){ArcadePresentation.toggle()};paragraph("Animation changes how moves look on this client. The server always controls the match.",UiRect(main.x+20,main.y+112,main.width-40,60))}
        }
        if(state.str("activeSession").isNotBlank())button(UiRect(width-218,height-28,200,23),"RESUME ACTIVE MATCH",true){hooks.intent("resume",JsonObject())}
    }
}

private fun JsonObject.str(key: String, fallback: String = "") = runCatching { get(key)?.asString ?: fallback }.getOrDefault(fallback)
private fun JsonObject.num(key: String) = runCatching { get(key)?.asInt ?: 0 }.getOrDefault(0)
private fun JsonObject.bool(key: String) = runCatching { get(key)?.asBoolean ?: false }.getOrDefault(false)
