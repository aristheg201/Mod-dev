package io.github.aristheg201.cobblemonworld.network;

import io.github.aristheg201.cobblemonworld.CobblemonWorldMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record PhoneSnapshotPayload(String json) implements CustomPacketPayload {
    public static final Type<PhoneSnapshotPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CobblemonWorldMod.MOD_ID, "phone_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PhoneSnapshotPayload> CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeUtf(payload.json(), 262144),
            buf -> new PhoneSnapshotPayload(buf.readUtf(262144))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
