package io.github.aristheg201.svhub.client.nativeui

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.github.aristheg201.svhub.native.network.NativeJsonDelta
import io.github.aristheg201.svhub.native.network.NativeJsonPatch
import io.github.aristheg201.svhub.client.gui.SVHubScreen
import io.github.aristheg201.svhub.native.network.NativeCloseC2S
import io.github.aristheg201.svhub.native.network.NativeIntentC2S
import io.github.aristheg201.svhub.ui.NativeLayout
import io.github.aristheg201.svhub.ui.ScrollbarLayout
import io.github.aristheg201.svhub.ui.ScrollbarMetrics
import io.github.aristheg201.svhub.ui.UiDensity
import io.github.aristheg201.svhub.ui.UiRect
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.resources.language.I18n
import net.minecraft.network.chat.Component
import java.util.UUID
import kotlin.math.min

class NativePlatformScreen(
    val module: String,
    private var state: JsonObject,
    private var notice: String,
    val viewId: String
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
    private val sceneInputs = mutableListOf<(Double, Double) -> Boolean>()
    private val itemDropInputs = mutableListOf<(Double, Double) -> Boolean>()
    private var selectedCell: Int? = null
    private var selectedSkin: String? = null
    private var selectedCompanion: String? = null
    private val tftUi = TftUiState()
    private val gachaUi = GachaRouletteState().also { it.observe(state, false) }
    private val boardSceneUi = NativeBoardSceneUiState()
    private var sceneFrame: PokemonSceneFrame? = null
    private var selectedTowerType: String? = null
    private var unoPendingCard: String? = null
    private var boardRect = UiRect(0, 0, 0, 0)
    private var boardW = 0
    private var boardH = 0
    private var cellSize = 0
    private var moduleScroll = 0
    private var moduleContentHeight = 0
    private var moduleMaxScroll = 0
    private var moduleScrollbar: ScrollbarMetrics? = null
    private var draggingModuleScrollbar = false
    private var moduleDragOffset = 0.0
    private var moduleViewport = UiRect(0, 0, 0, 0)
    private var clip: UiRect? = null
    private var currentGui: GuiGraphics? = null
    private var supersededByServer = false

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
        if (module == "gacha") gachaUi.observe(newState, true)
        if (module == "game") {
            val newView = newState.getAsJsonObject("view")
            if (newView == null || newView.bool("finished")) {
                selectedCell = null
                selectedTowerType = null
                unoPendingCard = null
            }
        }
        state = newState
        val errorKey = runCatching { newState.get("errorKey")?.asString.orEmpty() }.getOrDefault("")
        notice = when {
            errorKey.startsWith("gui.") -> tr(errorKey)
            message.startsWith("gui.") -> tr(message)
            else -> message
        }
    }
    fun applyDelta(patch: NativeJsonPatch, message: String) {
        val merged = state.deepCopy()
        NativeJsonDelta.apply(merged, patch)
        applyState(merged, message)
    }

    override fun init() { controls.clear(); sceneInputs.clear(); itemDropInputs.clear() }

    fun prepareForServerReplacement() { supersededByServer = true }

    override fun removed() {
        if (!supersededByServer) {
            NativePlatformClient.markClosed(viewId)
            runCatching { ClientPlayNetworking.send(NativeCloseC2S(viewId)) }
        }
        super.removed()
    }

    override fun render(gui: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        currentGui = gui
        val baseLayout = NativeLayout.resolve(width, height)
        val tftGame=module=="game" && state.getAsJsonObject("view")?.str("gameId")=="tft"
        val layout = if (tftGame) baseLayout.copy(
            navigation=UiRect(0,0,0,0),content=UiRect(4,4,(width-8).coerceAtLeast(144),(height-8).coerceAtLeast(70)),verticalNavigation=false
        ) else if (module == "game") baseLayout.copy(
            navigation = UiRect(0, 0, 0, 0),
            content = UiRect(8, 42, (width - 16).coerceAtLeast(144), (height - 50).coerceAtLeast(70)),
            verticalNavigation = false
        ) else baseLayout
        controls.clear()
        sceneInputs.clear()
        itemDropInputs.clear()
        drawBackground(gui)
        if(!tftGame) drawHeader(gui, layout, mouseX, mouseY)
        if (module != "game") drawNavigation(gui, layout, mouseX, mouseY)
        gui.fill(layout.content.x, layout.content.y, layout.content.right, layout.content.bottom, panel)
        gui.fill(layout.content.x, layout.content.y, layout.content.right, layout.content.y + 1, line)
        clip = layout.content
        moduleViewport = layout.content
        moduleContentHeight = layout.content.height
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
        if (module != "game") renderModuleScrollbar(gui, layout.content)
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
        val area=layout.content.inset(10)
        val wallet=state.getAsJsonObject("wallet")
        val rolling=state.bool("rolling")
        gui.drawString(font,"${tr("gui.svhub.ticket")}: ${wallet?.num("ticket") ?: 0}",area.x,area.y,gold,true)

        val rouletteHeight=GachaRouletteRenderer.render(gui,font,area,state,gachaUi)
        val bannerTop=area.y+24+rouletteHeight
        val gap=8
        val cols=if(area.width>=360)2 else 1
        val bannerW=(area.width-gap*(cols-1))/cols

        val banners=listOf("hunter" to tr("gui.svhub.gacha.hunter"),"beast" to tr("gui.svhub.gacha.beast"))
        val bannerRows=(banners.size+cols-1)/cols
        moduleContentHeight=maxOf(moduleContentHeight,bannerTop-layout.content.y+bannerRows*74+10)
        banners.forEachIndexed{index,(id,title)->
            val rect=UiRect(
                area.x+(index%cols)*(bannerW+gap),
                bannerTop+(index/cols)*74-moduleScroll,
                bannerW,
                66
            )
            gui.fill(rect.x,rect.y,rect.right,rect.bottom,panelAlt)
            gui.fill(rect.x,rect.y,rect.x+4,rect.bottom,if(id=="hunter")accent else gold)
            NativePixelArt.icon(gui,"gacha",rect.x+12,rect.y+12,28,if(id=="hunter")accent else gold)
            gui.drawString(font,fit(title,rect.width-60),rect.x+48,rect.y+12,text,true)
            gui.drawString(font,tr("gui.svhub.gacha.cost"),rect.x+48,rect.y+29,muted,false)
            addControl(
                UiRect(rect.right-74,rect.bottom-23,66,17),
                if(rolling)tr("gui.svhub.gacha.rolling") else tr("gui.svhub.roll"),
                mouseX,
                mouseY,
                enabled=!rolling
            ){
                intent("roll",json("banner" to id,"requestId" to UUID.randomUUID().toString()))
            }
        }
    }

    private fun renderSkins(gui:GuiGraphics,layout:NativeLayout,mouseX:Int,mouseY:Int){
        val area=layout.content.inset(8)
        val source=state.str("source","all")
        val page=state.num("page",0)
        val pages=state.num("pages",1)
        val skins=state.getAsJsonArray("skins")?:JsonArray()

        gui.drawString(font,"${tr("gui.svhub.owned")}: ${state.num("ownedCount")}  •  ${page+1}/$pages",area.x,area.y,muted,false)
        val tabs=listOf("all" to "gui.svhub.filter.all","dbz" to "DBZ","naruto" to "Naruto","pokelegends" to "PokeLegends")
        val tabGap=3
        val tabW=(area.width-tabGap*(tabs.size-1))/tabs.size
        tabs.forEachIndexed{index,(id,label)->
            addControl(
                UiRect(area.x+index*(tabW+tabGap),area.y+18,tabW,20),
                if(label.startsWith("gui."))tr(label) else label,
                mouseX,mouseY,active=source==id
            ){intent("source",json("source" to id,"page" to 0))}
        }

        val entries=(0 until min(skins.size(),24)).map{skins[it].asJsonObject}
        val selected=entries.firstOrNull{it.str("id")==selectedSkin} ?: entries.firstOrNull()
        if(selected!=null) selectedSkin=selected.str("id")

        val stageTop=area.y+43
        val stageH=(area.height*44/100).coerceIn(104,190)
        val stage=UiRect(area.x,stageTop,area.width,stageH)
        if(selected!=null){
            SkinShowcaseRenderer.render(
                gui,font,stage,
                SkinShowcaseRenderer.Skin(
                    id=selected.str("id"),
                    name=selected.str("name",selected.str("id")),
                    species=selected.str("species"),
                    aspect=selected.str("aspect"),
                    source=selected.str("source"),
                    rarity=selected.str("rarity"),
                    owned=selected.bool("owned")
                )
            )
        }else{
            gui.fill(stage.x,stage.y,stage.right,stage.bottom,panelAlt)
            NativePixelArt.icon(gui,"skins",stage.x+stage.width/2-18,stage.y+stage.height/2-18,36,accent)
            gui.drawCenteredString(font,tr("gui.svhub.not_found"),stage.x+stage.width/2,stage.bottom-18,muted)
        }

        val listTop=stage.bottom+7
        val cols=when{area.width>=650->4;area.width>=430->3;else->2}
        val gap=5
        val cellW=(area.width-gap*(cols-1))/cols
        val cellH=40
        val rows=(entries.size+cols-1)/cols
        moduleContentHeight=maxOf(moduleContentHeight,listTop+rows*(cellH+gap)+10-layout.content.y)

        entries.forEachIndexed{i,e->
            val row=i/cols
            val col=i%cols
            val rect=UiRect(area.x+col*(cellW+gap),listTop+row*(cellH+gap)-moduleScroll,cellW,cellH)
            val owned=e.bool("owned")
            val active=e.str("id")==selectedSkin
            val rarity=SkinShowcaseRenderer.rarityColor(e.str("rarity"))
            gui.fill(rect.x,rect.y,rect.right,rect.bottom,if(active)0xFF21443E.toInt() else panelAlt)
            gui.fill(rect.x,rect.y,rect.x+3,rect.bottom,if(active)gold else rarity)
            gui.drawString(font,fit(e.str("name",e.str("id")),rect.width-14),rect.x+9,rect.y+7,text,active)
            val subtitle=if(owned)tr("gui.svhub.owned") else e.str("rarity")
            gui.drawString(font,fit(subtitle,rect.width-14),rect.x+9,rect.y+23,if(owned)accent else rarity,false)
            addHit(rect){selectedSkin=e.str("id")}
        }
    }

    private fun renderArcade(gui:GuiGraphics,layout:NativeLayout,mouseX:Int,mouseY:Int){
        val area=layout.content.inset(8)
        gui.drawString(font,tr("gui.svhub.arcade.subtitle"),area.x,area.y,muted,false)
        val games=state.getAsJsonArray("games")?:return
        val activeGame=state.str("activeGame")
        val rowH=42
        val activeHeight=if(activeGame.isNotBlank())38 else 0
        val listTop=area.y+22+activeHeight
        moduleContentHeight=maxOf(moduleContentHeight,listTop+games.size()*(rowH+5)+8-layout.content.y)

        if(activeGame.isNotBlank()){
            val activeRect=UiRect(area.x,area.y+18-moduleScroll,area.width,32)
            gui.fill(activeRect.x,activeRect.y,activeRect.right,activeRect.bottom,0xFF17312D.toInt())
            gui.fill(activeRect.x,activeRect.y,activeRect.x+4,activeRect.bottom,accent)
            gui.drawString(font,trf("gui.svhub.arcade.current_game",gameTitleFor(activeGame,activeGame)),activeRect.x+10,activeRect.y+11,text,true)
            val leaveW=(font.width(tr("gui.svhub.leave_game"))+14).coerceAtLeast(56)
            val resumeW=(font.width(tr("gui.svhub.resume"))+14).coerceAtLeast(58)
            addControl(UiRect(activeRect.right-leaveW-6,activeRect.y+6,leaveW,20),tr("gui.svhub.leave_game"),mouseX,mouseY){
                intent("leave_active",JsonObject())
            }
            addControl(UiRect(activeRect.right-leaveW-resumeW-10,activeRect.y+6,resumeW,20),tr("gui.svhub.resume"),mouseX,mouseY){
                intent("resume",JsonObject())
            }
        }

        val modeLabels=linkedMapOf("bot_easy" to tr("gui.svhub.easy"),"bot_normal" to tr("gui.svhub.normal"),"bot_hard" to tr("gui.svhub.hard"),"pvp" to "PvP","solo" to tr("gui.svhub.solo"))
        for(i in 0 until games.size()){
            val game=games[i].asJsonObject
            val id=game.str("id")
            val y=listTop+i*(rowH+5)-moduleScroll
            val rect=UiRect(area.x,y,area.width,rowH)
            gui.fill(rect.x,rect.y,rect.right,rect.bottom,panelAlt)
            gui.fill(rect.x,rect.y,rect.x+4,rect.bottom,gameColor(id))
            NativePixelArt.icon(gui,id,rect.x+10,rect.y+8,26,gameColor(id))
            gui.drawString(font,gameTitleFor(id,game.str("title",id)),rect.x+45,rect.y+9,text,true)
            val advertised=game.getAsJsonArray("modes")?.let{a->(0 until a.size()).map{a[it].asString}}.orEmpty()
            val modes=if(advertised.isEmpty())listOf("bot_easy","bot_normal","bot_hard","pvp") else advertised
            var right=rect.right-6
            modes.asReversed().forEach{mode->
                val label=modeLabels[mode]?:mode
                val w=(font.width(label)+14).coerceAtLeast(42)
                right-=w
                addControl(UiRect(right,rect.y+10,w,22),label,mouseX,mouseY){
                    intent("start",json("game" to id,"mode" to mode))
                }
                right-=4
            }
        }
    }

    private fun renderCompanions(gui:GuiGraphics,layout:NativeLayout,mouseX:Int,mouseY:Int){
        val area=layout.content.inset(8)
        val arena=state.getAsJsonObject("arena")
        val serverSelected=state.str("selected")
        if(selectedCompanion==null&&serverSelected.isNotBlank())selectedCompanion=serverSelected

        gui.drawString(font,tr("gui.svhub.arena.title"),area.x,area.y,text,true)
        if(arena!=null){
            renderCompanionArena(gui,area,arena,mouseX,mouseY)
            moduleContentHeight=maxOf(moduleContentHeight,area.height)
            return
        }

        gui.drawString(font,tr("gui.svhub.arena.pick"),area.x,area.y+14,muted,false)
        val companions=state.getAsJsonArray("companions")?:JsonArray()
        val cols=when{area.width>=700->4;area.width>=460->3;else->2}
        val gap=6
        val cardW=(area.width-gap*(cols-1))/cols
        val cardH=88
        val top=area.y+36
        val rows=(companions.size()+cols-1)/cols
        moduleContentHeight=maxOf(moduleContentHeight,top+rows*(cardH+gap)+44-layout.content.y)

        repeat(companions.size()){index->
            val entry=companions[index].asJsonObject
            val id=entry.str("id")
            val col=index%cols
            val row=index/cols
            val rect=UiRect(area.x+col*(cardW+gap),top+row*(cardH+gap)-moduleScroll,cardW,cardH)
            val selected=id==selectedCompanion||id==serverSelected
            gui.fill(rect.x,rect.y,rect.right,rect.bottom,if(selected)0xFF21443E.toInt() else panelAlt)
            gui.fill(rect.x,rect.y,rect.x+4,rect.bottom,if(selected)accent else line)
            val modelTop=rect.y+5
            val modelBottom=rect.bottom-24
            val rendered=VanillaCompanionModelRenderer.render(
                gui,id,rect.x+5,modelTop,rect.right-5,modelBottom,true
            )
            if(!rendered)NativePixelArt.icon(gui,"companions",rect.x+10,rect.y+18,24,accent)
            gui.drawCenteredString(font,fit(entry.str("name",id),rect.width-10),rect.x+rect.width/2,rect.bottom-18,text)
            gui.drawCenteredString(font,"HP ${entry.num("hp")}  •  ATK ${entry.num("power")}",rect.x+rect.width/2,rect.bottom-8,muted)
            addHit(rect){
                selectedCompanion=id
                intent("select",json("entity" to id))
            }
        }

        val selected=selectedCompanion?.takeIf(String::isNotBlank)
        if(selected!=null){
            val y=top+rows*(cardH+gap)-moduleScroll+2
            addControl(UiRect(area.x,y,(area.width-6)/2,22),tr("gui.svhub.arena.fight"),mouseX,mouseY,enabled=selected==serverSelected){
                intent("arena_start",JsonObject())
            }
            addControl(UiRect(area.x+(area.width+6)/2,y,(area.width-6)/2,22),tr("gui.svhub.arena.dismiss"),mouseX,mouseY){
                selectedCompanion=null
                intent("select",json("entity" to "none"))
            }
        }
    }

    private fun renderCompanionArena(gui:GuiGraphics,area:UiRect,arena:JsonObject,mouseX:Int,mouseY:Int){
        val top=area.y+20
        val modelBottom=(area.y+area.height*2/3).coerceAtLeast(top+70)
        val mid=area.x+area.width/2
        val half=(area.width/2-8).coerceAtLeast(60)
        val playerRect=UiRect(area.x,top,half,modelBottom-top)
        val enemyRect=UiRect(mid+4,top,half,modelBottom-top)

        gui.fill(playerRect.x,playerRect.y,playerRect.right,playerRect.bottom,panelAlt)
        gui.fill(enemyRect.x,enemyRect.y,enemyRect.right,enemyRect.bottom,panelAlt)
        VanillaCompanionModelRenderer.render(gui,arena.str("playerId"),playerRect.x+4,playerRect.y+6,playerRect.right-4,playerRect.bottom-20,false)
        VanillaCompanionModelRenderer.render(gui,arena.str("enemyId"),enemyRect.x+4,enemyRect.y+6,enemyRect.right-4,enemyRect.bottom-20,true)

        gui.drawCenteredString(font,fit(arena.str("playerName"),playerRect.width-8),playerRect.x+playerRect.width/2,playerRect.y+5,text)
        gui.drawCenteredString(font,fit(arena.str("enemyName"),enemyRect.width-8),enemyRect.x+enemyRect.width/2,enemyRect.y+5,text)
        NativePixelArt.healthBar(gui,playerRect.x+7,playerRect.bottom-15,playerRect.width-14,arena.num("playerHp"),arena.num("playerMaxHp"),accent)
        NativePixelArt.healthBar(gui,enemyRect.x+7,enemyRect.bottom-15,enemyRect.width-14,arena.num("enemyHp"),arena.num("enemyMaxHp"),danger)
        gui.drawString(font,trf("gui.svhub.arena.energy",arena.num("playerEnergy")),playerRect.x+7,playerRect.bottom-27,gold,false)

        val finished=arena.bool("finished")
        val logY=modelBottom+7
        val arenaStatus=when{
            finished&&arena.str("result")=="Victory"->tr("gui.svhub.arena.victory")
            finished->tr("gui.svhub.arena.defeat")
            else->trf("gui.svhub.arena.turn",arena.num("turn"))
        }
        gui.drawCenteredString(font,fit(arenaStatus,area.width-12),area.x+area.width/2,logY,if(finished)gold else muted)

        val controlsY=area.bottom-25
        if(finished){
            addControl(UiRect(area.x,controlsY,area.width,21),tr("gui.svhub.arena.rematch"),mouseX,mouseY){
                intent("arena_start",JsonObject())
            }
        }else{
            val gap=4
            val w=(area.width-gap*2)/3
            addControl(UiRect(area.x,controlsY,w,21),tr("gui.svhub.arena.attack"),mouseX,mouseY){
                intent("arena_act",json("move" to "attack"))
            }
            addControl(UiRect(area.x+w+gap,controlsY,w,21),tr("gui.svhub.arena.skill"),mouseX,mouseY,enabled=arena.num("playerEnergy")>=2){
                intent("arena_act",json("move" to "skill"))
            }
            addControl(UiRect(area.x+(w+gap)*2,controlsY,w,21),tr("gui.svhub.arena.guard"),mouseX,mouseY){
                intent("arena_act",json("move" to "guard"))
            }
        }
    }

    private fun renderWallet(gui:GuiGraphics,layout:NativeLayout){val area=layout.content.inset(10);val wallet=state.getAsJsonObject("wallet");drawBalance(gui,area.x,area.y,area.width,"wallet",tr("gui.svhub.token"),wallet?.num("arcade")?:0,accent);drawBalance(gui,area.x,area.y+44,area.width,"gacha",tr("gui.svhub.ticket"),wallet?.num("ticket")?:0,gold)}

    private fun renderGame(gui:GuiGraphics,layout:NativeLayout,mouseX:Int,mouseY:Int){
        val view=state.getAsJsonObject("view")?:return
        val gameId=view.str("gameId")
        if(view.bool("finished") && view.getAsJsonObject("resultPresentation")!=null){
            renderGameResult(gui,layout,view,mouseX,mouseY)
            return
        }
        if(gameId=="tft"){
            sceneFrame=null
            boardRect=UiRect(0,0,0,0)
            boardW=0
            boardH=0
            TftGameRenderer.render(
                gui = gui,
                font = font,
                area = layout.content,
                density = layout.density,
                view = view,
                ui = tftUi,
                mouseX = mouseX,
                mouseY = mouseY,
                hooks = TftGameRenderer.Hooks(
                    control = { rect, label, enabled, action -> addControl(rect, label, mouseX, mouseY, enabled = enabled, action = action) },
                    hit = { rect, action -> addHit(rect, action = action) },
                    sceneInput = { handler -> sceneInputs += handler },
                    dropInput = { handler -> itemDropInputs += handler },
                    action = ::gameAct,
                    back = { intent("leave", JsonObject()) }
                )
            )
            return
        }

        val area=layout.content.inset(8)
        gui.drawString(font,gameTitleFor(gameId,view.str("title",tr("gui.svhub.game"))),area.x,area.y,text,true)
        gui.drawString(font,fit(localizedGameStatus(view),area.width-8),area.x,area.y+13,gold,false)

        val actions=view.getAsJsonArray("actions")
        val cards=view.getAsJsonArray("cards")
        boardW=view.num("boardWidth")
        boardH=view.num("boardHeight")
        val actionWidth=if(area.width>=520)110 else 0
        val sideActions=actionWidth>0
        val availableBoardWidth=area.width-if(sideActions)actionWidth+8 else 0
        val bottomReserve=when {
            gameId=="uno" && unoPendingCard!=null -> 84
            cards!=null&&cards.size()>0 -> 58
            else -> 8
        }
        val boardTop=area.y+30
        val boardAvailableHeight=(area.bottom-bottomReserve-boardTop).coerceAtLeast(30)
        val sceneArea=UiRect(area.x,boardTop,availableBoardWidth,boardAvailableHeight)
        val cardTable=CardTable3DRenderer.supports(gameId)

        if(cardTable){
            sceneFrame=null
            boardRect=sceneArea
            cellSize=0
            CardTable3DRenderer.render(gui,font,sceneArea,view,mouseX,mouseY){rect,card->
                addHit(rect){
                    when(gameId){
                        "uno"->{
                            val kind=card.getAsJsonObject("meta")?.str("kind").orEmpty()
                            if(kind=="wild"||kind=="wild4")unoPendingCard=card.str("id")
                            else gameAct("play",mapOf("index" to card.str("id")))
                        }
                        "pokecards"->gameAct("play",mapOf("index" to card.str("id")))
                    }
                }
            }
        }else if(NativeBoardSceneRenderer.supports(gameId)){
            val rendered=NativeBoardSceneRenderer.render(gui,font,sceneArea,view,boardSceneUi,selectedCell)
            sceneFrame=rendered?.frame
            boardRect=sceneArea
            cellSize=0
        }else{
            sceneFrame=null
            if(boardW>0&&boardH>0){
                cellSize=min(30,min((availableBoardWidth/boardW).coerceAtLeast(12),(boardAvailableHeight/boardH).coerceAtLeast(12)))
                boardRect=UiRect(area.x,boardTop,cellSize*boardW,cellSize*boardH)
                val board=view.getAsJsonArray("board")?:JsonArray()
                repeat(boardH){row->repeat(boardW){col->
                    val index=row*boardW+col
                    val x=boardRect.x+col*cellSize
                    val y=boardRect.y+row*cellSize
                    val cellColor=if((row+col)%2==0)0xFF1A2B2F.toInt() else 0xFF132226.toInt()
                    gui.fill(x,y,x+cellSize-1,y+cellSize-1,cellColor)
                    NativePixelArt.gamePiece(gui,board.elementOrNull(index)?.asString.orEmpty(),x,y,cellSize)
                }}
            }else boardRect=UiRect(0,0,0,0)
        }

        if(actions!=null){
            val enabled=(0 until actions.size()).map{actions[it].asJsonObject}.filter{it.bool("enabled",true)}.take(8)
            enabled.forEachIndexed{index,action->
                val rect=if(sideActions){
                    UiRect(area.right-actionWidth,boardTop+index*25,actionWidth,20)
                }else{
                    val widthPer=(area.width/enabled.size.coerceAtLeast(1)).coerceAtLeast(65)
                    UiRect(area.x+index*widthPer,area.bottom-22,widthPer-4,20)
                }
                addControl(rect,fit(localizedGameAction(view,action),rect.width-8),mouseX,mouseY){
                    gameAct(action.str("id"),actionPayload(action))
                }
            }
        }

        if(gameId=="tower_defense" && selectedCell!=null){
            val board=view.getAsJsonArray("board")
            val token=board?.elementOrNull(selectedCell!!)?.asString.orEmpty()
            if(token.startsWith("tower:")){
                val y=if(sideActions) area.bottom-48 else area.bottom-46
                val w=54
                addControl(UiRect(area.right-w*2-8,y,w,20),tr("gui.svhub.td.upgrade"),mouseX,mouseY){
                    gameAct("upgrade",mapOf("slot" to selectedCell.toString()))
                }
                addControl(UiRect(area.right-w-4,y,w,20),tr("gui.svhub.td.sell"),mouseX,mouseY){
                    gameAct("sell",mapOf("slot" to selectedCell.toString()))
                }
            }
        }

        if(cards!=null&&cards.size()>0&&!cardTable){
            val count=min(cards.size(),6)
            val gap=4
            val cardW=((area.width-gap*(count-1))/count).coerceAtLeast(44)
            val y=area.bottom-48
            repeat(count){index->
                val card=cards[index].asJsonObject
                val rect=UiRect(area.x+index*(cardW+gap),y,cardW,43)
                val selected=gameId=="tower_defense"&&selectedTowerType==card.str("id")
                gui.fill(rect.x,rect.y,rect.right,rect.bottom,if(selected)0xFF21443E.toInt() else panelAlt)
                gui.fill(rect.x,rect.y,rect.right,rect.y+3,cardColor(card.str("accent")))
                gui.drawCenteredString(font,fit(card.str("label",card.str("id")),rect.width-6),rect.x+rect.width/2,rect.y+10,text)
                val cardSubtitle=if(gameId=="tower_defense"){
                    val meta=card.getAsJsonObject("meta")?:JsonObject()
                    trf("gui.svhub.td.card_stats",card.num("cost"),meta.str("damage"),meta.str("range"))
                }else card.str("subtitle")
                gui.drawCenteredString(font,fit(cardSubtitle,rect.width-6),rect.x+rect.width/2,rect.y+25,muted)
                addHit(rect){
                    when(gameId){
                        "uno" -> {
                            val kind=card.getAsJsonObject("meta")?.str("kind").orEmpty()
                            if(kind=="wild"||kind=="wild4") unoPendingCard=card.str("id")
                            else gameAct("play",mapOf("index" to card.str("id")))
                        }
                        "tower_defense" -> selectedTowerType=card.str("id")
                        else -> gameAct("play",mapOf("index" to card.str("id")))
                    }
                }
            }
        }

        if(gameId=="uno" && unoPendingCard!=null){
            val colors=listOf("red","yellow","green","blue")
            val gap=4
            val w=((area.width-gap*3)/4).coerceAtLeast(42)
            colors.forEachIndexed{index,color->
                val rect=UiRect(area.x+index*(w+gap),area.bottom-74,w,20)
                addControl(rect,tr("gui.svhub.uno.$color"),mouseX,mouseY){
                    val card=unoPendingCard?:return@addControl
                    unoPendingCard=null
                    gameAct("play",mapOf("index" to card,"color" to color))
                }
            }
        }
    }

    private fun renderGameResult(gui:GuiGraphics,layout:NativeLayout,view:JsonObject,mouseX:Int,mouseY:Int){
        val gameId=view.str("gameId")
        sceneFrame=null
        boardRect=UiRect(0,0,0,0)
        boardW=0
        boardH=0
        ArcadeResultRenderer.render(
            gui=gui,
            font=font,
            area=layout.content.inset(if(gameId=="tft")0 else 8),
            view=view,
            hooks=ArcadeResultRenderer.Hooks(
                control={rect,label,enabled,action->addControl(rect,label,mouseX,mouseY,enabled=enabled,action=action)},
                continueAction={intent("leave",JsonObject())},
                rematch={intent("rematch",JsonObject())},
                exit={onClose()}
            )
        ){sceneArea->
            when {
                gameId=="tft" -> TftGameRenderer.render(
                    gui=gui,font=font,area=sceneArea,density=UiDensity.WIDE,view=view,ui=tftUi,mouseX=-10,mouseY=-10,
                    hooks=TftGameRenderer.Hooks(
                        control={_,_,_,_->},hit={_,_->},sceneInput={_->},dropInput={_->},action={_,_->},back={}
                    )
                )
                NativeBoardSceneRenderer.supports(gameId) -> {
                    NativeBoardSceneRenderer.render(gui,font,sceneArea.inset(4),view,boardSceneUi,null)
                }
                else -> {
                    gui.fill(sceneArea.x,sceneArea.y,sceneArea.right,sceneArea.bottom,panelAlt)
                    NativePixelArt.icon(gui,gameId,sceneArea.x+sceneArea.width/2-18,sceneArea.y+sceneArea.height/2-18,36,gameColor(gameId))
                }
            }
        }
    }

    private fun actionPayload(action:JsonObject):Map<String,String>{
        val payload=action.getAsJsonObject("payload")?:return emptyMap()
        return payload.entrySet().associate{(key,value)->key to runCatching{value.asString}.getOrDefault("")}
    }

    private fun renderModuleScrollbar(gui:GuiGraphics,content:UiRect){
        val metrics=ScrollbarLayout.resolve(content,moduleContentHeight,moduleScroll)
        moduleScrollbar=metrics
        moduleMaxScroll=metrics?.maxScroll?:0
        moduleScroll=metrics?.clampedScroll?:0
        if(metrics==null){draggingModuleScrollbar=false;return}
        val track=metrics.visualTrack;val thumb=metrics.visualThumb
        gui.fill(track.x,track.y,track.right,track.bottom,0xCC223337.toInt())
        gui.fill(thumb.x,thumb.y,thumb.right,thumb.bottom,if(draggingModuleScrollbar)gold else accent)
    }

    private fun setModuleScrollFromThumb(mouseY:Double){
        val metrics=moduleScrollbar?:return
        moduleScroll=ScrollbarLayout.scrollFromPointer(metrics,mouseY,moduleDragOffset)
    }

    private fun drawModuleCard(gui:GuiGraphics,rect:UiRect,id:String,title:String,value:String,mouseX:Int,mouseY:Int,action:()->Unit){val hovered=rect.contains(mouseX.toDouble(),mouseY.toDouble());gui.fill(rect.x,rect.y+if(hovered)1 else 2,rect.right,rect.bottom,if(hovered)0xFF203438.toInt() else panelAlt);gui.fill(rect.x,rect.y,rect.x+4,rect.bottom,if(hovered)gold else accent);if(rect.height<32){NativePixelArt.icon(gui,id,rect.x+7,rect.y+4,14,if(hovered)gold else accent);gui.drawString(font,fit(title,rect.width-31),rect.x+26,rect.y+8,text,true)}else{NativePixelArt.icon(gui,id,rect.x+11,rect.y+10,24,if(hovered)gold else accent);gui.drawString(font,fit(title,rect.width-50),rect.x+43,rect.y+9,text,true);gui.drawString(font,fit(value,rect.width-50),rect.x+43,rect.y+25,muted,false)};addHit(rect,action=action)}
    private fun drawBalance(gui:GuiGraphics,x:Int,y:Int,width:Int,icon:String,label:String,value:Int,color:Int){gui.fill(x,y,x+width,y+36,panelAlt);gui.fill(x,y,x+4,y+36,color);NativePixelArt.icon(gui,icon,x+12,y+8,20,color);gui.drawString(font,label,x+42,y+7,muted,false);gui.drawString(font,value.toString(),x+42,y+20,text,true)}
    private fun drawNotice(gui:GuiGraphics,layout:NativeLayout){if(notice.isBlank())return;val value=fit(notice,(layout.content.width-20).coerceAtLeast(80));val w=(font.width(value)+20).coerceAtMost(layout.content.width);val x=layout.content.x+(layout.content.width-w)/2;val y=layout.content.bottom-23;gui.fill(x,y,x+w,y+19,0xEE1C2B2E.toInt());gui.fill(x,y,x+3,y+19,gold);gui.drawCenteredString(font,value,x+w/2,y+6,text)}
    private fun addControl(rect:UiRect,label:String,mouseX:Int,mouseY:Int,icon:String?=null,active:Boolean=false,enabled:Boolean=true,action:()->Unit){if(!visible(rect))return;val hovered=enabled&&rect.contains(mouseX.toDouble(),mouseY.toDouble());val fill=when{!enabled->0xFF141C1E.toInt();active->0xFF21443E.toInt();hovered->0xFF213338.toInt();else->panelAlt};val border=if(active)accent else if(hovered)gold else line;currentGui?.let{gui->gui.fill(rect.x,rect.y,rect.right,rect.bottom,fill);gui.fill(rect.x,rect.y,rect.x+3,rect.bottom,border);icon?.let{NativePixelArt.icon(gui,it,rect.x+6,rect.y+(rect.height-16)/2,16,if(active)accent else muted)};val textX=rect.x+if(icon==null)7 else 27;if(label.isNotBlank())gui.drawString(font,fit(label,rect.width-(textX-rect.x)-5),textX,rect.y+(rect.height-8)/2,if(enabled)text else muted,false)};controls+=Control(rect,label,icon,active,enabled,action)}
    private fun addHit(rect:UiRect,label:String="",action:()->Unit){if(visible(rect))controls+=Control(rect,label,action=action)}
    private fun visible(rect:UiRect):Boolean{val c=clip?:return rect.right>0&&rect.x<width&&rect.bottom>0&&rect.y<height;return rect.right>c.x&&rect.x<c.right&&rect.bottom>c.y&&rect.y<c.bottom}
    override fun mouseClicked(mouseX:Double,mouseY:Double,button:Int):Boolean{
        if(button==0){
            val scrollbar=moduleScrollbar
            if(module!="game"&&scrollbar!=null&&scrollbar.hitRect.contains(mouseX,mouseY)){
                if(mouseY>=scrollbar.thumbTop&&mouseY<scrollbar.thumbBottom){
                    draggingModuleScrollbar=true
                    moduleDragOffset=mouseY-scrollbar.thumbTop
                }else{
                    moduleDragOffset=(scrollbar.thumbBottom-scrollbar.thumbTop)/2.0
                    setModuleScrollFromThumb(mouseY)
                    draggingModuleScrollbar=true
                }
                return true
            }
            controls.asReversed().firstOrNull{it.enabled&&it.rect.contains(mouseX,mouseY)}?.let{it.action();return true}
            if (sceneInputs.asReversed().any { it(mouseX, mouseY) }) return true
            if(module=="game"){
                val game=state.getAsJsonObject("view")?.str("gameId").orEmpty()
                val projected=sceneFrame?.layout?.pick(mouseX,mouseY)
                if(projected!=null){
                    when(game){
                        "chess","xiangqi"->{
                            val first=selectedCell
                            if(first==null) selectedCell=projected
                            else if(first==projected) selectedCell=null
                            else{
                                selectedCell=null
                                gameAct("move",mapOf("from" to coord(game,first),"to" to coord(game,projected)))
                            }
                        }
                        "tower_defense"->{
                            val tower=selectedTowerType
                            if(tower!=null){
                                selectedTowerType=null
                                selectedCell=projected
                                gameAct("deploy",mapOf("type" to tower,"slot" to projected.toString()))
                            }else selectedCell=projected
                        }
                        "ludo"->selectedCell=projected
                    }
                    return true
                }
                if(boardW>0&&boardH>0&&cellSize>0&&boardRect.contains(mouseX,mouseY)){
                    val col=((mouseX-boardRect.x)/cellSize).toInt()
                    val row=((mouseY-boardRect.y)/cellSize).toInt()
                    val index=row*boardW+col
                    if(game=="chess"||game=="xiangqi"){
                        val first=selectedCell
                        if(first==null)selectedCell=index
                        else{selectedCell=null;gameAct("move",mapOf("from" to coord(game,first),"to" to coord(game,index)))}
                    }else selectedCell=index
                    return true
                }
            }
        }
        return super.mouseClicked(mouseX,mouseY,button)
    }
    override fun mouseDragged(mouseX:Double,mouseY:Double,button:Int,dragX:Double,dragY:Double):Boolean{
        if(button==0 && tftUi.isItemDragging()) return true
        if(button==0&&draggingModuleScrollbar){
            setModuleScrollFromThumb(mouseY)
            return true
        }
        return super.mouseDragged(mouseX,mouseY,button,dragX,dragY)
    }
    override fun mouseReleased(mouseX:Double,mouseY:Double,button:Int):Boolean{
        if(button==0 && tftUi.isItemDragging()){
            itemDropInputs.asReversed().any { it(mouseX, mouseY) }
            tftUi.clearItem()
            return true
        }
        if(button==0)draggingModuleScrollbar=false
        return super.mouseReleased(mouseX,mouseY,button)
    }
    override fun mouseScrolled(mouseX:Double,mouseY:Double,horizontalAmount:Double,verticalAmount:Double):Boolean{
        if(module!="game"&&moduleViewport.contains(mouseX,mouseY)&&moduleMaxScroll>0){
            moduleScroll=(moduleScroll-(verticalAmount*28.0).toInt()).coerceIn(0,moduleMaxScroll)
            return true
        }
        return super.mouseScrolled(mouseX,mouseY,horizontalAmount,verticalAmount)
    }
    private fun coord(game:String,index:Int):String{val columns=if(game=="xiangqi")9 else 8;val row=index/columns;val column=index%columns;return if(game=="xiangqi")"${('a'.code+column).toChar()}$row" else "${('a'.code+column).toChar()}${8-row}"}
    private fun gameAct(action:String,args:Map<String,String>){val data=JsonObject();data.addProperty("gameAction",action);data.add("args",JsonObject().apply{args.forEach{(key,value)->addProperty(key,value)}});intent("act",data)}
    private fun open(target:String)=intent("open",json("module" to target))
    private fun intent(action:String,data:JsonObject)=ClientPlayNetworking.send(NativeIntentC2S(module,action,gson.toJson(data),viewId))
    private fun json(vararg pairs:Pair<String,Any>)=JsonObject().apply{pairs.forEach{(key,value)->when(value){is Number->addProperty(key,value);is Boolean->addProperty(key,value);else->addProperty(key,value.toString())}}}
    private fun fit(value:String,availableWidth:Int):String=font.plainSubstrByWidth(value,availableWidth.coerceAtLeast(8))
    private fun gameTitleFor(gameId:String,fallback:String):String =
        trOr("gui.svhub.game." + gameId + ".title", fallback)

    private fun localizedGameAction(view:JsonObject,action:JsonObject):String {
        val id=action.str("id")
        val fields=view.getAsJsonObject("fields")?:JsonObject()
        return when {
            id=="move" && view.str("gameId")=="ludo" -> {
                val piece=action.getAsJsonObject("payload")?.str("piece")?.toIntOrNull()?.plus(1)
                if(piece!=null)trf("gui.svhub.ludo.move_piece",piece) else trOr("gui.svhub.action.move",action.str("label",id))
            }
            id=="start_wave" -> trf("gui.svhub.action.start_wave",fields.num("wave")+1)
            else -> trOr("gui.svhub.action." + id,action.str("label",id))
        }
    }

    private fun localizedGameStatus(view:JsonObject):String {
        if(view.bool("finished")){
            val winner=view.str("winner")
            return if(winner.isNotBlank())trf("gui.svhub.game.finished_winner",winner) else tr("gui.svhub.game.finished")
        }
        val gameId=view.str("gameId")
        val phase=view.str("phase")
        val fields=view.getAsJsonObject("fields")?:JsonObject()
        return when(gameId){
            "chess" -> if(view.str("status")=="Check")tr("gui.svhub.chess.check") else trf("gui.svhub.game.turn",view.str("turn"))
            "xiangqi" -> if(view.str("status")=="Chiếu tướng")tr("gui.svhub.xiangqi.check") else trf("gui.svhub.game.turn",view.str("turn"))
            "ludo" -> if(phase=="roll")tr("gui.svhub.ludo.roll_wait") else trf("gui.svhub.ludo.rolled",fields.num("rolled"))
            "uno" -> {
                val color=fields.str("activeColor")
                trf("gui.svhub.uno.status",trOr("gui.svhub.uno." + color,color),fields.str("top"))
            }
            "pokecards" -> if(phase=="waiting")tr("gui.svhub.pokecards.waiting") else tr("gui.svhub.pokecards.pick")
            "tower_defense" -> if(fields.bool("running"))trf("gui.svhub.td.wave_running",fields.num("wave")) else trf("gui.svhub.td.prepare",fields.num("wave")+1)
            else -> view.str("status")
        }
    }

    private fun trOr(key:String,fallback:String):String {
        val translated=tr(key)
        return if(translated==key)fallback else translated
    }
    private fun trf(key:String,vararg args:Any):String=I18n.get(key,*args)
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
