package io.github.aristheg201.svhub.network

import io.github.aristheg201.svhub.SVHubRuntime
import io.github.aristheg201.svhub.action.ServerActionDispatcher
import io.github.aristheg201.svhub.content.*
import io.github.aristheg201.svhub.permission.EditAuthorization
import io.github.aristheg201.svhub.permission.SVHubPermissions
import io.github.aristheg201.svhub.server.EnvironmentManifest
import io.github.aristheg201.svhub.server.SnapshotProjector
import io.github.aristheg201.svhub.util.Compression
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom

object SVHubNetwork {
    private data class ClientState(val protocol:Int,val modIds:Set<String>)
    private val clients=ConcurrentHashMap<UUID,ClientState>(); private val assemblers=ConcurrentHashMap<UUID,ChunkAssembler>()
    fun registerCommon(){
        PayloadTypeRegistry.playS2C().register(HubHelloS2C.TYPE,HubHelloS2C.CODEC);PayloadTypeRegistry.playS2C().register(HubSnapshotChunkS2C.TYPE,HubSnapshotChunkS2C.CODEC);PayloadTypeRegistry.playS2C().register(HubOpenS2C.TYPE,HubOpenS2C.CODEC);PayloadTypeRegistry.playS2C().register(HubEditorResultS2C.TYPE,HubEditorResultS2C.CODEC)
        PayloadTypeRegistry.playC2S().register(HubClientManifestC2S.TYPE,HubClientManifestC2S.CODEC);PayloadTypeRegistry.playC2S().register(HubActionC2S.TYPE,HubActionC2S.CODEC);PayloadTypeRegistry.playC2S().register(HubRequestSnapshotC2S.TYPE,HubRequestSnapshotC2S.CODEC);PayloadTypeRegistry.playC2S().register(HubEditorChunkC2S.TYPE,HubEditorChunkC2S.CODEC)
        ServerPlayNetworking.registerGlobalReceiver(HubClientManifestC2S.TYPE){p,c->c.server().execute{val pl=c.player();clients[pl.uuid]=ClientState(p.protocol,EnvironmentManifest.modIds(p.clientManifest));if(p.protocol==HUB_PROTOCOL_VERSION&&p.cachedRevision!=SVHubRuntime.store.snapshot().revision)sendSnapshot(pl,false)}}
        ServerPlayNetworking.registerGlobalReceiver(HubActionC2S.TYPE){p,c->c.server().execute{ServerActionDispatcher.execute(c.player(),p.actionId)}}
        ServerPlayNetworking.registerGlobalReceiver(HubRequestSnapshotC2S.TYPE){p,c->c.server().execute{val pl=c.player();if(SVHubPermissions.has(pl,SVHubPermissions.OPEN,0))sendSnapshot(pl,p.editor&&SVHubPermissions.has(pl,SVHubPermissions.EDITOR,2))}}
        ServerPlayNetworking.registerGlobalReceiver(HubEditorChunkC2S.TYPE){p,c->c.server().execute{receiveEditorChunk(c.player(),p)}}
    }
    fun clientHasMod(player:ServerPlayer,modId:String)=clients[player.uuid]?.modIds?.contains(modId)==true
    fun onJoin(player:ServerPlayer){val open=SVHubPermissions.has(player,SVHubPermissions.OPEN,0);val edit=open&&SVHubPermissions.has(player,SVHubPermissions.EDITOR,2);ServerPlayNetworking.send(player,HubHelloS2C(HUB_PROTOCOL_VERSION,SVHubRuntime.store.snapshot().revision,open,edit,EnvironmentManifest.local().toJson()))}
    fun onDisconnect(player:ServerPlayer){clients.remove(player.uuid);assemblers.remove(player.uuid)?.clear();ServerActionDispatcher.clear(player)}
    fun open(player:ServerPlayer,page:String,editor:Boolean){if(!SVHubPermissions.has(player,SVHubPermissions.OPEN,0))return;ServerPlayNetworking.send(player,HubOpenS2C(page,editor&&SVHubPermissions.has(player,SVHubPermissions.EDITOR,2)))}
    fun sendSnapshot(player:ServerPlayer,editor:Boolean){val projected=SnapshotProjector.forPlayer(SVHubRuntime.store.snapshot().content,player,editor);val encoded=Compression.encodeUtf8(HubContentCodec.encode(projected));val chunks=Compression.chunks(encoded);val transfer=ThreadLocalRandom.current().nextLong();chunks.forEachIndexed{i,s->ServerPlayNetworking.send(player,HubSnapshotChunkS2C(transfer,projected.revision,editor,i,chunks.size,s))}}
    fun broadcastPlayerSnapshots(){SVHubRuntime.server?.playerList?.players?.forEach{if(SVHubPermissions.has(it,SVHubPermissions.OPEN,0))sendSnapshot(it,false)}}
    private fun receiveEditorChunk(player:ServerPlayer,p:HubEditorChunkC2S){if(!SVHubPermissions.has(player,SVHubPermissions.EDITOR_PUBLISH,2))return;val joined=runCatching{assemblers.computeIfAbsent(player.uuid){ChunkAssembler()}.accept(p.transferId,p.index,p.total,p.chunk)}.getOrElse{return}?:return;val candidate=runCatching{HubContentCodec.decode(Compression.decodeUtf8(joined))}.getOrElse{ServerPlayNetworking.send(player,HubEditorResultS2C(false,SVHubRuntime.store.snapshot().revision,it.message?:"Decode failed"));return};val reason=EditAuthorization.rejectReason(player,SVHubRuntime.store.snapshot().content,candidate);if(reason!=null){ServerPlayNetworking.send(player,HubEditorResultS2C(false,SVHubRuntime.store.snapshot().revision,reason));return};val result=SVHubRuntime.store.commit(p.baseRevision,candidate);ServerPlayNetworking.send(player,HubEditorResultS2C(result.ok,result.revision,result.message));if(result.ok)broadcastPlayerSnapshots()}
}
