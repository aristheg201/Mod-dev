package io.github.aristheg201.cobblemonworld.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ShopSnapshotPayload(String json, boolean open) implements CustomPacketPayload {
    public static final Type<ShopSnapshotPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("cobblemonworld", "shop_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ShopSnapshotPayload> CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeUtf(p.json(), 500000); buf.writeBoolean(p.open()); },
            buf -> new ShopSnapshotPayload(buf.readUtf(500000), buf.readBoolean()));
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
