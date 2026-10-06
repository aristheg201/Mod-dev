package io.github.aristheg201.cobblemonworld.network;

import io.github.aristheg201.cobblemonworld.CobblemonWorldMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record QaControlPayload(String action, String primary, String secondary) implements CustomPacketPayload {
    public static final Type<QaControlPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CobblemonWorldMod.MOD_ID, "qa_control"));
    public static final StreamCodec<RegistryFriendlyByteBuf, QaControlPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeUtf(payload.action(), 64);
                buf.writeUtf(payload.primary(), 256);
                buf.writeUtf(payload.secondary(), 256);
            },
            buf -> new QaControlPayload(buf.readUtf(64), buf.readUtf(256), buf.readUtf(256))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
