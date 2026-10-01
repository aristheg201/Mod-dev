package vn.svarcade.tcg.fabric;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import java.util.List;

public final class TcgPackets {
    private TcgPackets(){}
    public record Request(String action,List<String> args,long revision) {}
    public record Input(String json) implements CustomPayload {
        public static final Id<Input> ID=new Id<>(Identifier.of("svarcade_tcg","input"));
        public static final PacketCodec<RegistryByteBuf,Input> CODEC=PacketCodec.of((v,b)->b.writeString(v.json,8192),b->new Input(b.readString(8192)));
        public Id<? extends CustomPayload> getId(){return ID;}
    }
    public record Snapshot(String json) implements CustomPayload {
        public static final Id<Snapshot> ID=new Id<>(Identifier.of("svarcade_tcg","snapshot"));
        public static final PacketCodec<RegistryByteBuf,Snapshot> CODEC=PacketCodec.of((v,b)->b.writeByteArray(SnapshotCompression.encode(v.json)),b->new Snapshot(SnapshotCompression.decode(b.readByteArray(900*1024))));
        public Id<? extends CustomPayload> getId(){return ID;}
    }
}
