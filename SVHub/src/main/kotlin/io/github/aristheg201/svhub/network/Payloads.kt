package io.github.aristheg201.svhub.network

import io.github.aristheg201.svhub.util.Compression
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation

private fun id(path: String) = ResourceLocation.fromNamespaceAndPath("svhub", path)

private object PayloadLimits {
    const val SERVER_MANIFEST_CHARS = 128 * 1024
    const val CLIENT_MANIFEST_CHARS = 32 * 1024
    const val OPEN_PAGE_CHARS = 256
    const val EDITOR_MESSAGE_CHARS = 1024
    const val ACTION_ID_CHARS = 192
    const val POKEMON_KEY_CHARS = 384
    const val POKEMON_SPECIES_CHARS = 256
    const val POKEMON_ASPECTS_CHARS = 1024
    const val POKEMON_INFO_CHARS = 128 * 1024
}

data class HubHelloS2C(val protocol:Int,val revision:Long,val canOpen:Boolean,val canEdit:Boolean,val cacheable:Boolean,val serverManifest:String):CustomPacketPayload{override fun type()=TYPE;companion object{val TYPE=CustomPacketPayload.Type<HubHelloS2C>(id("hello_s2c"));val CODEC:StreamCodec<RegistryFriendlyByteBuf,HubHelloS2C> = StreamCodec.of({b,p->b.writeVarInt(p.protocol);b.writeLong(p.revision);b.writeBoolean(p.canOpen);b.writeBoolean(p.canEdit);b.writeBoolean(p.cacheable);b.writeUtf(p.serverManifest,PayloadLimits.SERVER_MANIFEST_CHARS)},{b->HubHelloS2C(b.readVarInt(),b.readLong(),b.readBoolean(),b.readBoolean(),b.readBoolean(),b.readUtf(PayloadLimits.SERVER_MANIFEST_CHARS))})}}
data class HubSnapshotChunkS2C(val transferId:Long,val revision:Long,val editor:Boolean,val index:Int,val total:Int,val chunk:String):CustomPacketPayload{override fun type()=TYPE;companion object{val TYPE=CustomPacketPayload.Type<HubSnapshotChunkS2C>(id("snapshot_chunk_s2c"));val CODEC:StreamCodec<RegistryFriendlyByteBuf,HubSnapshotChunkS2C> = StreamCodec.of({b,p->b.writeLong(p.transferId);b.writeLong(p.revision);b.writeBoolean(p.editor);b.writeVarInt(p.index);b.writeVarInt(p.total);b.writeUtf(p.chunk,Compression.CHUNK_CHARS)},{b->HubSnapshotChunkS2C(b.readLong(),b.readLong(),b.readBoolean(),b.readVarInt(),b.readVarInt(),b.readUtf(Compression.CHUNK_CHARS))})}}
data class HubOpenS2C(val page:String,val editor:Boolean):CustomPacketPayload{override fun type()=TYPE;companion object{val TYPE=CustomPacketPayload.Type<HubOpenS2C>(id("open_s2c"));val CODEC:StreamCodec<RegistryFriendlyByteBuf,HubOpenS2C> = StreamCodec.of({b,p->b.writeUtf(p.page,PayloadLimits.OPEN_PAGE_CHARS);b.writeBoolean(p.editor)},{b->HubOpenS2C(b.readUtf(PayloadLimits.OPEN_PAGE_CHARS),b.readBoolean())})}}
data class HubEditorResultS2C(val ok:Boolean,val revision:Long,val message:String):CustomPacketPayload{override fun type()=TYPE;companion object{val TYPE=CustomPacketPayload.Type<HubEditorResultS2C>(id("editor_result_s2c"));val CODEC:StreamCodec<RegistryFriendlyByteBuf,HubEditorResultS2C> = StreamCodec.of({b,p->b.writeBoolean(p.ok);b.writeLong(p.revision);b.writeUtf(p.message,PayloadLimits.EDITOR_MESSAGE_CHARS)},{b->HubEditorResultS2C(b.readBoolean(),b.readLong(),b.readUtf(PayloadLimits.EDITOR_MESSAGE_CHARS))})}}
data class HubClientManifestC2S(val protocol:Int,val cachedRevision:Long,val clientManifest:String):CustomPacketPayload{override fun type()=TYPE;companion object{val TYPE=CustomPacketPayload.Type<HubClientManifestC2S>(id("manifest_c2s"));val CODEC:StreamCodec<RegistryFriendlyByteBuf,HubClientManifestC2S> = StreamCodec.of({b,p->b.writeVarInt(p.protocol);b.writeLong(p.cachedRevision);b.writeUtf(p.clientManifest,PayloadLimits.CLIENT_MANIFEST_CHARS)},{b->HubClientManifestC2S(b.readVarInt(),b.readLong(),b.readUtf(PayloadLimits.CLIENT_MANIFEST_CHARS))})}}
data class HubActionC2S(val actionId:String):CustomPacketPayload{override fun type()=TYPE;companion object{val TYPE=CustomPacketPayload.Type<HubActionC2S>(id("action_c2s"));val CODEC:StreamCodec<RegistryFriendlyByteBuf,HubActionC2S> = StreamCodec.of({b,p->b.writeUtf(p.actionId,PayloadLimits.ACTION_ID_CHARS)},{b->HubActionC2S(b.readUtf(PayloadLimits.ACTION_ID_CHARS))})}}
data class HubRequestSnapshotC2S(val editor:Boolean):CustomPacketPayload{override fun type()=TYPE;companion object{val TYPE=CustomPacketPayload.Type<HubRequestSnapshotC2S>(id("request_snapshot_c2s"));val CODEC:StreamCodec<RegistryFriendlyByteBuf,HubRequestSnapshotC2S> = StreamCodec.of({b,p->b.writeBoolean(p.editor)},{b->HubRequestSnapshotC2S(b.readBoolean())})}}

data class PokemonRuntimeInfoC2S(val key:String,val speciesId:String,val aspects:String):CustomPacketPayload{override fun type()=TYPE;companion object{val TYPE=CustomPacketPayload.Type<PokemonRuntimeInfoC2S>(id("pokemon_runtime_info_c2s"));val CODEC:StreamCodec<RegistryFriendlyByteBuf,PokemonRuntimeInfoC2S> = StreamCodec.of({b,p->b.writeUtf(p.key,PayloadLimits.POKEMON_KEY_CHARS);b.writeUtf(p.speciesId,PayloadLimits.POKEMON_SPECIES_CHARS);b.writeUtf(p.aspects,PayloadLimits.POKEMON_ASPECTS_CHARS)},{b->PokemonRuntimeInfoC2S(b.readUtf(PayloadLimits.POKEMON_KEY_CHARS),b.readUtf(PayloadLimits.POKEMON_SPECIES_CHARS),b.readUtf(PayloadLimits.POKEMON_ASPECTS_CHARS))})}}
data class PokemonRuntimeInfoS2C(val key:String,val json:String):CustomPacketPayload{override fun type()=TYPE;companion object{val TYPE=CustomPacketPayload.Type<PokemonRuntimeInfoS2C>(id("pokemon_runtime_info_s2c"));val CODEC:StreamCodec<RegistryFriendlyByteBuf,PokemonRuntimeInfoS2C> = StreamCodec.of({b,p->b.writeUtf(p.key,PayloadLimits.POKEMON_KEY_CHARS);b.writeUtf(p.json,PayloadLimits.POKEMON_INFO_CHARS)},{b->PokemonRuntimeInfoS2C(b.readUtf(PayloadLimits.POKEMON_KEY_CHARS),b.readUtf(PayloadLimits.POKEMON_INFO_CHARS))})}}

data class HubEditorChunkC2S(val transferId:Long,val baseRevision:Long,val index:Int,val total:Int,val chunk:String):CustomPacketPayload{override fun type()=TYPE;companion object{val TYPE=CustomPacketPayload.Type<HubEditorChunkC2S>(id("editor_chunk_c2s"));val CODEC:StreamCodec<RegistryFriendlyByteBuf,HubEditorChunkC2S> = StreamCodec.of({b,p->b.writeLong(p.transferId);b.writeLong(p.baseRevision);b.writeVarInt(p.index);b.writeVarInt(p.total);b.writeUtf(p.chunk,Compression.CHUNK_CHARS)},{b->HubEditorChunkC2S(b.readLong(),b.readLong(),b.readVarInt(),b.readVarInt(),b.readUtf(Compression.CHUNK_CHARS))})}}
