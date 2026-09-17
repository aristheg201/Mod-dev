package io.github.aristheg201.svhub.server

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.spawning.CobblemonSpawnPools
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.github.aristheg201.svhub.SVHub
import io.github.aristheg201.svhub.network.PokemonRuntimeInfoC2S
import io.github.aristheg201.svhub.network.PokemonRuntimeInfoS2C
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Server-authoritative spawn + evolution data for both official Pokemon and Fakemon. */
object PokemonRuntimeInfoService {
    private data class Request(val player: UUID, val key: String, val species: String, val aspects: Set<String>)
    private data class Spawn(val id:String,val species:String,val aspects:Set<String>,val form:String,val bucket:String,val weight:Double,val level:String,val context:String,val conditions:List<String>,val herd:String?)
    private val gson=GsonBuilder().disableHtmlEscaping().create()
    private val queued=ConcurrentHashMap<String,Request>()
    private val lastRequest=ConcurrentHashMap<UUID,Long>()
    private val building=AtomicBoolean(false)
    private val threadCounter=AtomicInteger()
    @Volatile private var spawnIndex:Map<String,List<Spawn>>?=null
    @Volatile private var executor:ThreadPoolExecutor?=null

    fun start(){if(executor!=null)return;synchronized(this){if(executor==null)executor=ThreadPoolExecutor(1,2,30,TimeUnit.SECONDS,ArrayBlockingQueue(128),{r->Thread(r,"SVHub-Dex-${threadCounter.incrementAndGet()}").apply{isDaemon=true;priority=Thread.NORM_PRIORITY-1}},ThreadPoolExecutor.AbortPolicy()).apply{allowCoreThreadTimeOut(true)}}}
    fun invalidate(){spawnIndex=null;queued.clear();building.set(false)}
    fun onDisconnect(id:UUID){lastRequest.remove(id);queued.entries.removeIf{it.value.player==id}}
    fun shutdown(){invalidate();val p=synchronized(this){val x=executor;executor=null;x};p?.shutdownNow()}

    fun request(player:ServerPlayer,payload:PokemonRuntimeInfoC2S){
        val id=ResourceLocation.tryParse(payload.speciesId)?:return
        val species=PokemonSpecies.getByIdentifier(id)?:return
        val key=payload.key.take(384);if(key.isBlank())return
        val aspects=payload.aspects.split(',').map(String::trim).filter{it.matches(ASPECT)}.take(32).toSet()
        val now=System.currentTimeMillis();val prev=lastRequest[player.uuid];if(prev!=null&&now-prev<120)return;lastRequest[player.uuid]=now
        val req=Request(player.uuid,key,id.toString(),aspects)
        spawnIndex?.let{dispatch(player.server,req,it,species);return}
        queued["${player.uuid}:$key"]=req;ensureIndex(player.server)
    }

    private fun ensureIndex(server:MinecraftServer){if(!building.compareAndSet(false,true))return;val pool=executor?:run{building.set(false);return};val details=runCatching{CobblemonSpawnPools.WORLD_SPAWN_POOL.details.toList()}.getOrElse{SVHub.LOGGER.warn("Unable to snapshot Cobblemon spawn pool",it);emptyList()};try{pool.execute{val built=runCatching{buildIndex(details)};server.execute{building.set(false);built.onSuccess{idx->spawnIndex=idx;val requests=queued.values.toList();queued.clear();requests.forEach{r->ResourceLocation.tryParse(r.species)?.let(PokemonSpecies::getByIdentifier)?.let{s->dispatch(server,r,idx,s)}}}.onFailure{SVHub.LOGGER.warn("Unable to build SVHub spawn index",it)}}}}catch(_:RejectedExecutionException){building.set(false)}}

    private fun dispatch(server:MinecraftServer,req:Request,index:Map<String,List<Spawn>>,species:com.cobblemon.mod.common.pokemon.Species){
        val form=runCatching{species.getForm(req.aspects)}.getOrElse{species.standardForm}
        val evos=runCatching{(species.evolutions.toList()+form.evolutions.toList()).distinctBy{runCatching{it.id.toString()}.getOrElse{it.toString()}}.map{it as Any}}.getOrDefault(emptyList())
        val pre=runCatching{form.preEvolution?.species?.translatedName?.string}.getOrNull()
        val name=runCatching{species.translatedName.string}.getOrDefault(req.species)
        val spawns=index[req.species.lowercase()].orEmpty();val pool=executor?:return
        try{pool.execute{val response=JsonObject().apply{
            addProperty("species",req.species);addProperty("name",name);pre?.let{addProperty("preEvolution",it)}
            add("evolutions",JsonArray().also{a->evos.take(32).forEach{a.add(evolutionJson(it))}})
            val all=spawns.filter{s->req.aspects.isEmpty()||s.aspects.isEmpty()||s.aspects.containsAll(req.aspects)||req.aspects.any{s.form.equals(it,true)}}.sortedWith(compareBy<Spawn>({bucketOrder(it.bucket)},{-it.weight}))
            addProperty("spawnCount",all.size);addProperty("spawnIndexSource","Cobblemon runtime spawn pool");add("spawns",JsonArray().also{a->all.take(48).forEach{a.add(spawnJson(it))}})
        };val json=bounded(response);server.execute{server.playerList.getPlayer(req.player)?.let{ServerPlayNetworking.send(it,PokemonRuntimeInfoS2C(req.key,json))}}}}catch(_:RejectedExecutionException){}
    }

    private fun buildIndex(details:List<Any>):Map<String,List<Spawn>>{val out=linkedMapOf<String,MutableList<Spawn>>();details.forEach{d->runCatching{val p=call(d,"getPokemon");if(p!=null){record(d,p,null)?.let{out.getOrPut(it.species){mutableListOf()}.add(it)};return@runCatching};val members=list(call(d,"getHerdablePokemon"));if(members.isNotEmpty()){val max=text(call(d,"getMaxHerdSize"));members.forEach{m->val props=call(m,"getPokemon")?:return@forEach;val role=when{bool(call(m,"isLeader"))==true||bool(call(m,"getLeader"))==true->"leader";bool(call(m,"isFollower"))==true||bool(call(m,"getFollower"))==true->"follower";else->"member"};val herd="$role${if(bool(call(m,"isAlpha"))==true)" • alpha" else ""}${if(max.isNotBlank())" • max $max" else ""}";record(d,props,herd)?.let{out.getOrPut(it.species){mutableListOf()}.add(it)}}}}};return out.mapValues{it.value.toList()}}

    private fun record(d:Any,p:Any,herd:String?):Spawn?{val raw=text(call(p,"getSpecies")).lowercase();if(raw.isBlank())return null;val species=if(':' in raw)raw else "cobblemon:$raw";val aspects=list(call(p,"getAspects")).map(::text).filter(String::isNotBlank).toSet();val form=text(call(p,"getForm"));val bucket=bucket(call(d,"getBucket"));val weight=num(call(d,"getWeight"))?:1.0;val level=range(call(d,"getLevelRange")).ifBlank{range(call(p,"getLevelRange"))}.ifBlank{"1-100"};val context=text(call(d,"getContext")).ifBlank{"grounded"};val conditions=buildList{list(call(d,"getConditions")).forEach{addAll(condition(it))};call(d,"getCondition")?.let{addAll(condition(it))};call(d,"getCompositeCondition")?.let{c->list(call(c,"getConditions")).forEach{addAll(condition(it))}};val anti=list(call(d,"getAnticonditions")).flatMap(::condition);if(anti.isNotEmpty())add("Exclude: ${anti.distinct().take(8).joinToString("; ")}")}.distinct().take(24);return Spawn(text(call(d,"getId")).ifBlank{"$species:${bucket.lowercase()}"},species,aspects,form,bucket,weight,level,context,conditions,herd)}

    private fun condition(c:Any)=buildList<String>{values(c,"getBiomes")?.takeIf{it.isNotEmpty()}?.let{add("Biome: ${it.joinToString()}")};values(c,"getDimensions")?.takeIf{it.isNotEmpty()}?.let{add("Dimension: ${it.joinToString()}")};values(c,"getStructures")?.takeIf{it.isNotEmpty()}?.let{add("Structure: ${it.joinToString()}")};range(call(c,"getTimeRange")).takeIf(String::isNotBlank)?.let{add("Time: $it")};val rain=bool(call(c,"getIsRaining"));val thunder=bool(call(c,"getIsThundering"));if(rain!=null||thunder!=null)add("Weather: rain=${rain?:"any"}, thunder=${thunder?:"any"}");bool(call(c,"getCanSeeSky"))?.let{add("Sky: ${if(it)"visible" else "covered"}")};num(call(c,"getMinLight"))?.let{add("Light: ${it.toInt()}-${num(call(c,"getMaxLight"))?.toInt()?:15}")};val minY=num(call(c,"getMinY"))?.toInt();val maxY=num(call(c,"getMaxY"))?.toInt();if(minY!=null||maxY!=null)add("Y: ${minY?:"*"}-${maxY?:"*"}");values(c,"getNeededBaseBlocks")?.takeIf{it.isNotEmpty()}?.let{add("Base: ${it.joinToString()}")};values(c,"getNeededNearbyBlocks")?.takeIf{it.isNotEmpty()}?.let{add("Nearby: ${it.joinToString()}")};text(call(c,"getFluid")).takeIf(String::isNotBlank)?.let{add("Fluid: $it")};num(call(c,"getMinLureLevel"))?.let{add("Lure: ${it.toInt()}+")}}

    private fun evolutionJson(e:Any)=JsonObject().apply{val result=call(e,"getResult");addProperty("to",text(call(result,"getSpecies")).ifBlank{"Unknown"});add("aspects",JsonArray().also{a->list(call(result,"getAspects")).map(::text).filter(String::isNotBlank).forEach(a::add)});addProperty("variant",e.javaClass.simpleName.removeSuffix("Evolution").replace(Regex("([a-z])([A-Z])"),"$1 $2").ifBlank{"evolution"});add("requirements",JsonArray().also{a->list(call(e,"getRequirements")).take(24).forEach{a.add(requirement(it))}});text(call(e,"getRequiredContext")).takeIf(String::isNotBlank)?.let{addProperty("context",it)};bool(call(e,"getConsumeHeldItem"))?.let{addProperty("consumeHeldItem",it)}}
    private fun requirement(r:Any):String{val title=r.javaClass.simpleName.removeSuffix("Requirement").replace(Regex("([a-z])([A-Z])"),"$1 $2").ifBlank{"Requirement"};val fields=r.javaClass.methods.asSequence().filter{it.parameterCount==0&&it.name!="getClass"&&(it.name.startsWith("get")||it.name.startsWith("is"))}.mapNotNull{m->val v=runCatching{m.invoke(r)}.getOrNull()?:return@mapNotNull null;val s=compact(v);if(s.isBlank()||s=="false"||s=="0")null else "${m.name.removePrefix("get").removePrefix("is").replaceFirstChar(Char::lowercase)}=$s"}.distinct().take(10).toList();return if(fields.isEmpty())title else "$title • ${fields.joinToString(" • ")}".take(600)}
    private fun spawnJson(s:Spawn)=JsonObject().apply{addProperty("id",s.id);addProperty("bucket",s.bucket);addProperty("weight",s.weight);addProperty("level",s.level);addProperty("context",s.context);addProperty("form",s.form);add("aspects",JsonArray().also{a->s.aspects.sorted().forEach(a::add)});add("conditions",JsonArray().also{a->s.conditions.forEach(a::add)});s.herd?.let{addProperty("herd",it)}}
    private fun bounded(o:JsonObject):String{var s=gson.toJson(o);if(s.length<=MAX)return s;val sp=o.getAsJsonArray("spawns");while(sp!=null&&sp.size()>8&&s.length>MAX){sp.remove(sp.size()-1);s=gson.toJson(o)};val ev=o.getAsJsonArray("evolutions");while(ev!=null&&ev.size()>4&&s.length>MAX){ev.remove(ev.size()-1);s=gson.toJson(o)};if(s.length<=MAX)return s;return gson.toJson(JsonObject().apply{addProperty("species",o.get("species")?.asString?:"");addProperty("name",o.get("name")?.asString?:"");addProperty("spawnCount",o.get("spawnCount")?.asInt?:0);addProperty("truncated",true);add("evolutions",JsonArray());add("spawns",JsonArray())})}
    private fun bucket(x:Any?):String=text(call(x,"getName")).ifBlank{text(x)}.ifBlank{"common"};private fun bucketOrder(s:String)=when(s.lowercase()){ "common"->0;"uncommon"->1;"rare"->2;"ultra-rare","ultra_rare"->3;else->4 }
    private fun values(o:Any,g:String)=call(o,g)?.let{list(it).map(::compact).filter(String::isNotBlank).distinct()};private fun range(x:Any?):String{if(x==null)return "";val rs=list(call(x,"getRanges"));if(rs.isNotEmpty())return rs.joinToString(", "){compact(it)};val a=text(call(x,"getFirst"));val b=text(call(x,"getLast"));return if(a.isNotBlank()||b.isNotBlank())"$a-$b" else text(x).takeUnless{it.contains('@')}?:""}
    private fun compact(x:Any?):String=when(x){null->"";is String->x;is Number,is Boolean,is Enum<*>->x.toString();is Iterable<*>->x.filterNotNull().take(12).joinToString(", "){compact(it)};is Array<*>->x.filterNotNull().take(12).joinToString(", "){compact(it)};else->text(call(x,"getIdentifier")).ifBlank{text(call(x,"getId"))}.ifBlank{x.toString().replace(Regex("@[0-9a-fA-F]+$"),"").take(200)}}
    private fun text(x:Any?)=when(x){null->"";is String->x;is Number,is Boolean,is Enum<*>->x.toString();else->x.toString().replace(Regex("@[0-9a-fA-F]+$"),"")};private fun num(x:Any?)=if(x is Number)x.toDouble() else x?.toString()?.toDoubleOrNull();private fun bool(x:Any?)=if(x is Boolean)x else x?.toString()?.toBooleanStrictOrNull();private fun list(x:Any?)=when(x){is Iterable<*>->x.filterNotNull();is Array<*>->x.filterNotNull();else->emptyList()};private fun call(x:Any?,name:String):Any?{if(x==null)return null;val m=x.javaClass.methods.firstOrNull{it.name==name&&it.parameterCount==0}?:return null;return runCatching{m.invoke(x)}.getOrNull()}
    private const val MAX=128*1024
    private val ASPECT=Regex("^[a-zA-Z0-9_.:-]{1,96}$")
}
