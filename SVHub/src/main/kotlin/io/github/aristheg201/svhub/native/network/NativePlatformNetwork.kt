package io.github.aristheg201.svhub.native.network
import com.google.gson.Gson
import com.google.gson.JsonObject
import io.github.aristheg201.svhub.SVHub
import io.github.aristheg201.svhub.native.NativePlatform
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.server.level.ServerPlayer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
object NativePlatformNetwork{
 private val gson=Gson();private val openModules=ConcurrentHashMap<UUID,String>();private val lastIntentAt=ConcurrentHashMap<UUID,Long>()
 fun registerCommon(){PayloadTypeRegistry.playS2C().register(NativeOpenS2C.TYPE,NativeOpenS2C.CODEC);PayloadTypeRegistry.playS2C().register(NativeStateS2C.TYPE,NativeStateS2C.CODEC);PayloadTypeRegistry.playC2S().register(NativeIntentC2S.TYPE,NativeIntentC2S.CODEC);ServerPlayNetworking.registerGlobalReceiver(NativeIntentC2S.TYPE){payload,context->context.server().execute{val player=context.player();val now=System.currentTimeMillis();val previous=lastIntentAt[player.uuid]?:0L;if(now-previous<60L)return@execute;lastIntentAt[player.uuid]=now;if(!payload.module.matches(ID)||!payload.action.matches(ID))return@execute;val data=runCatching{gson.fromJson(payload.data,JsonObject::class.java)?:JsonObject()}.getOrElse{JsonObject()};runCatching{NativePlatform.handleIntent(player,payload.module,payload.action,data)}.onFailure{SVHub.LOGGER.warn("Native intent failed: player={} module={} action={}",player.uuid,payload.module,payload.action,it)}}}}
 fun sendOpen(player:ServerPlayer,module:String,state:JsonObject){openModules[player.uuid]=module;ServerPlayNetworking.send(player,NativeOpenS2C(module,gson.toJson(state)))}
 fun sendState(player:ServerPlayer,module:String,state:JsonObject,message:String=""){if(openModules[player.uuid]!=module)return;ServerPlayNetworking.send(player,NativeStateS2C(module,gson.toJson(state),message.take(512)))}
 fun currentModule(id:UUID):String?=openModules[id];fun close(id:UUID){openModules.remove(id);lastIntentAt.remove(id)}
 private val ID=Regex("^[a-z0-9_.:-]{1,48}$")
}
