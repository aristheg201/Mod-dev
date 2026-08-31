package vn.svframe.svquest.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import vn.svframe.svquest.SVQuest;

public record ActionPayload(String action) implements CustomPayload {
    public static final Id<ActionPayload> ID = new Id<>(Identifier.of(SVQuest.MOD_ID, "action"));
    public static final PacketCodec<RegistryByteBuf, ActionPayload> CODEC =
            PacketCodec.tuple(PacketCodecs.STRING, ActionPayload::action, ActionPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
