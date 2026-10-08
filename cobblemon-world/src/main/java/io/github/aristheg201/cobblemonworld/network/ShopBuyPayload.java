package io.github.aristheg201.cobblemonworld.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ShopBuyPayload(String shop, String entry, int quantity) implements CustomPacketPayload {
    public static final Type<ShopBuyPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("cobblemonworld", "shop_buy"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ShopBuyPayload> CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeUtf(p.shop(), 64); buf.writeUtf(p.entry(), 256); buf.writeVarInt(p.quantity()); },
            buf -> new ShopBuyPayload(buf.readUtf(64), buf.readUtf(256), buf.readVarInt()));
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
