package io.github.aristheg201.cobblemonworld.network;

import io.github.aristheg201.cobblemonworld.CobblemonWorldMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record QaAckPayload(String token, boolean ok, String detail) implements CustomPacketPayload {
    public static final Type<QaAckPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CobblemonWorldMod.MOD_ID, "qa_ack"));
    public static final StreamCodec<RegistryFriendlyByteBuf, QaAckPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeUtf(payload.token(), 256);
                buf.writeBoolean(payload.ok());
                buf.writeUtf(payload.detail(), 1024);
            },
            buf -> new QaAckPayload(buf.readUtf(256), buf.readBoolean(), buf.readUtf(1024))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
