package io.github.aristheg201.svhub.native.network
import com.google.gson.Gson
import com.google.gson.JsonObject
import io.github.aristheg201.svhub.SVHub
import io.github.aristheg201.svhub.native.NativeArcadeService
import io.github.aristheg201.svhub.native.NativePlatform
import io.github.aristheg201.svhub.native.TftLifecyclePolicy
import io.github.aristheg201.svhub.network.SVHubNetwork
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.server.level.ServerPlayer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
object NativePlatformNetwork{
 private data class Subscription(val module:String,val viewId:String)
 private val gson=Gson();private val openViews=ConcurrentHashMap<UUID,Subscription>();private val lastIntentAt=ConcurrentHashMap<UUID,Long>()
 fun registerCommon(){
  PayloadTypeRegistry.playS2C().register(NativeOpenS2C.TYPE,NativeOpenS2C.CODEC)
  PayloadTypeRegistry.playS2C().register(NativeStateS2C.TYPE,NativeStateS2C.CODEC)
  PayloadTypeRegistry.playS2C().register(NativeCloseS2C.TYPE,NativeCloseS2C.CODEC)
  PayloadTypeRegistry.playC2S().register(NativeIntentC2S.TYPE,NativeIntentC2S.CODEC)
  PayloadTypeRegistry.playC2S().register(NativeCloseC2S.TYPE,NativeCloseC2S.CODEC)
  PayloadTypeRegistry.playC2S().register(NativeResumeTftC2S.TYPE,NativeResumeTftC2S.CODEC)
  ServerPlayNetworking.registerGlobalReceiver(NativeIntentC2S.TYPE){payload,context->context.server().execute{
   val player=context.player();val sub=openViews[player.uuid]?:return@execute
   if(sub.viewId!=payload.viewId||sub.module!=payload.module)return@execute
   val now=System.currentTimeMillis();val previous=lastIntentAt[player.uuid]?:0L;if(now-previous<60L)return@execute;lastIntentAt[player.uuid]=now
   if(!payload.module.matches(ID)||!payload.action.matches(ID))return@execute
   val data=runCatching{gson.fromJson(payload.data,JsonObject::class.java)?:JsonObject()}.getOrElse{JsonObject()}
   runCatching{NativePlatform.handleIntent(player,payload.module,payload.action,data)}.onFailure{SVHub.LOGGER.warn("Native intent failed: player={} module={} action={}",player.uuid,payload.module,payload.action,it)}
  }}
  ServerPlayNetworking.registerGlobalReceiver(NativeCloseC2S.TYPE){payload,context->context.server().execute{
   val player=context.player();val module=currentModule(player.uuid)
   if(close(player.uuid,payload.viewId)&&module=="game"&&TftLifecyclePolicy.closeResigns(NativeArcadeService.activeGameId(player.uuid)))NativeArcadeService.leave(player)
  }}
  ServerPlayNetworking.registerGlobalReceiver(NativeResumeTftC2S.TYPE){_,context->context.server().execute{
   val player=context.player()
   if(NativeArcadeService.resumeActiveTft(player))NativePlatform.open(player,"arcade") else SVHubNetwork.open(player,"home",false)
  }}
 }
 fun sendOpen(player:ServerPlayer,module:String,state:JsonObject):String{val previous=openViews[player.uuid];val viewId=UUID.randomUUID().toString();openViews[player.uuid]=Subscription(module,viewId);lastIntentAt.remove(player.uuid);ServerPlayNetworking.send(player,NativeOpenS2C(module,gson.toJson(state),viewId,previous?.viewId.orEmpty()));return viewId}
 fun sendState(player:ServerPlayer,module:String,state:JsonObject,message:String=""){val sub=openViews[player.uuid]?:return;if(sub.module!=module)return;ServerPlayNetworking.send(player,NativeStateS2C(module,gson.toJson(state),message.take(512),sub.viewId))}
 fun sendClose(player:ServerPlayer,reason:String=""){val sub=openViews.remove(player.uuid)?:return;lastIntentAt.remove(player.uuid);ServerPlayNetworking.send(player,NativeCloseS2C(sub.viewId,reason.take(512)))}
 fun currentModule(id:UUID):String?=openViews[id]?.module
 fun currentViewId(id:UUID):String?=openViews[id]?.viewId
 fun close(id:UUID,expectedViewId:String?=null):Boolean{val sub=openViews[id]?:return false;if(expectedViewId!=null&&sub.viewId!=expectedViewId)return false;val removed=openViews.remove(id,sub);if(removed)lastIntentAt.remove(id);return removed}
 private val ID=Regex("^[a-z0-9_.:-]{1,48}$")
}
