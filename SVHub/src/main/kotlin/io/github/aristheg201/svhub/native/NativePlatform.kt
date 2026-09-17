package io.github.aristheg201.svhub.native

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.github.aristheg201.svhub.companion.VanillaCompanionService
import io.github.aristheg201.svhub.native.network.NativePlatformNetwork
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.nio.file.Path

object NativePlatform {
    private var tickCounter=0
    fun start(root:Path){NativeProfileStore.start(root.resolve("profiles"));NativeArcadeService.start(root.resolve("arcade"))}
    fun onJoin(player:ServerPlayer){SkiesSkinsBridge.invalidate();NativeProfileStore.onJoin(player){}}
    fun onDisconnect(player:ServerPlayer){NativeArcadeService.onDisconnect(player);NativeProfileStore.onDisconnect(player)}
    fun tick(server:MinecraftServer){tickCounter++;NativeArcadeService.tick(server).forEach{(id,message)->server.playerList.getPlayer(id)?.let{p->if(NativePlatformNetwork.currentModule(id)=="game")NativePlatformNetwork.sendState(p,"game",gameState(p),message)}};if(tickCounter%20==0)server.playerList.players.forEach{p->if(NativePlatformNetwork.currentModule(p.uuid)=="game")NativePlatformNetwork.sendState(p,"game",gameState(p))}}
    fun shutdown(){NativeArcadeService.shutdown();NativeProfileStore.shutdown()}
    fun open(player:ServerPlayer,requested:String):Boolean{if(!NativeProfileStore.isLoaded(player.uuid)){player.sendSystemMessage(net.minecraft.network.chat.Component.literal("SVHub đang tải profile."));return false};val module=requested.lowercase().trim().takeIf{it in MODULES}?:"dashboard";NativePlatformNetwork.sendOpen(player,module,state(player,module));return true}
    fun handleIntent(player:ServerPlayer,module:String,action:String,data:JsonObject):String{if(!NativeProfileStore.isLoaded(player.uuid))return "Profile chưa sẵn sàng.";return when(module){"gacha"->handleGacha(player,action,data);"skins"->handleSkins(player,action,data);"arcade"->handleArcade(player,action,data);"game"->handleGame(player,action,data);"companions"->handleCompanions(player,action,data);"wallet","dashboard"->when(action){"open"->{open(player,data.string("module","dashboard"));""};"close"->{NativePlatformNetwork.close(player.uuid);""};else->"Không có thao tác cho module này."};else->"Module không hợp lệ."}}
    fun state(player:ServerPlayer,module:String):JsonObject=when(module){"gacha"->NativeGachaService.state(player);"skins"->NativeSkinService.state(player);"arcade"->NativeArcadeService.lobbyState(player);"game"->gameState(player);"companions"->companionState();"wallet"->walletState(player);else->dashboardState(player)}
    fun refresh(player:ServerPlayer,module:String)=NativePlatformNetwork.sendState(player,module,state(player,module))
    private fun handleGacha(player:ServerPlayer,action:String,data:JsonObject)=when(action){"roll"->NativeGachaService.roll(player,data.string("banner","hunter")).let{r->NativePlatformNetwork.sendState(player,"gacha",r.state?:NativeGachaService.state(player,data.string("banner","hunter")),r.message);r.message};"select"->{NativePlatformNetwork.sendState(player,"gacha",NativeGachaService.state(player,data.string("banner","hunter")));""};else->"Gacha action không hợp lệ."}
    private fun handleSkins(player:ServerPlayer,action:String,data:JsonObject)=when(action){
        "page","source"->{val source=data.string("source","all").takeIf{it in setOf("all","dbz","naruto","pokelegends","hunter")}?:"all";NativePlatformNetwork.sendState(player,"skins",NativeSkinService.state(player,data.int("page",0),source));""}
        "buy","shop"->NativeSkinService.purchase(player,data.string("skin")).message
        "inventory"->NativeSkinService.openInventory(player).message
        "equip"->NativeSkinService.equip(player,data.string("skin"),data.int("slot")).let{r->NativePlatformNetwork.sendState(player,"skins",NativeSkinService.state(player,data.int("page",0),data.string("source","all")),r.message);r.message}
        "unequip"->NativeSkinService.unequip(player,data.int("slot")).let{r->NativePlatformNetwork.sendState(player,"skins",NativeSkinService.state(player,data.int("page",0),data.string("source","all")),r.message);r.message}
        else->"Skin action không hợp lệ."
    }
    private fun handleArcade(player:ServerPlayer,action:String,data:JsonObject)=when(action){"start"->NativeArcadeService.start(player,data.string("game"),data.string("mode","bot")).let{r->r.changedPlayers.forEach{id->player.server.playerList.getPlayer(id)?.let{p->if(NativeArcadeService.gameState(p).get("empty")?.asBoolean==false)NativePlatformNetwork.sendOpen(p,"game",gameState(p))else NativePlatformNetwork.sendState(p,"arcade",NativeArcadeService.lobbyState(p),r.message)}};r.message};"cancel_queue"->NativeArcadeService.cancelQueue(player).let{r->NativePlatformNetwork.sendState(player,"arcade",NativeArcadeService.lobbyState(player),r.message);r.message};"resume"->{val s=gameState(player);if(!s.get("empty").asBoolean)NativePlatformNetwork.sendOpen(player,"game",s);""};else->"Arcade action không hợp lệ."}
    private fun handleGame(player:ServerPlayer,action:String,data:JsonObject):String{if(action=="leave"){val r=NativeArcadeService.leave(player);NativePlatformNetwork.sendOpen(player,"arcade",NativeArcadeService.lobbyState(player));return r.message};if(action!="act")return "Game action không hợp lệ.";val args=linkedMapOf<String,String>();data.getAsJsonObject("args")?.entrySet()?.forEach{(k,v)->if(k.length<=32)args[k]=runCatching{v.asString}.getOrDefault("").take(128)};val r=NativeArcadeService.act(player,data.string("gameAction"),args);r.changedPlayers.forEach{id->player.server.playerList.getPlayer(id)?.let{NativePlatformNetwork.sendState(it,"game",gameState(it),r.message)}};if(!r.ok&&r.changedPlayers.isEmpty())NativePlatformNetwork.sendState(player,"game",gameState(player),r.message);return r.message}
    private fun handleCompanions(player:ServerPlayer,action:String,data:JsonObject):String{if(action!="select")return "Companion action không hợp lệ.";val ok=VanillaCompanionService.select(player,data.string("entity"));val msg=if(ok)"Đã cập nhật Linh Thú." else "Linh Thú không hợp lệ.";NativePlatformNetwork.sendState(player,"companions",companionState(),msg);return msg}
    private fun dashboardState(player:ServerPlayer)=JsonObject().apply{addProperty("module","dashboard");addProperty("skinBackend","SkiesSkins");addProperty("skinBackendReady",SkiesSkinsBridge.available());add("wallet",NativeSkinService.walletJson(NativeProfileStore.get(player.uuid)?:NativeProfile()));addProperty("ownedSkins",SkiesSkinsBridge.ownedCount(player));addProperty("skinTotal",NativeSkinCatalog.all.size);addProperty("gameCount",NativeArcadeService.games.size);add("features",JsonArray().also{a->listOf("gacha","skins","companions","arcade","wallet").forEach(a::add)})}
    private fun walletState(player:ServerPlayer)=JsonObject().apply{val p=NativeProfileStore.get(player.uuid)?:NativeProfile();addProperty("module","wallet");add("wallet",NativeSkinService.walletJson(p));addProperty("ownedSkins",SkiesSkinsBridge.ownedCount(player));addProperty("skinBackend","SkiesSkins")}
    private fun companionState()=JsonObject().apply{addProperty("module","companions");add("companions",JsonArray().also{a->COMPANIONS.forEach{(id,name)->a.add(JsonObject().apply{addProperty("id",id);addProperty("name",name)})}})}
    private fun gameState(player:ServerPlayer)=NativeArcadeService.gameState(player)
    private val MODULES=setOf("dashboard","gacha","skins","companions","arcade","game","wallet")
    private val COMPANIONS=linkedMapOf("allay" to "Allay","axolotl" to "Axolotl","bee" to "Bee","cat" to "Cat","fox" to "Fox","frog" to "Frog","parrot" to "Parrot","rabbit" to "Rabbit","wolf" to "Wolf","armadillo" to "Armadillo","sniffer" to "Sniffer","none" to "Cất Linh Thú")
}
