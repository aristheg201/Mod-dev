package io.github.aristheg201.cobblemonworld.network;

import io.github.aristheg201.cobblemonworld.CobblemonWorldMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ToastPayload(String category, String title, String body) implements CustomPacketPayload {
    public static final Type<ToastPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CobblemonWorldMod.MOD_ID, "toast"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ToastPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeUtf(payload.category(), 64);
                buf.writeUtf(payload.title(), 256);
                buf.writeUtf(payload.body(), 1024);
            },
            buf -> new ToastPayload(buf.readUtf(64), buf.readUtf(256), buf.readUtf(1024))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
