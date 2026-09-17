package io.github.aristheg201.svhub.native

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import io.github.aristheg201.svhub.SVHub
import io.github.aristheg201.svhub.SVHubRuntime
import io.github.aristheg201.svhub.util.AtomicFiles
import net.minecraft.server.level.ServerPlayer
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

object NativeProfileStore {
    private val gson = GsonBuilder().disableHtmlEscaping().create()
    private val profiles = ConcurrentHashMap<UUID, NativeProfile>()
    private val loaded = ConcurrentHashMap.newKeySet<UUID>()
    private val latestSnapshots = ConcurrentHashMap<UUID, NativeProfile>()
    private val queued = ConcurrentHashMap.newKeySet<UUID>()
    private val closed = AtomicBoolean(false)
    private lateinit var root: Path
    private val io = Executors.newSingleThreadExecutor { task -> Thread(task, "SVHub-NativeProfile-IO").apply { isDaemon = true; priority = Thread.NORM_PRIORITY - 1 } }

    fun start(path: Path) { root = path; Files.createDirectories(root); closed.set(false) }
    fun onJoin(player: ServerPlayer, ready: (ServerPlayer) -> Unit) {
        val id = player.uuid
        io.execute {
            val profile = load(id)
            SVHubRuntime.server?.execute {
                val live = SVHubRuntime.server?.playerList?.getPlayer(id)
                if (live != null) { profiles[id] = sanitize(profile); loaded += id; ready(live) }
            }
        }
    }
    fun onDisconnect(player: ServerPlayer) { val id=player.uuid; profiles[id]?.let { scheduleSave(id, snapshot(it)) }; loaded.remove(id); profiles.remove(id) }
    fun isLoaded(id: UUID)=id in loaded
    fun get(id: UUID): NativeProfile?=profiles[id]
    fun mutate(id: UUID, mutation:(NativeProfile)->Unit):Boolean { val p=profiles[id]?:return false; mutation(p); p.touch(); scheduleSave(id, snapshot(p)); return true }
    fun shutdown(){if(!closed.compareAndSet(false,true))return;profiles.forEach{(id,p)->latestSnapshots[id]=snapshot(p)};latestSnapshots.keys.forEach(::queueWriter);io.shutdown();try{if(!io.awaitTermination(5,TimeUnit.SECONDS))io.shutdownNow()}catch(_:InterruptedException){io.shutdownNow();Thread.currentThread().interrupt()};profiles.clear();loaded.clear();queued.clear();latestSnapshots.clear()}

    private fun load(id:UUID):NativeProfile{
        val path=root.resolve("$id.json");if(!Files.exists(path))return NativeProfile()
        return runCatching{
            val o=gson.fromJson(Files.readString(path),JsonObject::class.java)?:return@runCatching NativeProfile()
            val p=NativeProfile(schema=2,arcadeTokens=o.number("arcadeTokens"),gachaTickets=o.number("gachaTickets").toInt(),pity=linkedMapOf(),stats=linkedMapOf(),lastUpdatedEpochMs=o.number("lastUpdatedEpochMs",System.currentTimeMillis()))
            o.getAsJsonObject("pity")?.entrySet()?.forEach{(k,v)->runCatching{v.asInt}.getOrNull()?.let{p.pity[k]=it}}
            o.getAsJsonObject("stats")?.entrySet()?.forEach{(k,v)->val st=runCatching{v.asJsonObject}.getOrNull()?:return@forEach;p.stats[k]=NativeGameStats(st.int("played"),st.int("wins"),st.int("losses"),st.int("draws"))}
            p
        }.onFailure{SVHub.LOGGER.warn("Unable to load native profile {}",id,it)}.getOrDefault(NativeProfile())
    }

    private fun snapshot(p: NativeProfile) = NativeProfile(
        schema = p.schema, arcadeTokens = p.arcadeTokens, gachaTickets = p.gachaTickets,
        pity = p.pity.toMutableMap(),
        stats = p.stats.mapValuesTo(linkedMapOf()) { (_, s) -> NativeGameStats(s.played, s.wins, s.losses, s.draws) },
        lastUpdatedEpochMs = p.lastUpdatedEpochMs
    )
    private fun JsonObject.number(key:String,fallback:Long=0L)=runCatching{get(key)?.asLong?:fallback}.getOrDefault(fallback)
    private fun JsonObject.int(key:String,fallback:Int=0)=runCatching{get(key)?.asInt?:fallback}.getOrDefault(fallback)
    private fun sanitize(p:NativeProfile):NativeProfile{p.schema=2;p.arcadeTokens=p.arcadeTokens.coerceIn(0,NativeProfile.MAX_BALANCE);p.gachaTickets=p.gachaTickets.coerceIn(0,1_000_000);p.pity=p.pity.mapValuesTo(linkedMapOf()){(_,v)->v.coerceIn(0,10_000)};return p}
    private fun scheduleSave(id:UUID,profileSnapshot:NativeProfile){if(!closed.get()){latestSnapshots[id]=profileSnapshot;queueWriter(id)}}
    private fun queueWriter(id:UUID){if(!queued.add(id))return;io.execute{try{while(true){val profile=latestSnapshots.remove(id)?:break;val json=runCatching{gson.toJson(profile)}.getOrElse{error->SVHub.LOGGER.warn("Unable to serialize native profile {}",id,error);continue};runCatching{AtomicFiles.writeUtf8(root.resolve("$id.json"),json)}.onFailure{SVHub.LOGGER.warn("Unable to save native profile {}",id,it)};if(!latestSnapshots.containsKey(id))break}}finally{queued.remove(id);if(latestSnapshots.containsKey(id)&&!closed.get())queueWriter(id)}}}
}
