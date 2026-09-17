package io.github.aristheg201.svhub.client.nativeui

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.github.aristheg201.svhub.client.gui.SVHubScreen
import io.github.aristheg201.svhub.native.network.NativeIntentC2S
import io.github.aristheg201.svhub.ui.NativeLayout
import io.github.aristheg201.svhub.ui.UiDensity
import io.github.aristheg201.svhub.ui.UiRect
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.resources.language.I18n
import net.minecraft.network.chat.Component
import kotlin.math.min

class NativePlatformScreen(
    val module: String,
    private var state: JsonObject,
    private var notice: String
) : SVHubScreen(Component.translatable("screen.svhub.native")) {
    private data class Control(
        val rect: UiRect,
        val label: String,
        val icon: String? = null,
        val active: Boolean = false,
        val enabled: Boolean = true,
        val action: () -> Unit
    )

    private val gson = Gson()
    private val controls = mutableListOf<Control>()
    private var selectedCell: Int? = null
    private var selectedSkin: String? = null
    private var selectedCompanion: String? = null
    private val tftUi = TftUiState()
    private var boardRect = UiRect(0, 0, 0, 0)
    private var boardW = 0
    private var boardH = 0
    private var cellSize = 0
    private var moduleScroll = 0
    private var clip: UiRect? = null
    private var currentGui: GuiGraphics? = null

    private val background = 0xFF0A1114.toInt()
    private val panel = 0xFF111C20.toInt()
    private val panelAlt = 0xFF18272B.toInt()
    private val line = 0xFF2A3B3F.toInt()
    private val text = 0xFFF1F5F3.toInt()
    private val muted = 0xFF92A5A1.toInt()
    private val accent = 0xFF4CC7B2.toInt()
    private val gold = 0xFFE2BE62.toInt()
    private val danger = 0xFFE36C5C.toInt()
    private val modVersion: String = FabricLoader.getInstance().getModContainer("svhub").map { it.metadata.version.friendlyString }.orElse("?")

    fun applyState(newState: JsonObject, message: String) {
        state = newState
        notice = message
    }

    override fun init() { controls.clear() }

    override fun render(gui: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        currentGui = gui
        val baseLayout = NativeLayout.resolve(width, height)
        val layout = if (module == "game") baseLayout.copy(
            navigation = UiRect(0, 0, 0, 0),
            content = UiRect(8, 42, (width - 16).coerceAtLeast(144), (height - 50).coerceAtLeast(70)),
            verticalNavigation = false
        ) else baseLayout
        controls.clear()
        drawBackground(gui)
        drawHeader(gui, layout, mouseX, mouseY)
        if (module != "game") drawNavigation(gui, layout, mouseX, mouseY)
        gui.fill(layout.content.x, layout.content.y, layout.content.right, layout.content.bottom, panel)
        gui.fill(layout.content.x, layout.content.y, layout.content.right, layout.content.y + 1, line)
        clip = layout.content
        gui.enableScissor(layout.content.x, layout.content.y, layout.content.right, layout.content.bottom)
        when (module) {
            "dashboard" -> renderDashboard(gui, layout, mouseX, mouseY)
            "gacha" -> renderGacha(gui, layout, mouseX, mouseY)
            "skins" -> renderSkins(gui, layout, mouseX, mouseY)
            "arcade" -> renderArcade(gui, layout, mouseX, mouseY)
            "companions" -> renderCompanions(gui, layout, mouseX, mouseY)
            "wallet" -> renderWallet(gui, layout)
            "game" -> renderGame(gui, layout, mouseX, mouseY)
        }
        gui.disableScissor(); clip = null
        drawNotice(gui, layout)
        super.render(gui, mouseX, mouseY, partialTick)
        currentGui = null
    }

    private fun drawBackground(gui: GuiGraphics) {
        gui.fill(0, 0, width, height, background)
        var x = 0
        while (x < width) { gui.fill(x, 0, x + 1, height, 0x161E4542); x += 24 }
        var y = 0
        while (y < height) { gui.fill(0, y, width, y + 1, 0x161E4542); y += 24 }
    }

    private fun drawHeader(gui: GuiGraphics, layout: NativeLayout, mouseX: Int, mouseY: Int) {
        gui.fill(0, 0, width, layout.header.height, 0xFF101B1F.toInt())
        gui.fill(0, layout.header.bottom - 2, width, layout.header.bottom, accent)
        NativePixelArt.icon(gui, moduleIcon(module), 10, 9, 18, accent)
        gui.drawString(font, "SV HUB", 34, 8, text, true)
        gui.drawString(font, moduleTitle(module), 34, 20, muted, false)
        val close = UiRect(width - 31, 7, 23, 22)
        addControl(close, "×", mouseX, mouseY, action = ::onClose)
        gui.drawString(font, "v$modVersion", width - 78, 14, muted, false)
    }

    private fun drawNavigation(gui: GuiGraphics, layout: NativeLayout, mouseX: Int, mouseY: Int) {
        val entries = listOf(
            "dashboard" to "gui.svhub.nav.home", "gacha" to "gui.svhub.nav.gacha", "skins" to "gui.svhub.nav.skins",
            "arcade" to "gui.svhub.nav.arcade", "companions" to "gui.svhub.nav.arena"
        )
        if (layout.verticalNavigation) {
            gui.fill(layout.navigation.x, layout.navigation.y, layout.navigation.right, layout.navigation.bottom, 0xE6111C20.toInt())
            var y = layout.navigation.y + 7
            entries.forEach { (id, key) ->
                addControl(UiRect(layout.navigation.x + 5, y, layout.navigation.width - 10, 34), tr(key), mouseX, mouseY, id, id == module) { open(id) }
                y += 39
            }
            addControl(UiRect(layout.navigation.x + 5, layout.navigation.bottom - 39, layout.navigation.width - 10, 32), tr("gui.svhub.nav.wallet"), mouseX, mouseY, "wallet", module == "wallet") { open("wallet") }
        } else {
            val gap = 3; val itemW = (layout.navigation.width - gap * (entries.size - 1)) / entries.size
            entries.forEachIndexed { index, (id, key) ->
                val label = if (layout.navigation.width < 330) "" else tr(key)
                addControl(UiRect(layout.navigation.x + index * (itemW + gap), layout.navigation.y, itemW, layout.navigation.height), label, mouseX, mouseY, id, id == module) { open(id) }
            }
        }
    }

    private fun renderDashboard(gui: GuiGraphics, layout: NativeLayout, mouseX: Int, mouseY: Int) {
        val area = layout.content.inset(10); val wallet = state.getAsJsonObject("wallet")
        gui.drawString(font, tr("gui.svhub.dashboard.title"), area.x, area.y + 1, text, true)
        val backend = if (state.bool("skinBackendReady")) tr("gui.svhub.status.online") else tr("gui.svhub.status.offline")
        gui.drawString(font, "SkiesSkins $backend  •  ${state.num("ownedSkins")}/${state.num("skinTotal")}", area.x, area.y + 15, muted, false)
        val cards = listOf(
            Triple("gacha", tr("gui.svhub.nav.gacha"), "${wallet?.num("ticket") ?: 0} ${tr("gui.svhub.ticket")}"),
            Triple("skins", tr("gui.svhub.nav.skins"), "${state.num("ownedSkins")} ${tr("gui.svhub.owned")}"),
            Triple("arcade", tr("gui.svhub.nav.arcade"), "${state.num("gameCount")} ${tr("gui.svhub.games")}"),
            Triple("companions", tr("gui.svhub.nav.arena"), tr("gui.svhub.arena.ready")),
            Triple("wallet", tr("gui.svhub.nav.wallet"), "${wallet?.num("arcade") ?: 0} ${tr("gui.svhub.token")}")
        )
        val ultraCompact = area.height < 130
        val cols = when { ultraCompact -> 3; layout.density == UiDensity.WIDE -> 3; else -> 2 }
        val gap = if (ultraCompact) 4 else 7; val cardW = (area.width - gap * (cols - 1)) / cols
        val cardH = when { ultraCompact -> 23; area.height < 190 -> 46; else -> 58 }
        cards.forEachIndexed { index, (id, title, value) ->
            val row=index/cols; val col=index%cols
            val rect=UiRect(area.x+col*(cardW+gap),area.y+(if(ultraCompact)29 else 35)+row*(cardH+gap),cardW,cardH)
            drawModuleCard(gui,rect,id,title,value,mouseX,mouseY){open(id)}
        }
    }

    private fun renderGacha(gui:GuiGraphics,layout:NativeLayout,mouseX:Int,mouseY:Int){
        val area=layout.content.inset(10);val wallet=state.getAsJsonObject("wallet")
        gui.drawString(font,"${tr("gui.svhub.ticket")}: ${wallet?.num("ticket") ?: 0}",area.x,area.y,gold,true)
        val gap=8;val cols=if(area.width>=360)2 else 1;val bannerW=(area.width-gap*(cols-1))/cols
        listOf("hunter" to tr("gui.svhub.gacha.hunter"),"beast" to tr("gui.svhub.gacha.beast")).forEachIndexed{index,(id,title)->
            val rect=UiRect(area.x+(index%cols)*(bannerW+gap),area.y+24+(index/cols)*74-moduleScroll,bannerW,66)
            gui.fill(rect.x,rect.y,rect.right,rect.bottom,panelAlt);gui.fill(rect.x,rect.y,rect.x+4,rect.bottom,if(id=="hunter")accent else gold)
            NativePixelArt.icon(gui,"gacha",rect.x+12,rect.y+12,28,if(id=="hunter")accent else gold)
            gui.drawString(font,fit(title,rect.width-60),rect.x+48,rect.y+12,text,true);gui.drawString(font,tr("gui.svhub.gacha.cost"),rect.x+48,rect.y+29,muted,false)
            addControl(UiRect(rect.right-74,rect.bottom-23,66,17),tr("gui.svhub.roll"),mouseX,mouseY){intent("roll",json("banner" to id))}
        }
    }

    private fun renderSkins(gui:GuiGraphics,layout:NativeLayout,mouseX:Int,mouseY:Int){
        val area=layout.content.inset(8);val source=state.str("source","all");val page=state.num("page",0);val pages=state.num("pages",1)
        gui.drawString(font,"${tr("gui.svhub.owned")}: ${state.num("ownedCount")}  •  ${page+1}/$pages",area.x,area.y,muted,false)
        val tabs=listOf("all" to "gui.svhub.filter.all","dbz" to "DBZ","naruto" to "Naruto","pokelegends" to "PokeLegends")
        val tabGap=3;val tabW=(area.width-tabGap*(tabs.size-1))/tabs.size
        tabs.forEachIndexed{index,(id,label)->addControl(UiRect(area.x+index*(tabW+tabGap),area.y+18,tabW,20),if(label.startsWith("gui."))tr(label) else label,mouseX,mouseY,active=source==id){intent("source",json("source" to id,"page" to 0))}}
        val skins=state.getAsJsonArray("skins")?:JsonArray();val cols=when{area.width>=650->4;area.width>=430->3;else->2};val gap=5;val cellW=(area.width-gap*(cols-1))/cols;val cellH=36
        repeat(min(skins.size(),24)){i->val e=skins[i].asJsonObject;val row=i/cols;val col=i%cols;val rect=UiRect(area.x+col*(cellW+gap),area.y+45+row*(cellH+gap)-moduleScroll,cellW,cellH);val owned=e.bool("owned");gui.fill(rect.x,rect.y,rect.right,rect.bottom,if(selectedSkin==e.str("id"))0xFF21443E.toInt() else panelAlt);gui.fill(rect.x,rect.y,rect.x+3,rect.bottom,if(owned)accent else muted);gui.drawString(font,fit(e.str("name",e.str("id")),rect.width-14),rect.x+9,rect.y+8,text,false);gui.drawString(font,if(owned)tr("gui.svhub.owned") else e.str("rarity"),rect.x+9,rect.y+21,if(owned)accent else muted,false);addHit(rect){selectedSkin=e.str("id")}}
    }

    private fun renderArcade(gui:GuiGraphics,layout:NativeLayout,mouseX:Int,mouseY:Int){
        val area=layout.content.inset(8);gui.drawString(font,tr("gui.svhub.arcade.subtitle"),area.x,area.y,muted,false)
        val games=state.getAsJsonArray("games")?:return;val rowH=42;val modeLabels=linkedMapOf("bot_easy" to tr("gui.svhub.easy"),"bot_normal" to tr("gui.svhub.normal"),"bot_hard" to tr("gui.svhub.hard"),"pvp" to "PvP","solo" to tr("gui.svhub.solo"))
        for(i in 0 until games.size()){val game=games[i].asJsonObject;val id=game.str("id");val y=area.y+22+i*(rowH+5)-moduleScroll;val rect=UiRect(area.x,y,area.width,rowH);gui.fill(rect.x,rect.y,rect.right,rect.bottom,panelAlt);gui.fill(rect.x,rect.y,rect.x+4,rect.bottom,gameColor(id));NativePixelArt.icon(gui,id,rect.x+10,rect.y+8,26,gameColor(id));gui.drawString(font,game.str("title",id),rect.x+45,rect.y+9,text,true);gui.drawString(font,tr("gui.svhub.arcade.server_auth"),rect.x+45,rect.y+24,muted,false)
            val advertised=game.getAsJsonArray("modes")?.let{a->(0 until a.size()).map{a[it].asString}}.orEmpty();val modes=if(advertised.isEmpty())listOf("bot_easy","bot_normal","bot_hard","pvp") else advertised;var right=rect.right-6
            modes.asReversed().forEach{mode->val label=modeLabels[mode]?:mode;val w=(font.width(label)+14).coerceAtLeast(42);right-=w;addControl(UiRect(right,rect.y+10,w,22),label,mouseX,mouseY){intent("start",json("game" to id,"mode" to mode))};right-=4}
        }
    }

    private fun renderCompanions(gui:GuiGraphics,layout:NativeLayout,mouseX:Int,mouseY:Int){
        val area=layout.content.inset(8);val arena=state.getAsJsonObject("arena")
        if(layout.density==UiDensity.COMPACT&&arena!=null){renderCompanionsCompact(gui,area,arena,mouseX,mouseY);return}
        gui.drawString(font,tr("gui.svhub.arena.title"),area.x,area.y,text,true);gui.drawString(font,tr("gui.svhub.arena.subtitle"),area.x,area.y+14,muted,false)
    }

    private fun renderCompanionsCompact(gui:GuiGraphics,area:UiRect,arena:JsonObject,mouseX:Int,mouseY:Int){
        val mid=area.x+area.width/2;NativePixelArt.companion(gui,arena.str("playerId"),area.x+area.width/4,area.y+50,2,accent,gold,true);NativePixelArt.companion(gui,arena.str("enemyId"),area.x+area.width*3/4,area.y+50,2,danger,gold,false)
        gui.drawCenteredString(font,arena.str("playerName"),area.x+area.width/4,area.y+7,text);gui.drawCenteredString(font,arena.str("enemyName"),area.x+area.width*3/4,area.y+7,text)
        NativePixelArt.healthBar(gui,area.x+8,area.y+20,area.width/2-16,arena.num("playerHp"),arena.num("playerMaxHp"),accent);NativePixelArt.healthBar(gui,mid+8,area.y+20,area.width/2-16,arena.num("enemyHp"),arena.num("enemyMaxHp"),danger)
    }

    private fun renderWallet(gui:GuiGraphics,layout:NativeLayout){val area=layout.content.inset(10);val wallet=state.getAsJsonObject("wallet");drawBalance(gui,area.x,area.y,area.width,"wallet",tr("gui.svhub.token"),wallet?.num("arcade")?:0,accent);drawBalance(gui,area.x,area.y+44,area.width,"gacha",tr("gui.svhub.ticket"),wallet?.num("ticket")?:0,gold)}

    private fun renderGame(gui:GuiGraphics,layout:NativeLayout,mouseX:Int,mouseY:Int){
        val view=state.getAsJsonObject("view")?:return;val gameId=view.str("gameId");if(gameId=="tft"){boardRect=UiRect(0,0,0,0);boardW=0;boardH=0;TftGameRenderer.render(gui,font,layout.content,NativeLayout.resolve(width,height).density,view,mouseX,mouseY,tftUi,::addHit,::gameAct);return}
        val area=layout.content.inset(8);gui.drawString(font,view.str("title",tr("gui.svhub.game")),area.x,area.y,text,true);gui.drawString(font,fit(view.str("status"),area.width-8),area.x,area.y+13,gold,false)
        val actions=view.getAsJsonArray("actions");val cards=view.getAsJsonArray("cards");boardW=view.num("boardWidth");boardH=view.num("boardHeight");val actionWidth=if(area.width>=520)110 else 0;val sideActions=actionWidth>0;val availableBoardWidth=area.width-if(sideActions)actionWidth+8 else 0;val bottomReserve=if(cards!=null&&cards.size()>0)58 else 8;val boardTop=area.y+30;val boardAvailableHeight=(area.bottom-bottomReserve-boardTop).coerceAtLeast(30)
        if(boardW>0&&boardH>0){cellSize=min(30,min((availableBoardWidth/boardW).coerceAtLeast(12),(boardAvailableHeight/boardH).coerceAtLeast(12)));boardRect=UiRect(area.x,boardTop,cellSize*boardW,cellSize*boardH);val board=view.getAsJsonArray("board")?:JsonArray();repeat(boardH){r->repeat(boardW){c->val index=r*boardW+c;val x=boardRect.x+c*cellSize;val y=boardRect.y+r*cellSize;val cellColor=if((r+c)%2==0)0xFF1A2B2F.toInt() else 0xFF132226.toInt();gui.fill(x,y,x+cellSize-1,y+cellSize-1,cellColor);NativePixelArt.gamePiece(gui,board.elementOrNull(index)?.asString.orEmpty(),x,y,cellSize)}}}else boardRect=UiRect(0,0,0,0)
        if(actions!=null){val enabled=(0 until actions.size()).map{actions[it].asJsonObject}.filter{it.bool("enabled",true)}.take(6);enabled.forEachIndexed{index,action->val rect=if(sideActions)UiRect(area.right-actionWidth,boardTop+index*25,actionWidth,20) else UiRect(area.x+index*((area.width/enabled.size.coerceAtLeast(1)).coerceAtLeast(65)),area.bottom-22,(area.width/enabled.size.coerceAtLeast(1)-4).coerceAtLeast(61),20);addControl(rect,fit(action.str("label",action.str("id")),rect.width-8),mouseX,mouseY){gameAct(action.str("id"),emptyMap())}}}
        if(cards!=null&&cards.size()>0){val count=min(cards.size(),6);val gap=4;val cardW=((area.width-gap*(count-1))/count).coerceAtLeast(44);val y=area.bottom-48;repeat(count){index->val card=cards[index].asJsonObject;val rect=UiRect(area.x+index*(cardW+gap),y,cardW,43);gui.fill(rect.x,rect.y,rect.right,rect.bottom,panelAlt);gui.fill(rect.x,rect.y,rect.right,rect.y+3,cardColor(card.str("accent")));gui.drawCenteredString(font,fit(card.str("label",card.str("id")),rect.width-6),rect.x+rect.width/2,rect.y+10,text);gui.drawCenteredString(font,fit(card.str("subtitle"),rect.width-6),rect.x+rect.width/2,rect.y+25,muted);addHit(rect){when(view.str("gameId")){"uno"->gameAct("play",mapOf("index" to card.str("id"),"color" to "red"));"tft"->gameAct("buy",mapOf("index" to card.str("id").substringAfter(':')));else->gameAct("play",mapOf("index" to card.str("id")))}}}}
    }

    private fun drawModuleCard(gui:GuiGraphics,rect:UiRect,id:String,title:String,value:String,mouseX:Int,mouseY:Int,action:()->Unit){val hovered=rect.contains(mouseX.toDouble(),mouseY.toDouble());gui.fill(rect.x,rect.y+if(hovered)1 else 2,rect.right,rect.bottom,if(hovered)0xFF203438.toInt() else panelAlt);gui.fill(rect.x,rect.y,rect.x+4,rect.bottom,if(hovered)gold else accent);if(rect.height<32){NativePixelArt.icon(gui,id,rect.x+7,rect.y+4,14,if(hovered)gold else accent);gui.drawString(font,fit(title,rect.width-31),rect.x+26,rect.y+8,text,true)}else{NativePixelArt.icon(gui,id,rect.x+11,rect.y+10,24,if(hovered)gold else accent);gui.drawString(font,fit(title,rect.width-50),rect.x+43,rect.y+9,text,true);gui.drawString(font,fit(value,rect.width-50),rect.x+43,rect.y+25,muted,false)};addHit(rect,action=action)}
    private fun drawBalance(gui:GuiGraphics,x:Int,y:Int,width:Int,icon:String,label:String,value:Int,color:Int){gui.fill(x,y,x+width,y+36,panelAlt);gui.fill(x,y,x+4,y+36,color);NativePixelArt.icon(gui,icon,x+12,y+8,20,color);gui.drawString(font,label,x+42,y+7,muted,false);gui.drawString(font,value.toString(),x+42,y+20,text,true)}
    private fun drawNotice(gui:GuiGraphics,layout:NativeLayout){if(notice.isBlank())return;val value=fit(notice,(layout.content.width-20).coerceAtLeast(80));val w=(font.width(value)+20).coerceAtMost(layout.content.width);val x=layout.content.x+(layout.content.width-w)/2;val y=layout.content.bottom-23;gui.fill(x,y,x+w,y+19,0xEE1C2B2E.toInt());gui.fill(x,y,x+3,y+19,gold);gui.drawCenteredString(font,value,x+w/2,y+6,text)}
    private fun addControl(rect:UiRect,label:String,mouseX:Int,mouseY:Int,icon:String?=null,active:Boolean=false,enabled:Boolean=true,action:()->Unit){if(!visible(rect))return;val hovered=enabled&&rect.contains(mouseX.toDouble(),mouseY.toDouble());val fill=when{!enabled->0xFF141C1E.toInt();active->0xFF21443E.toInt();hovered->0xFF213338.toInt();else->panelAlt};val border=if(active)accent else if(hovered)gold else line;currentGui?.let{gui->gui.fill(rect.x,rect.y,rect.right,rect.bottom,fill);gui.fill(rect.x,rect.y,rect.x+3,rect.bottom,border);icon?.let{NativePixelArt.icon(gui,it,rect.x+6,rect.y+(rect.height-16)/2,16,if(active)accent else muted)};val textX=rect.x+if(icon==null)7 else 27;if(label.isNotBlank())gui.drawString(font,fit(label,rect.width-(textX-rect.x)-5),textX,rect.y+(rect.height-8)/2,if(enabled)text else muted,false)};controls+=Control(rect,label,icon,active,enabled,action)}
    private fun addHit(rect:UiRect,label:String="",action:()->Unit){if(visible(rect))controls+=Control(rect,label,action=action)}
    private fun visible(rect:UiRect):Boolean{val c=clip?:return rect.right>0&&rect.x<width&&rect.bottom>0&&rect.y<height;return rect.right>c.x&&rect.x<c.right&&rect.bottom>c.y&&rect.y<c.bottom}
    override fun mouseClicked(mouseX:Double,mouseY:Double,button:Int):Boolean{if(button==0){controls.asReversed().firstOrNull{it.enabled&&it.rect.contains(mouseX,mouseY)}?.let{it.action();return true};if(module=="game"&&boardW>0&&boardH>0&&boardRect.contains(mouseX,mouseY)){val col=((mouseX-boardRect.x)/cellSize).toInt();val row=((mouseY-boardRect.y)/cellSize).toInt();val index=row*boardW+col;val game=state.getAsJsonObject("view")?.str("gameId").orEmpty();if(game=="chess"||game=="xiangqi"){val first=selectedCell;if(first==null)selectedCell=index else{selectedCell=null;gameAct("move",mapOf("from" to coord(game,first),"to" to coord(game,index)))}}else selectedCell=index;return true}};return super.mouseClicked(mouseX,mouseY,button)}
    override fun mouseScrolled(mouseX:Double,mouseY:Double,horizontalAmount:Double,verticalAmount:Double):Boolean{if(module in setOf("gacha","skins","arcade","companions","wallet")){moduleScroll=(moduleScroll-verticalAmount.toInt()*24).coerceIn(0,1200);return true};return super.mouseScrolled(mouseX,mouseY,horizontalAmount,verticalAmount)}
    private fun coord(game:String,index:Int):String{val columns=if(game=="xiangqi")9 else 8;val row=index/columns;val column=index%columns;return if(game=="xiangqi")"${('a'.code+column).toChar()}$row" else "${('a'.code+column).toChar()}${8-row}"}
    private fun gameAct(action:String,args:Map<String,String>){val data=JsonObject();data.addProperty("gameAction",action);data.add("args",JsonObject().apply{args.forEach{(key,value)->addProperty(key,value)}});intent("act",data)}
    private fun open(target:String)=intent("open",json("module" to target),"dashboard")
    private fun intent(action:String,data:JsonObject,targetModule:String=module)=ClientPlayNetworking.send(NativeIntentC2S(targetModule,action,gson.toJson(data)))
    private fun json(vararg pairs:Pair<String,Any>)=JsonObject().apply{pairs.forEach{(key,value)->when(value){is Number->addProperty(key,value);is Boolean->addProperty(key,value);else->addProperty(key,value.toString())}}}
    private fun fit(value:String,availableWidth:Int):String=font.plainSubstrByWidth(value,availableWidth.coerceAtLeast(8))
    private fun tr(key:String):String=I18n.get(key)
    private fun moduleTitle(value:String)=tr("gui.svhub.module.$value")
    private fun moduleIcon(value:String)=if(value=="game")state.getAsJsonObject("view")?.str("gameId","arcade")?:"arcade" else value
    private fun gameColor(id:String)=when(id){"chess"->0xFFE4D9BE.toInt();"xiangqi"->0xFFE36C5C.toInt();"ludo"->0xFF60A5E8.toInt();"uno"->0xFFE2BE62.toInt();"pokecards"->0xFFB68BE0.toInt();"tft"->0xFF4CC7B2.toInt();else->0xFF80B56B.toInt()}
    private fun cardColor(id:String)=when(id.lowercase()){"red"->danger;"yellow"->gold;"green"->accent;"blue"->0xFF60A5E8.toInt();"fire"->danger;"water"->0xFF60A5E8.toInt();"grass"->0xFF80B56B.toInt();else->accent}
    private fun JsonObject.str(key:String,fallback:String="")=runCatching{get(key)?.asString?:fallback}.getOrDefault(fallback)
    private fun JsonObject.num(key:String,fallback:Int=0)=runCatching{get(key)?.asInt?:fallback}.getOrDefault(fallback)
    private fun JsonObject.bool(key:String,fallback:Boolean=false)=runCatching{get(key)?.asBoolean?:fallback}.getOrDefault(fallback)
    private fun JsonArray.elementOrNull(index:Int)=if(index in 0 until size())get(index) else null
}
