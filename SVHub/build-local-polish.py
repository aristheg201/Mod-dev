from pathlib import Path
c=Path('src/client/kotlin/io/github/aristheg201/svhub/client/nativeui')
(c/'ArcadeUiPainter.kt').write_text('''package io.github.aristheg201.svhub.client.nativeui

import io.github.aristheg201.svhub.ui.UiRect
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.Font
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import kotlin.math.abs
import kotlin.math.roundToInt

/** Resolution-independent framing and typography, separate from artwork and live content. */
object ArcadeUiPainter {
    val body = ResourceLocation.fromNamespaceAndPath("svhub","notosans")
    val display = ResourceLocation.fromNamespaceAndPath("svhub","cinzel")
    fun text(value:String, heading:Boolean=false)=Component.literal(value).withStyle { it.withFont(if(heading)display else body) }
    fun line(gui:GuiGraphics,x0:Int,y0:Int,x1:Int,y1:Int,color:Int) {
        val steps=maxOf(abs(x1-x0),abs(y1-y0),1)
        for(i in 0..steps) { val x=x0+(x1-x0)*i/steps;val y=y0+(y1-y0)*i/steps;gui.fill(x,y,x+1,y+1,color) }
    }
    fun frame(gui:GuiGraphics,r:UiRect,color:Int=0xFF315562.toInt(),fill:Boolean=true) {
        if(fill) gui.fillGradient(r.x+1,r.y+1,r.right-1,r.bottom-1,0xF00D2939.toInt(),0xF0051824.toInt())
        val c=5
        line(gui,r.x+c,r.y,r.right-c,r.y,color);line(gui,r.x+c,r.bottom,r.right-c,r.bottom,color)
        line(gui,r.x,r.y+c,r.x,r.bottom-c,color);line(gui,r.right,r.y+c,r.right,r.bottom-c,color)
        line(gui,r.x,r.y+c,r.x+c,r.y,color);line(gui,r.right-c,r.y,r.right,r.y+c,color)
        line(gui,r.x,r.bottom-c,r.x+c,r.bottom,color);line(gui,r.right-c,r.bottom,r.right,r.bottom-c,color)
        val edge=0xFF8D7849.toInt()
        listOf(r.x to 1,r.right to -1).forEach { (x,d) ->
            line(gui,x+d*2,r.y+13,x+d*2,r.y+7,edge);line(gui,x+d*2,r.y+7,x+d*7,r.y+2,edge);line(gui,x+d*7,r.y+2,x+d*16,r.y+2,edge)
            line(gui,x+d*2,r.bottom-13,x+d*2,r.bottom-7,edge);line(gui,x+d*2,r.bottom-7,x+d*7,r.bottom-2,edge)
        }
    }
    fun icon(gui:GuiGraphics,id:String,x:Int,y:Int,size:Int,color:Int) {
        fun l(a:Int,b:Int,c:Int,d:Int)=line(gui,x+a*size/24,y+b*size/24,x+c*size/24,y+d*size/24,color)
        when(id) {
            "ranked" -> { l(2,6,6,19);l(6,19,18,19);l(18,19,22,6);l(22,6,16,12);l(16,12,12,3);l(12,3,8,12);l(8,12,2,6);l(6,22,18,22) }
            "ai" -> { l(5,7,19,7);l(19,7,21,18);l(21,18,12,22);l(12,22,3,18);l(3,18,5,7);l(12,7,12,1);l(7,12,9,14);l(15,14,17,12) }
            "normal" -> { l(3,2,20,21);l(2,3,19,22);l(3,21,20,2);l(4,22,21,3);l(2,17,7,22);l(17,22,22,17) }
            "clock" -> { l(8,2,16,2);l(16,2,22,8);l(22,8,22,16);l(22,16,16,22);l(16,22,8,22);l(8,22,2,16);l(2,16,2,8);l(2,8,8,2);l(12,6,12,12);l(12,12,17,15) }
            else -> { l(12,1,22,7);l(22,7,19,20);l(19,20,12,24);l(12,24,5,20);l(5,20,2,7);l(2,7,12,1);l(12,5,17,10);l(17,10,9,16);l(9,16,15,19) }
        }
    }
}
''',encoding='utf-8')
p=c/'ArcadeScreen.kt';s=p.read_text(encoding='utf-8');prefix=s[:s.index('/** Full viewport')];suffix=s[s.index('private fun JsonObject.str'):]
body='''/** Full viewport composition with actual, server-backed controls and local presentation state. */
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
            val texture=ResourceLocation.fromNamespaceAndPath("svhub","textures/gui/arcade/hero_$name.png")
            val h=minOf(r.height,r.width*324/868)
            gui.blit(texture,r.x,r.y,r.width,h,0f,0f,868,324,868,324)
        }
        fun paragraph(value:String,r:UiRect,color:Int=theme.muted) {
            gui.withArcadeScissor(r) { font.split(ArcadeUiPainter.text(value),r.width).take(r.height/14).forEachIndexed { i,line -> gui.drawString(font,line,r.x,r.y+i*14,color,false) } }
        }
        val games=state.getAsJsonArray("games")?.map { it.asJsonObject }.orEmpty()
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
            if(y==r.y+43) paragraph(if(leaderboard)"Complete a ranked match to join the leaderboard." else if(history)"Your next match starts a new story." else "Your rank will appear here.",UiRect(r.x+14,y,r.width-28,42))
        }
        gui.fill(0,0,width,height,0xFF030D16.toInt())
        val environment=ResourceLocation.fromNamespaceAndPath("svhub","textures/gui/arcade/hero_environment.png")
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
                val textW=r.width*48/100
                label(if(home)"FEATURED GAME" else "READY TO PLAY",r.x+20,r.y+17,textW-20,theme.gold,true,.8f)
                val titleLines=font.split(ArcadeUiPainter.text(game.str("title"),true),(textW/1.85f).toInt())
                gui.pose().pushPose()
                try {gui.pose().translate((r.x+20).toFloat(),(r.y+42).toFloat(),0f);gui.pose().scale(1.85f,1.85f,1f);titleLines.take(2).forEachIndexed { i,line ->gui.drawString(font,line,0,i*14,0xFFFFDDA0.toInt(),false)}} finally{gui.pose().popPose()}
                val textY=r.y+48+minOf(2,titleLines.size)*26
                paragraph(subtitle,UiRect(r.x+20,textY,textW-24,30),theme.text)
                paragraph(description,UiRect(r.x+20,textY+32,textW-30,(r.bottom-textY-if(home)88 else 44).coerceAtLeast(28)))
                if(home)button(UiRect(r.x+20,r.bottom-45,minOf(172,textW-28),31),"PLAY  >",primary=true){detail(selected)}
                val species=when(selected){"tft"->"eevee";"ludo"->"rapidash";"xiangqi"->"lucario";else->"pikachu"}
                val view=io.github.aristheg201.svhub.client.cobblemon.PokemonView(key="arcade-featured:$species",route="",speciesId="cobblemon:$species",aspects=emptySet(),displayName=species,dexNumber=0,fakemon=false)
                val rendered=io.github.aristheg201.svhub.client.cobblemon.PokemonModelRenderer.render(gui,view,r.x+r.width*72/100,r.y+r.height*76/100,(r.height*.84f).toInt(),155f,1f,12f)
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
                paragraph(g.str("title"),UiRect(box.x+7,box.bottom-29,box.width-14,28),theme.text)
                hooks.hit(box,g.str("title")){detail(g.str("id"))}
            }
            if(games.size>count){button(UiRect(r.right-60,r.y-2,26,20),"<"){cardOffset=(cardOffset-1).coerceAtLeast(0)};button(UiRect(r.right-28,r.y-2,26,20),">"){cardOffset=(cardOffset+1).coerceAtMost(games.size-count)}}
        }
        when(page) {
            is ArcadeHomeScreen -> {
                val cardH=117
                val heroH=area.height-cardH-16
                hero(UiRect(main.x,main.y,main.width,heroH),true)
                gameCards(UiRect(area.x,area.bottom-cardH,area.width,cardH))
                if(sideW>0){val top=heroH*60/100;ranks(UiRect(side.x,side.y,side.width,top));ranks(UiRect(side.x,side.y+top+10,side.width,heroH-top-10),true)}
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

'''
p.write_text(prefix+body+suffix,encoding='utf-8')
# Initialize title-screen species before the new real-model hero is rendered.
p=c/'VisualSmokeHarness.kt';s=p.read_text(encoding='utf-8').replace('        if (!enabled || !ArcadeAcceptanceHarness.complete || System.getenv("SVHUB_ARCADE_ONLY") == "1") return','        if (!enabled) return\n        prepareSyntheticSpecies()\n        if (!ArcadeAcceptanceHarness.complete || System.getenv("SVHUB_ARCADE_ONLY") == "1") return');p.write_text(s,encoding='utf-8')
p=c/'ArcadeAcceptanceHarness.kt';s=p.read_text(encoding='utf-8').replace('else "tft"','else "chess"');s=s.replace('                    add("ranking",JsonObject())','''                    add("ranking",JsonObject().also { ranks -> NativeArcadeService.games.filter { "ranked" in it.modes }.forEach { game -> ranks.add(game.id,JsonObject().apply { addProperty("tier","Unranked");addProperty("rating",0) }) } })''');p.write_text(s,encoding='utf-8')
