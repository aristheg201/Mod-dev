package io.github.aristheg201.svhub.native.network
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
private fun id(path:String)=ResourceLocation.fromNamespaceAndPath("svhub",path)
private const val MODULE_MAX=32;private const val ACTION_MAX=48;private const val VIEW_MAX=64;private const val STATE_MAX=96*1024;private const val INTENT_MAX=8*1024;private const val MESSAGE_MAX=512
data class NativeOpenS2C(val module:String,val state:String,val viewId:String,val replacesViewId:String):CustomPacketPayload{override fun type()=TYPE;companion object{val TYPE=CustomPacketPayload.Type<NativeOpenS2C>(id("native_open_s2c"));val CODEC:StreamCodec<RegistryFriendlyByteBuf, NativeOpenS2C> = StreamCodec.of({b,p->b.writeUtf(p.module,MODULE_MAX);b.writeUtf(p.state,STATE_MAX);b.writeUtf(p.viewId,VIEW_MAX);b.writeUtf(p.replacesViewId,VIEW_MAX)},{b->NativeOpenS2C(b.readUtf(MODULE_MAX),b.readUtf(STATE_MAX),b.readUtf(VIEW_MAX),b.readUtf(VIEW_MAX))})}}
data class NativeStateS2C(val module:String,val state:String,val message:String,val viewId:String):CustomPacketPayload{override fun type()=TYPE;companion object{val TYPE=CustomPacketPayload.Type<NativeStateS2C>(id("native_state_s2c"));val CODEC:StreamCodec<RegistryFriendlyByteBuf, NativeStateS2C> = StreamCodec.of({b,p->b.writeUtf(p.module,MODULE_MAX);b.writeUtf(p.state,STATE_MAX);b.writeUtf(p.message,MESSAGE_MAX);b.writeUtf(p.viewId,VIEW_MAX)},{b->NativeStateS2C(b.readUtf(MODULE_MAX),b.readUtf(STATE_MAX),b.readUtf(MESSAGE_MAX),b.readUtf(VIEW_MAX))})}}
data class NativeCloseS2C(val viewId:String,val reason:String):CustomPacketPayload{override fun type()=TYPE;companion object{val TYPE=CustomPacketPayload.Type<NativeCloseS2C>(id("native_close_s2c"));val CODEC:StreamCodec<RegistryFriendlyByteBuf, NativeCloseS2C> = StreamCodec.of({b,p->b.writeUtf(p.viewId,VIEW_MAX);b.writeUtf(p.reason,MESSAGE_MAX)},{b->NativeCloseS2C(b.readUtf(VIEW_MAX),b.readUtf(MESSAGE_MAX))})}}
data class NativeIntentC2S(val module:String,val action:String,val data:String,val viewId:String):CustomPacketPayload{override fun type()=TYPE;companion object{val TYPE=CustomPacketPayload.Type<NativeIntentC2S>(id("native_intent_c2s"));val CODEC:StreamCodec<RegistryFriendlyByteBuf, NativeIntentC2S> = StreamCodec.of({b,p->b.writeUtf(p.module,MODULE_MAX);b.writeUtf(p.action,ACTION_MAX);b.writeUtf(p.data,INTENT_MAX);b.writeUtf(p.viewId,VIEW_MAX)},{b->NativeIntentC2S(b.readUtf(MODULE_MAX),b.readUtf(ACTION_MAX),b.readUtf(INTENT_MAX),b.readUtf(VIEW_MAX))})}}
data class NativeCloseC2S(val viewId:String):CustomPacketPayload{override fun type()=TYPE;companion object{val TYPE=CustomPacketPayload.Type<NativeCloseC2S>(id("native_close_c2s"));val CODEC:StreamCodec<RegistryFriendlyByteBuf, NativeCloseC2S> = StreamCodec.of({b,p->b.writeUtf(p.viewId,VIEW_MAX)},{b->NativeCloseC2S(b.readUtf(VIEW_MAX))})}}
class NativeResumeTftC2S:CustomPacketPayload{override fun type()=TYPE;companion object{val TYPE=CustomPacketPayload.Type<NativeResumeTftC2S>(id("native_resume_tft_c2s"));val CODEC:StreamCodec<RegistryFriendlyByteBuf,NativeResumeTftC2S> = StreamCodec.unit(NativeResumeTftC2S())}}

data class NativeTftPreviewS2C(val requestId:String,val label:String,val species:String,val aspects:String,val semantic:String):CustomPacketPayload{
 override fun type()=TYPE
 companion object{
  val TYPE=CustomPacketPayload.Type<NativeTftPreviewS2C>(id("native_tft_preview_s2c"))
  val CODEC:StreamCodec<RegistryFriendlyByteBuf,NativeTftPreviewS2C> = StreamCodec.of(
   {b,p->b.writeUtf(p.requestId,VIEW_MAX);b.writeUtf(p.label,96);b.writeUtf(p.species,160);b.writeUtf(p.aspects,1024);b.writeUtf(p.semantic,64)},
   {b->NativeTftPreviewS2C(b.readUtf(VIEW_MAX),b.readUtf(96),b.readUtf(160),b.readUtf(1024),b.readUtf(64))}
  )
 }
}
data class NativeTftPreviewResultC2S(val requestId:String,val message:String):CustomPacketPayload{
 override fun type()=TYPE
 companion object{
  val TYPE=CustomPacketPayload.Type<NativeTftPreviewResultC2S>(id("native_tft_preview_result_c2s"))
  val CODEC:StreamCodec<RegistryFriendlyByteBuf,NativeTftPreviewResultC2S> = StreamCodec.of(
   {b,p->b.writeUtf(p.requestId,VIEW_MAX);b.writeUtf(p.message,4096)},
   {b->NativeTftPreviewResultC2S(b.readUtf(VIEW_MAX),b.readUtf(4096))}
  )
 }
}
