package io.github.aristheg201.cobblemonworld.network;

import io.github.aristheg201.cobblemonworld.CobblemonWorldMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ToastPayload(String category, net.minecraft.network.chat.Component title, net.minecraft.network.chat.Component body) implements CustomPacketPayload {
    public ToastPayload(String category, String title, String body) {
        this(category, net.minecraft.network.chat.Component.translatable(title), net.minecraft.network.chat.Component.translatable(body));
    }
    public static final Type<ToastPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CobblemonWorldMod.MOD_ID, "toast"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ToastPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeUtf(payload.category(), 64);
                net.minecraft.network.chat.ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, payload.title());
                net.minecraft.network.chat.ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, payload.body());
            },
            buf -> new ToastPayload(buf.readUtf(64), net.minecraft.network.chat.ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf), net.minecraft.network.chat.ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
