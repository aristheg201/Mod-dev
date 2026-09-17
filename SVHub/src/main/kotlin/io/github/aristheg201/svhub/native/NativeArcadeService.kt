package io.github.aristheg201.svhub.native

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.github.aristheg201.svhub.native.game.*
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.util.ArrayDeque
import java.util.UUID

object NativeArcadeService {
    data class GameDef(val id:String,val title:String,val icon:String,val modes:Set<String>)
    data class Result(val ok:Boolean,val message:String,val changedPlayers:Set<UUID> = emptySet())
    val games=listOf(
        GameDef("chess","Pokémon Chess","chess",setOf("bot","pvp")),GameDef("xiangqi","Cờ Tướng","xiangqi",setOf("bot","pvp")),
        GameDef("ludo","Cờ Cá Ngựa","ludo",setOf("bot","pvp")),GameDef("uno","UNO","uno",setOf("bot","pvp")),
        GameDef("pokecards","PokéDraft Cards","cards",setOf("bot","pvp")),GameDef("tft","Pokémon TFT","tft",setOf("bot","pvp")),
        GameDef("tower_defense","Pokémon Tower Defense","tower_defense",setOf("solo")))
    private val gson=Gson();private val sessions=linkedMapOf<String,NativeGameSession>();private val active=linkedMapOf<UUID,String>()
    private val queues=games.associate{it.id to ArrayDeque<UUID>()}.toMutableMap();private val rewarded=hashSetOf<String>();private val finishedAt=hashMapOf<String,Long>()
    fun lobbyState(player:ServerPlayer)=JsonObject().apply{
        addProperty("module","arcade");add("wallet",NativeSkinService.walletJson(NativeProfileStore.get(player.uuid)?:NativeProfile()))
        add("games",JsonArray().also{arr->games.forEach{d->arr.add(JsonObject().apply{addProperty("id",d.id);addProperty("title",d.title);addProperty("icon",d.icon);add("modes",JsonArray().also{a->d.modes.forEach(a::add)});addProperty("queued",queues[d.id]?.contains(player.uuid)==true)})}})
        active[player.uuid]?.let{sid->sessions[sid]?.let{s->addProperty("activeGame",s.gameId);addProperty("activeSession",s.sessionId)}}
        add("stats",JsonObject().also{out->NativeProfileStore.get(player.uuid)?.stats?.forEach{(id,s)->out.add(id,JsonObject().apply{addProperty("played",s.played);addProperty("wins",s.wins);addProperty("losses",s.losses);addProperty("draws",s.draws)})}})
    }
    fun gameState(player:ServerPlayer)=JsonObject().apply{addProperty("module","game");val s=active[player.uuid]?.let(sessions::get);if(s==null)addProperty("empty",true)else{addProperty("empty",false);add("view",gson.toJsonTree(s.viewFor(player.uuid.toString())))}}
    fun start(player:ServerPlayer,gameId:String,mode:String):Result{
        val def=games.firstOrNull{it.id==gameId}?:return Result(false,"Game không tồn tại.");if(sessions.size>=MAX_SESSIONS)return Result(false,"Arcade đang đạt giới hạn session; hãy thử lại sau.");if(mode !in def.modes)return Result(false,"Mode không hợp lệ.");if(active.containsKey(player.uuid))return Result(false,"Bạn đang có một ván chưa kết thúc.");queues.values.forEach{it.remove(player.uuid)}
        if(mode=="pvp"){val q=queues.getValue(gameId);while(q.isNotEmpty()){val oid=q.removeFirst();val op=player.server.playerList.getPlayer(oid)?:continue;if(oid==player.uuid||active.containsKey(oid))continue;val s=create(gameId,listOf(realSeat(player),realSeat(op)));register(s);return Result(true,"Đã ghép trận với ${op.gameProfile.name}.",setOf(player.uuid,oid))};q.addLast(player.uuid);return Result(true,"Đã vào hàng chờ ${def.title} PvP.",setOf(player.uuid))}
        val seats=when(gameId){"ludo"->listOf(realSeat(player),botSeat("Blue"),botSeat("Green"),botSeat("Yellow"));"uno"->listOf(realSeat(player),botSeat("UNO Bot A"),botSeat("UNO Bot B"),botSeat("UNO Bot C"));"tower_defense"->listOf(realSeat(player));else->listOf(realSeat(player),botSeat("SV Bot"))};val s=create(gameId,seats);register(s);return Result(true,"Đã tạo ${def.title}.",setOf(player.uuid))
    }
    fun cancelQueue(player:ServerPlayer):Result{var removed=false;queues.values.forEach{removed=it.remove(player.uuid)||removed};return Result(removed,if(removed)"Đã rời hàng chờ." else "Bạn không ở hàng chờ.",setOf(player.uuid))}
    fun act(player:ServerPlayer,action:String,args:Map<String,String>):Result{val sid=active[player.uuid]?:return Result(false,"Không có ván đang hoạt động.");val s=sessions[sid]?:return Result(false,"Session đã hết hạn.");val r=s.act(player.uuid.toString(),action,args);if(r.changed)finishIfNeeded(s);return Result(r.accepted,r.message,if(r.changed)realPlayers(s) else setOf(player.uuid))}
    fun leave(player:ServerPlayer):Result{cancelQueue(player);val sid=active[player.uuid]?:return Result(true,"Đã rời Arcade.",setOf(player.uuid));val s=sessions[sid];if(s!=null&&!s.finished)s.act(player.uuid.toString(),"resign",emptyMap());active.remove(player.uuid);finishIfNeeded(s);return Result(true,"Đã rời ván.",(s?.let(::realPlayers)?:emptySet())+player.uuid)}
    fun tick(server:MinecraftServer,now:Long=System.currentTimeMillis()):Set<UUID>{val changed=linkedSetOf<UUID>();sessions.values.toList().forEach{s->if(!s.finished&&s.tick(now))changed+=realPlayers(s);finishIfNeeded(s)};finishedAt.filterValues{now-it>SESSION_RETAIN_MS}.keys.toList().forEach{sid->sessions.remove(sid)?.let{s->realPlayers(s).forEach{id->if(active[id]==sid)active.remove(id)}};rewarded.remove(sid);finishedAt.remove(sid)};queues.values.forEach{q->q.removeIf{server.playerList.getPlayer(it)==null||active.containsKey(it)}};return changed}
    fun onDisconnect(player:ServerPlayer){queues.values.forEach{it.remove(player.uuid)};val sid=active.remove(player.uuid)?:return;val s=sessions[sid]?:return;if(!s.finished)s.act(player.uuid.toString(),"resign",emptyMap());finishIfNeeded(s)}
    fun shutdown(){sessions.clear();active.clear();queues.values.forEach{it.clear()};rewarded.clear();finishedAt.clear()}
    private fun register(s:NativeGameSession){sessions[s.sessionId]=s;realPlayers(s).forEach{active[it]=s.sessionId}}
    private fun create(id:String,seats:List<NativeSeat>):NativeGameSession=when(id){"chess"->ChessSession(seats);"xiangqi"->XiangqiSession(seats);"ludo"->LudoSession(seats);"uno"->UnoSession(seats);"pokecards"->CardDuelSession(seats);"tft"->TftSession(seats);"tower_defense"->TowerDefenseSession(seats);else->error("Unknown native game $id")}
    private fun finishIfNeeded(s:NativeGameSession?){if(s==null||!s.finished||!rewarded.add(s.sessionId))return;finishedAt[s.sessionId]=System.currentTimeMillis();realPlayers(s).forEach{id->NativeProfileStore.mutate(id){p->val st=p.stats.getOrPut(s.gameId){NativeGameStats()};st.played++;when{ s.winnerSeatId==null->{st.draws++;p.credit("arcade",25)};s.winnerSeatId==id.toString()->{st.wins++;p.credit("arcade",50)};else->{st.losses++;p.credit("arcade",15)}}}}}
    private fun realPlayers(s:NativeGameSession)=s.seats.asSequence().filterNot{it.bot}.mapNotNull{runCatching{UUID.fromString(it.id)}.getOrNull()}.toSet()
    private fun realSeat(p:ServerPlayer)=NativeSeat(p.uuid.toString(),p.gameProfile.name,false);private fun botSeat(name:String)=NativeSeat("bot:${UUID.randomUUID()}",name,true)
    private const val SESSION_RETAIN_MS=120_000L;private const val MAX_SESSIONS=512
}
