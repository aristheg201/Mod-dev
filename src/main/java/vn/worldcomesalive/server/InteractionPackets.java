package vn.worldcomesalive.server;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import java.util.*;

public final class InteractionPackets {
    public record Input(UUID npc,long token,long revision,String action) implements CustomPayload {
        public static final Id<Input> ID=new Id<>(Identifier.of("worldcomesalive","interaction"));
        public static final PacketCodec<RegistryByteBuf,Input> CODEC=PacketCodec.of((p,b)->{b.writeUuid(p.npc);b.writeLong(p.token);b.writeLong(p.revision);b.writeString(p.action,32);},b->new Input(b.readUuid(),b.readLong(),b.readLong(),b.readString(32)));
        public Id<? extends CustomPayload> getId(){return ID;}
    }
    public record View(UUID npc,long token,long revision,String name,String profession,String settlement,String activity,String mood,String text,List<String> options,double friendship,double trust,double respect,String stage,long money,String partner,String home,String status) {}
    public record Snapshot(String json) implements CustomPayload {
        public static final Id<Snapshot> ID=new Id<>(Identifier.of("worldcomesalive","dialogue"));
        public static final PacketCodec<RegistryByteBuf,Snapshot> CODEC=PacketCodec.of((p,b)->b.writeString(p.json,16384),b->new Snapshot(b.readString(16384)));
        public Id<? extends CustomPayload> getId(){return ID;}
    }
}
