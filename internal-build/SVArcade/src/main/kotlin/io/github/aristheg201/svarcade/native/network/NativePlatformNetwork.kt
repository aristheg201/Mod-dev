package io.github.aristheg201.svarcade.native.network
import com.google.gson.Gson
import com.google.gson.JsonObject
import io.github.aristheg201.svarcade.SVArcade
import io.github.aristheg201.svarcade.native.NativeArcadeService
import io.github.aristheg201.svarcade.native.NativePlatform
import io.github.aristheg201.svarcade.native.TftLifecyclePolicy
import io.github.aristheg201.svarcade.network.SVArcadeNetwork
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.server.level.ServerPlayer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
object NativePlatformNetwork{
 private data class Subscription(val module:String,val viewId:String,val replication:NativeReplicationTracker=NativeReplicationTracker(),var state:JsonObject=JsonObject())
 private val gson=Gson();private val openViews=ConcurrentHashMap<UUID,Subscription>();private val lastIntentAt=ConcurrentHashMap<UUID,Long>();private val pendingTftPreview=ConcurrentHashMap<String,UUID>()
 fun registerCommon(){
  PayloadTypeRegistry.playS2C().register(NativeOpenS2C.TYPE,NativeOpenS2C.CODEC)
  PayloadTypeRegistry.playS2C().register(NativeStateS2C.TYPE,NativeStateS2C.CODEC)
  PayloadTypeRegistry.playS2C().register(NativeDeltaS2C.TYPE,NativeDeltaS2C.CODEC)
  PayloadTypeRegistry.playS2C().register(NativeCloseS2C.TYPE,NativeCloseS2C.CODEC)
  PayloadTypeRegistry.playC2S().register(NativeIntentC2S.TYPE,NativeIntentC2S.CODEC)
  PayloadTypeRegistry.playC2S().register(NativeCloseC2S.TYPE,NativeCloseC2S.CODEC)
  PayloadTypeRegistry.playC2S().register(NativeResumeTftC2S.TYPE,NativeResumeTftC2S.CODEC)
  PayloadTypeRegistry.playS2C().register(NativeTftPreviewS2C.TYPE,NativeTftPreviewS2C.CODEC)
  PayloadTypeRegistry.playC2S().register(NativeTftPreviewResultC2S.TYPE,NativeTftPreviewResultC2S.CODEC)
  ServerPlayNetworking.registerGlobalReceiver(NativeIntentC2S.TYPE){payload,context->context.server().execute{
   val player=context.player();val sub=openViews[player.uuid]?:return@execute
   if(sub.viewId!=payload.viewId||sub.module!=payload.module)return@execute
   val now=System.currentTimeMillis();val previous=lastIntentAt[player.uuid]?:0L;if(now-previous<60L)return@execute;lastIntentAt[player.uuid]=now
   if(!payload.module.matches(ID)||!payload.action.matches(ID))return@execute
   val data=runCatching{gson.fromJson(payload.data,JsonObject::class.java)?:JsonObject()}.getOrElse{JsonObject()}
   runCatching{NativePlatform.handleIntent(player,payload.module,payload.action,data)}.onFailure{SVArcade.LOGGER.warn("Native intent failed: player={} module={} action={}",player.uuid,payload.module,payload.action,it)}
  }}
  ServerPlayNetworking.registerGlobalReceiver(NativeCloseC2S.TYPE){payload,context->context.server().execute{
   val player=context.player();val module=currentModule(player.uuid)
   if(close(player.uuid,payload.viewId)&&module=="game"&&TftLifecyclePolicy.closeResigns(NativeArcadeService.activeGameId(player.uuid)))NativeArcadeService.leave(player)
  }}
  ServerPlayNetworking.registerGlobalReceiver(NativeResumeTftC2S.TYPE){_,context->context.server().execute{
   val player=context.player()
   if(NativeArcadeService.resumeActiveTft(player))NativePlatform.open(player,"arcade") else NativePlatform.open(player,"arcade")
  }}
  ServerPlayNetworking.registerGlobalReceiver(NativeTftPreviewResultC2S.TYPE){payload,context->context.server().execute{
   val player=context.player()
   if(pendingTftPreview.remove(payload.requestId)!=player.uuid)return@execute
   player.sendSystemMessage(net.minecraft.network.chat.Component.literal(payload.message.take(4096)))
  }}
 }
 fun sendOpen(player:ServerPlayer,module:String,state:JsonObject):String{val previous=openViews[player.uuid];val viewId=UUID.randomUUID().toString();val json=gson.toJson(state);val tracker=NativeReplicationTracker();tracker.recordFull(utf8Length(json));openViews[player.uuid]=Subscription(module,viewId,tracker,state.deepCopy());lastIntentAt.remove(player.uuid);ServerPlayNetworking.send(player,NativeOpenS2C(module,json,viewId,previous?.viewId.orEmpty()));return viewId}
 fun sendState(player:ServerPlayer,module:String,state:JsonObject,message:String=""){
  val sub=openViews[player.uuid]?:return
  if(sub.module!=module)return
  val patch=NativeJsonDelta.diff(sub.state,state);val safeMessage=message.take(512)
  if(patch.isEmpty&&safeMessage.isEmpty()){sub.replication.recordNoOp();return}
  val changed=gson.toJson(patch.changed);val removed=patch.removed.joinToString("\u0000")
  sub.state=state.deepCopy();sub.replication.recordDelta(utf8Length(changed)+utf8Length(removed)+utf8Length(safeMessage),countLeaves(patch.changed)+patch.removed.size)
  ServerPlayNetworking.send(player,NativeDeltaS2C(module,changed,removed,safeMessage,sub.viewId))
 }
 fun sendClose(player:ServerPlayer,reason:String=""){val sub=openViews.remove(player.uuid)?:return;lastIntentAt.remove(player.uuid);ServerPlayNetworking.send(player,NativeCloseS2C(sub.viewId,reason.take(512)))}
 fun requestTftPreview(player:ServerPlayer,label:String,species:String,aspects:Set<String>,semantic:String):String{
  val requestId=UUID.randomUUID().toString()
  pendingTftPreview[requestId]=player.uuid
  ServerPlayNetworking.send(player,NativeTftPreviewS2C(requestId,label.take(96),species.take(160),aspects.sorted().joinToString(",").take(1024),semantic.take(64)))
  return requestId
 }
 fun currentModule(id:UUID):String?=openViews[id]?.module
 fun currentViewId(id:UUID):String?=openViews[id]?.viewId
 data class Metrics(val subscriptions:Int,val sentPackets:Long,val suppressedPackets:Long,val serializedPayloadBytes:Long,val packetsPerSecond:Long,val bytesPerSecond:Long,val fullSnapshots:Long,val deltaPackets:Long,val componentsReplicated:Long,val replicationFlushes:Long,val noOpFlushes:Long,val averageDeltaBytes:Long,val maximumDeltaBytes:Long)
 fun metrics():Metrics{
  val values=openViews.values.map{it.replication.metrics()}
  val deltas=values.sumOf{it.deltaPackets};val deltaBytes=values.sumOf{it.averageDeltaBytes*it.deltaPackets}
  return Metrics(values.size,values.sumOf{it.sentPackets},values.sumOf{it.suppressedPackets},values.sumOf{it.sentBytes},values.sumOf{it.packetsPerSecond},values.sumOf{it.bytesPerSecond},values.sumOf{it.fullSnapshots},deltas,values.sumOf{it.componentsReplicated},values.sumOf{it.replicationFlushes},values.sumOf{it.noOpFlushes},if(deltas==0L)0 else deltaBytes/deltas,values.maxOfOrNull{it.maximumDeltaBytes}?:0)
 }
 fun close(id:UUID,expectedViewId:String?=null):Boolean{val sub=openViews[id]?:return false;if(expectedViewId!=null&&sub.viewId!=expectedViewId)return false;val removed=openViews.remove(id,sub);if(removed)lastIntentAt.remove(id);return removed}
 private val ID=Regex("^[a-z0-9_.:-]{1,48}$")
 private fun countLeaves(value:com.google.gson.JsonElement):Int=if(value.isJsonObject)value.asJsonObject.entrySet().sumOf{countLeaves(it.value)} else 1
 private fun utf8Length(value:String):Long{var bytes=0L;var i=0;while(i<value.length){val c=value[i];bytes+=when{c.code<=0x7f->1;c.code<=0x7ff->2;c.isHighSurrogate()&&i+1<value.length&&value[i+1].isLowSurrogate()->{i++;4};else->3};i++};return bytes}
}
