package vn.svframe.svquest.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import vn.svframe.svquest.SVQuest;

public record StatePayload(String state) implements CustomPayload {
    public static final Id<StatePayload> ID = new Id<>(Identifier.of(SVQuest.MOD_ID, "state"));
    public static final PacketCodec<RegistryByteBuf, StatePayload> CODEC =
            PacketCodec.tuple(PacketCodecs.STRING, StatePayload::state, StatePayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
