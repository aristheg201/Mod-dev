package io.github.aristheg201.cobblemonworld.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ShopBuyPayload(String shop, String entry, int quantity, java.util.UUID session, int revision, java.util.UUID requestId) implements CustomPacketPayload {
    public static final Type<ShopBuyPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("cobblemonworld", "shop_buy"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ShopBuyPayload> CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeUtf(p.shop(), 64); buf.writeUtf(p.entry(), 256); buf.writeVarInt(p.quantity()); buf.writeUUID(p.session());buf.writeVarInt(p.revision());buf.writeUUID(p.requestId()); },
            buf -> new ShopBuyPayload(buf.readUtf(64), buf.readUtf(256), buf.readVarInt(),buf.readUUID(),buf.readVarInt(),buf.readUUID()));
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
