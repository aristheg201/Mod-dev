package io.github.aristheg201.svhub.client.nativeui

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.github.aristheg201.svhub.native.ArcadeCapabilities
import io.github.aristheg201.svhub.native.NativeArcadeService
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot

/** Opt-in local regression driver: real screen rendering and clicks; transport is recorded. */
object ArcadeAcceptanceHarness {
    private val pages = listOf("home","ranked","profile","settings") + NativeArcadeService.games.map { it.id }
    private var page = 0
    private var ticks = 0
    private var screen: NativePlatformScreen? = null
    private val sent = mutableListOf<Pair<String,JsonObject>>()
    var complete = false
        private set
    fun register() {
        if (System.getenv("SVHUB_ARCADE_SMOKE") != "1") { complete=true;return }
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            if(complete) return@register
            if(client.overlay != null) return@register
            if(page==0 && screen==null) { client.options.guiScale().set(System.getenv("SVHUB_ACCEPTANCE_SCALE")?.toIntOrNull() ?: 2);client.resizeDisplay() }
            if(page>=pages.size) {
                complete=true
                System.out.println("[Arcade acceptance] PASS navigation, all visible mode intents, utility links, animation toggle, scissor rendering; ${pages.size} screens")
                if(System.getenv("SVHUB_ARCADE_ONLY") == "1") client.stop()
                return@register
            }
            val name=pages[page]
            if(screen == null) {
                val state=JsonObject().apply {
                    add("games",JsonArray().also { a -> NativeArcadeService.games.forEach { game -> a.add(JsonObject().apply {
                        addProperty("id",game.id);addProperty("title",game.title)
                        add("modes",JsonArray().also { m -> game.modes.forEach(m::add) })
                        add("capabilities",ArcadeCapabilities.json(game.id,game.modes))
                    }) } })
                    add("ranking",JsonObject().also { ranks -> NativeArcadeService.games.filter { "ranked" in it.modes }.forEach { game -> ranks.add(game.id,JsonObject().apply { addProperty("tier","Unranked");addProperty("rating",0) }) } })
                }
                val next=NativePlatformScreen("arcade",state,"","local-acceptance")
                next.acceptanceIntent={ action,data -> sent += action to data.deepCopy() }
                next.arcadeUi.selected=if(name in NativeArcadeService.games.map { it.id })name else "chess"
                next.arcadeUi.page=when(name){"home"->ArcadeHomeScreen();"ranked"->ArcadeRankedScreen();"profile"->ArcadeProfileScreen();"settings"->ArcadeSettingsScreen();else->ArcadeGameDetailScreen(name)}
                client.setScreen(next);screen=next;ticks=0
                return@register
            }
            ticks++
            val active=screen!!
            if(ticks==15) {
                val controls=active.acceptanceControls()
                controls.forEach { (label,r) -> check(r.x>=0 && r.y>=0 && r.right<=active.width && r.bottom<=active.height) { "$name control outside viewport: $label $r (${active.width}x${active.height})" } }
                Screenshot.grab(client.gameDirectory,"arcade-$name.png",client.mainRenderTarget) { }
                fun click(label:String, last: Boolean = false) {
                    val matches=controls.filter { it.first==label }
                    val r=(if(last) matches.last() else matches.first()).second
                    check(active.mouseClicked(r.x+r.width/2.0,r.y+r.height/2.0,0))
                }
                if(name in NativeArcadeService.games.map { it.id }) {
                    val game=NativeArcadeService.games.single { it.id==name }
                    if(!ArcadeCapabilities.clock(name)) check(controls.none { it.first in ArcadeCapabilities.clocks })
                    for(label in listOf("NORMAL","RANKED","SOLO","VS AI")) if(controls.any { it.first==label && it.second.y>100 }) {
                        sent.clear();click(label,true)
                        val (action,data)=sent.single()
                        check(action=="start" && data.get("game").asString==name && data.get("mode").asString in game.modes)
                        check(data.has("timeControl")==ArcadeCapabilities.clock(name))
                    }
                }
                if(name=="home") {
                    listOf("GACHA","SKINS","STORE","ARENA","TACTICIANS","WALLET").forEach { label ->
                        sent.clear();click(label);check(sent.single().first=="open")
                    }
                    listOf("HOME","PLAY","RANKED","PROFILE","SETTINGS").forEach { click(it) }
                }
                if(name=="settings") {
                    val previous=ArcadePresentation.animation
                    click("Game Animation: ${if(previous) "ON" else "OFF"}")
                    check(previous!=ArcadePresentation.animation)
                    ArcadePresentation.toggle()
                }
            }
            if(ticks>=22) { active.prepareForServerReplacement();screen=null;page++ }
        }
    }
}
