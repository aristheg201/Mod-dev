package io.github.aristheg201.svhub.network

import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation

private fun id(path: String) = ResourceLocation.fromNamespaceAndPath("svhub", path)

data class HubHelloS2C(
    val protocol: Int,
    val revision: Long,
    val canOpen: Boolean,
    val canEdit: Boolean,
    val serverManifest: String
) : CustomPacketPayload {
    override fun type() = TYPE
    companion object {
        val TYPE = CustomPacketPayload.Type<HubHelloS2C>(id("hello_s2c"))
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, HubHelloS2C> = StreamCodec.of(
            { buf, p -> buf.writeVarInt(p.protocol); buf.writeLong(p.revision); buf.writeBoolean(p.canOpen); buf.writeBoolean(p.canEdit); buf.writeUtf(p.serverManifest, 1_000_000) },
            { buf -> HubHelloS2C(buf.readVarInt(), buf.readLong(), buf.readBoolean(), buf.readBoolean(), buf.readUtf(1_000_000)) }
        )
    }
}

data class HubSnapshotChunkS2C(
    val transferId: Long,
    val revision: Long,
    val editor: Boolean,
    val index: Int,
    val total: Int,
    val chunk: String
) : CustomPacketPayload {
    override fun type() = TYPE
    companion object {
        val TYPE = CustomPacketPayload.Type<HubSnapshotChunkS2C>(id("snapshot_chunk_s2c"))
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, HubSnapshotChunkS2C> = StreamCodec.of(
            { buf, p ->
                buf.writeLong(p.transferId); buf.writeLong(p.revision); buf.writeBoolean(p.editor)
                buf.writeVarInt(p.index); buf.writeVarInt(p.total); buf.writeUtf(p.chunk, 25_000)
            },
            { buf -> HubSnapshotChunkS2C(buf.readLong(), buf.readLong(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(25_000)) }
        )
    }
}

data class HubOpenS2C(val page: String, val editor: Boolean) : CustomPacketPayload {
    override fun type() = TYPE
    companion object {
        val TYPE = CustomPacketPayload.Type<HubOpenS2C>(id("open_s2c"))
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, HubOpenS2C> = StreamCodec.of(
            { buf, p -> buf.writeUtf(p.page, 256); buf.writeBoolean(p.editor) },
            { buf -> HubOpenS2C(buf.readUtf(256), buf.readBoolean()) }
        )
    }
}

data class HubEditorResultS2C(val ok: Boolean, val revision: Long, val message: String) : CustomPacketPayload {
    override fun type() = TYPE
    companion object {
        val TYPE = CustomPacketPayload.Type<HubEditorResultS2C>(id("editor_result_s2c"))
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, HubEditorResultS2C> = StreamCodec.of(
            { buf, p -> buf.writeBoolean(p.ok); buf.writeLong(p.revision); buf.writeUtf(p.message, 4096) },
            { buf -> HubEditorResultS2C(buf.readBoolean(), buf.readLong(), buf.readUtf(4096)) }
        )
    }
}

data class HubClientManifestC2S(val protocol: Int, val cachedRevision: Long, val clientManifest: String) : CustomPacketPayload {
    override fun type() = TYPE
    companion object {
        val TYPE = CustomPacketPayload.Type<HubClientManifestC2S>(id("manifest_c2s"))
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, HubClientManifestC2S> = StreamCodec.of(
            { buf, p -> buf.writeVarInt(p.protocol); buf.writeLong(p.cachedRevision); buf.writeUtf(p.clientManifest, 1_000_000) },
            { buf -> HubClientManifestC2S(buf.readVarInt(), buf.readLong(), buf.readUtf(1_000_000)) }
        )
    }
}

data class HubActionC2S(val actionId: String) : CustomPacketPayload {
    override fun type() = TYPE
    companion object {
        val TYPE = CustomPacketPayload.Type<HubActionC2S>(id("action_c2s"))
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, HubActionC2S> = StreamCodec.of(
            { buf, p -> buf.writeUtf(p.actionId, 192) },
            { buf -> HubActionC2S(buf.readUtf(192)) }
        )
    }
}

data class HubRequestSnapshotC2S(val editor: Boolean) : CustomPacketPayload {
    override fun type() = TYPE
    companion object {
        val TYPE = CustomPacketPayload.Type<HubRequestSnapshotC2S>(id("request_snapshot_c2s"))
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, HubRequestSnapshotC2S> = StreamCodec.of(
            { buf, p -> buf.writeBoolean(p.editor) },
            { buf -> HubRequestSnapshotC2S(buf.readBoolean()) }
        )
    }
}

data class HubEditorChunkC2S(
    val transferId: Long,
    val baseRevision: Long,
    val index: Int,
    val total: Int,
    val chunk: String
) : CustomPacketPayload {
    override fun type() = TYPE
    companion object {
        val TYPE = CustomPacketPayload.Type<HubEditorChunkC2S>(id("editor_chunk_c2s"))
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, HubEditorChunkC2S> = StreamCodec.of(
            { buf, p ->
                buf.writeLong(p.transferId); buf.writeLong(p.baseRevision); buf.writeVarInt(p.index); buf.writeVarInt(p.total); buf.writeUtf(p.chunk, 25_000)
            },
            { buf -> HubEditorChunkC2S(buf.readLong(), buf.readLong(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(25_000)) }
        )
    }
}
