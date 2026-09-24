package io.github.aristheg201.svarcade.client.cobblemon

import com.google.gson.Gson
import com.google.gson.JsonObject
import io.github.aristheg201.svarcade.network.PokemonRuntimeInfoC2S
import io.github.aristheg201.svarcade.network.PokemonRuntimeInfoS2C
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import java.util.concurrent.ConcurrentHashMap

object ClientPokemonRuntimeInfo {
    private val gson=Gson();private val cache=ConcurrentHashMap<String,JsonObject>();private val requestedAt=ConcurrentHashMap<String,Long>()
    fun getOrRequest(view:PokemonView):JsonObject?{cache[view.key]?.let{return it};val now=System.currentTimeMillis();val previous=requestedAt[view.key];if(previous==null||now-previous>=5_000L){requestedAt[view.key]=now;ClientPlayNetworking.send(PokemonRuntimeInfoC2S(view.key,view.speciesId,view.aspects.sorted().joinToString(",")))};return null}
    fun accept(payload:PokemonRuntimeInfoS2C){val parsed=runCatching{gson.fromJson(payload.json,JsonObject::class.java)}.getOrNull()?:return;cache[payload.key]=parsed;requestedAt.remove(payload.key)}
    fun clear(){cache.clear();requestedAt.clear()}
}
