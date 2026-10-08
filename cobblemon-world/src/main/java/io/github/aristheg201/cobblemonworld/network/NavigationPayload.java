package io.github.aristheg201.cobblemonworld.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record NavigationPayload(String json) implements CustomPacketPayload {
    public static final Type<NavigationPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("cobblemonworld", "navigation"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NavigationPayload> CODEC = StreamCodec.of(
            (buf, p) -> buf.writeUtf(p.json(), 4096), buf -> new NavigationPayload(buf.readUtf(4096)));
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
