package io.github.aristheg201.svarcade.native

import com.mojang.authlib.GameProfile
import com.google.gson.JsonObject
import io.github.aristheg201.svarcade.native.store.*
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.level.ClientInformation
import net.minecraft.server.players.PlayerList
import java.util.UUID
import java.lang.reflect.ParameterizedType
import java.math.BigDecimal

/** Opt-in, isolated local server verification. Never runs on an ordinary server. */
object InternalArcadeAcceptance {
    private var players = emptyList<ServerPlayer>()
    private var cases = emptyList<Pair<String,String>>()
    private var index=0
    private var ticks=0
    private var started=false
    private var ready=0
    private var waiting=false
    private var economyChecked=false
    fun register() {
        if(System.getenv("SVARCADE_SERVER_ACCEPTANCE")!="1")return
        ServerLifecycleEvents.SERVER_STARTED.register { server ->
            cases=NativeArcadeService.games.flatMap { game -> game.modes.map { game.id to it } }
            players=(0..7).map { i -> ServerPlayer(server,server.overworld(),GameProfile(UUID.randomUUID(),"ArcadeTest$i"),ClientInformation.createDefault()) }
            // Register mock players only in the lookup used by matchmaking. They do not enter the world or tick network connections.
            val field=PlayerList::class.java.declaredFields.first { f ->
                val type=f.genericType as? ParameterizedType
                type?.actualTypeArguments?.toList()==listOf(UUID::class.java,ServerPlayer::class.java)
            }.also { it.isAccessible=true }
            @Suppress("UNCHECKED_CAST") val lookup=field.get(server.playerList) as MutableMap<UUID,ServerPlayer>
            players.forEach { p -> lookup[p.uuid]=p;NativeProfileStore.onJoin(p) { ready++ } }
            started=true
        }
        ServerTickEvents.END_SERVER_TICK.register { server ->
            if(!started)return@register
            ticks++
            try {
                check(ticks<4000) { "Acceptance timeout at $index ready=$ready" }
                if(ready!=players.size || !NativeArcadeSessionStore.isLoadComplete())return@register
                if(!economyChecked) { verifyEconomy();economyChecked=true }
                if(ticks%12!=0)return@register
                if(index>=cases.size) {
                    val rank=NativeProfileStore.get(players[0].uuid)?.stats?.values.orEmpty().sumOf { it.rankedPlayed }
                    check(rank>0) { "Rank settlement missing" }
                    println("[Arcade server acceptance] PASS ${cases.size} advertised modes launched, ranked queues settled, BEconomy renamed IDs, wallet, purchase and equip; rankedMatches=$rank")
                    started=false;server.halt(false);return@register
                }
                val (game,mode)=cases[index]
                if(!waiting) {
                    players.forEach { NativeArcadeService.leave(it) }
                    val count=if(mode=="pvp" && game=="tft")8 else if(mode in setOf("pvp","ranked"))2 else 1
                    players.take(count).forEach { p ->
                        val result=NativeArcadeService.start(p,game,mode,"15+10")
                        check(result.ok) { "$game/$mode rejected: ${result.message}" }
                    }
                    waiting=true
                } else {
                    val state=NativeArcadeService.gameState(players[0])
                    check(state.get("empty")?.asBoolean==false) { "$game/$mode did not start" }
                    val view=state.getAsJsonObject("view")
                    check(view.get("gameId").asString==game)
                    if(ArcadeCapabilities.clock(game)) check(view.getAsJsonObject("fields").get("incrementMs").asString=="10000")
                    println("[Arcade server acceptance] launched $game/$mode")
                    players.forEach { NativeArcadeService.leave(it) }
                    waiting=false;index++
                }
            } catch(error:Throwable) {
                println("[Arcade server acceptance] FAILED ${error.stackTraceToString()}")
                started=false;server.halt(false)
            }
        }
    }
    private fun verifyEconomy() {
        val entry=Class.forName("org.krripe.beconomy.api.BEconomy")
        val provider=entry.getMethod("getAPI").invoke(entry.getField("INSTANCE").get(null))
        val create=provider.javaClass.methods.first { it.name=="createCurrency" && it.parameterCount==7 }
        for(id in listOf("Arcade Credits","Arcade Gems")) create.invoke(provider,id,"Acceptance currency","minecraft:emerald",0,BigDecimal.ZERO,"A",false)
        val adapter=BEconomyAdapter()
        check(adapter.status().ready)
        val owner=players[0].uuid
        val path=java.nio.file.Files.createTempFile("arcade-economy-acceptance", ".json")
        java.nio.file.Files.writeString(path,"""{"defaults":{"reward":"credits"},"wallet":["@beconomy:*"],"currencies":{"credits":{"provider":"beconomy","currency":"arcade_credits"}}}""")
        EconomyConfig.start(path)
        val before=adapter.balance(owner,"credits")
        adapter.credit(owner,BigDecimal("1000"),"credits")
        check(adapter.debit(owner,BigDecimal("12.5"),"credits"))
        check(adapter.balance(owner,"credits").compareTo(before+BigDecimal("987.5"))==0)
        check(NativeCosmeticService.balances(owner).has("arcade_credits"))
        val account=CosmeticAccount(migrationVersion=1)
        val profiles=object:CosmeticProfiles {
            override fun read(player:UUID)=account
            override fun legacyBalance(player:UUID)=0L
            override fun retireLegacy(player:UUID,done:()->Unit)=done()
            override fun save(player:UUID,value:CosmeticAccount,done:()->Unit) { account.entitlements.clear();account.entitlements.addAll(value.entitlements);account.equipped.clear();account.equipped.putAll(value.equipped);account.payments.clear();account.payments.putAll(value.payments);done() }
        }
        val offer=CosmeticOffer(CosmeticKind.ARENA,"acceptance",BigDecimal("5.25"),"credits")
        val purchases=CosmeticPurchases(adapter,profiles,{listOf(offer)})
        purchases.purchase(owner,offer.kind,offer.id,UUID.randomUUID().toString()) { check(it==StoreResult.PURCHASED) { "Purchase: $it" } }
        purchases.equip(owner,offer.kind,offer.id) { check(it==StoreResult.EQUIPPED) }
        java.nio.file.Files.writeString(path,"""{"defaults":{"reward":"credits"},"wallet":["@beconomy:*"],"currencies":{"credits":{"provider":"beconomy","currency":"arcade_gems"}}}""")
        EconomyConfig.start(path)
        adapter.credit(owner,BigDecimal("3.75"),"credits")
        check(adapter.debit(owner,BigDecimal("3.75"),"credits"))
        println("[Arcade server acceptance] BEconomy provider mutations, alias rename, wallet and cosmetic purchase/equip PASS")
    }
}
