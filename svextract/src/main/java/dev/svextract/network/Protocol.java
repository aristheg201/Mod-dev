package dev.svextract.network;

import dev.svextract.SvExtract;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Bounded requests contain selections only, never client-authored gameplay outcomes. */
public final class Protocol {
    public static final int VERSION=4;
    public record StashAction(int action,int page,int slot,long revision) implements CustomPayload {
        public static final Id<StashAction> ID=new Id<>(Identifier.of("svextract","stash_action"));
        public static final PacketCodec<RegistryByteBuf,StashAction> CODEC=PacketCodec.of((p,b)->{b.writeVarInt(p.action);b.writeVarInt(p.page);b.writeVarInt(p.slot);b.writeLong(p.revision);},b->new StashAction(b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readLong()));
        public Id<? extends CustomPayload> getId(){return ID;}
    }
    public record StashState(long revision,int page,int pages,byte[] items,String status) implements CustomPayload {
        public static final Id<StashState> ID=new Id<>(Identifier.of("svextract","stash_state"));
        public static final PacketCodec<RegistryByteBuf,StashState> CODEC=PacketCodec.of((p,b)->{b.writeLong(p.revision);b.writeVarInt(p.page);b.writeVarInt(p.pages);b.writeByteArray(p.items);b.writeString(p.status,160);},b->new StashState(b.readLong(),b.readVarInt(),b.readVarInt(),b.readByteArray(60*1024),b.readString(160)));
        public Id<? extends CustomPayload> getId(){return ID;}
    }
    public record LootClaim(String cache) implements CustomPayload {
        public static final Id<LootClaim> ID=new Id<>(Identifier.of("svextract","loot_claim"));
        public static final PacketCodec<RegistryByteBuf,LootClaim> CODEC=PacketCodec.of((p,b)->b.writeString(p.cache,48),b->new LootClaim(b.readString(48)));
        public Id<? extends CustomPayload> getId(){return ID;}
    }
    public record LootState(String cache,byte[] preview,boolean claimed,String status) implements CustomPayload {
        public LootState(String cache,byte[] preview,boolean claimed){this(cache,preview,claimed,"");}
        public static final Id<LootState> ID=new Id<>(Identifier.of("svextract","loot_state"));
        public static final PacketCodec<RegistryByteBuf,LootState> CODEC=PacketCodec.of((p,b)->{b.writeString(p.cache,48);b.writeByteArray(p.preview);b.writeBoolean(p.claimed);b.writeString(p.status,128);},b->new LootState(b.readString(48),b.readByteArray(60*1024),b.readBoolean(),b.readString(128)));
        public Id<? extends CustomPayload> getId(){return ID;}
    }
    public record LobbyAction(int action,String selection,String target,int difficulty,int friendlyBots,boolean acceptEquipmentRisk) implements CustomPayload {
        public static final Id<LobbyAction> ID=new Id<>(Identifier.of("svextract","lobby_action"));
        public static final PacketCodec<RegistryByteBuf,LobbyAction> CODEC=PacketCodec.of(LobbyAction::write,LobbyAction::read);
        private void write(RegistryByteBuf b){b.writeVarInt(action);b.writeString(selection,24);b.writeString(target,36);b.writeVarInt(difficulty);b.writeVarInt(friendlyBots);b.writeBoolean(acceptEquipmentRisk);}
        private static LobbyAction read(RegistryByteBuf b){return new LobbyAction(b.readVarInt(),b.readString(24),b.readString(36),b.readVarInt(),b.readVarInt(),b.readBoolean());}
        public Id<? extends CustomPayload> getId(){return ID;}
    }
    public record LobbyState(String json,boolean open) implements CustomPayload {
        public static final Id<LobbyState> ID=new Id<>(Identifier.of("svextract","lobby_state"));
        public static final PacketCodec<RegistryByteBuf,LobbyState> CODEC=PacketCodec.of(LobbyState::write,LobbyState::read);
        private void write(RegistryByteBuf b){b.writeString(json,16384);b.writeBoolean(open);}
        private static LobbyState read(RegistryByteBuf b){return new LobbyState(b.readString(16384),b.readBoolean());}
        public Id<? extends CustomPayload> getId(){return ID;}
    }
    public record Hello(int protocol,String modVersion) implements CustomPayload {
        public static final Id<Hello> ID=new Id<>(Identifier.of("svextract","hello"));
        public static final PacketCodec<RegistryByteBuf,Hello> CODEC=PacketCodec.of(Hello::write,Hello::read);
        private void write(RegistryByteBuf b){b.writeVarInt(protocol);b.writeString(modVersion,64);}
        private static Hello read(RegistryByteBuf b){return new Hello(b.readVarInt(),b.readString(64));}
        public Id<? extends CustomPayload> getId(){return ID;}
    }
    public record Hud(String objective,int remainingSeconds,int extractionSeconds,int threat,String phase,int teamScore,int enemyScore,int originX) implements CustomPayload {
        public Hud(String objective,int remainingSeconds,int extractionSeconds,int threat,String phase){this(objective,remainingSeconds,extractionSeconds,threat,phase,0,0,0);}
        public static final Id<Hud> ID=new Id<>(Identifier.of("svextract","hud"));
        public static final PacketCodec<RegistryByteBuf,Hud> CODEC=PacketCodec.of(Hud::write,Hud::read);
        private void write(RegistryByteBuf b){b.writeString(objective,48);b.writeVarInt(remainingSeconds);b.writeVarInt(extractionSeconds);b.writeVarInt(threat);b.writeString(phase,24);b.writeVarInt(teamScore);b.writeVarInt(enemyScore);b.writeInt(originX);}
        private static Hud read(RegistryByteBuf b){return new Hud(b.readString(48),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readString(24),b.readVarInt(),b.readVarInt(),b.readInt());}
        public Id<? extends CustomPayload> getId(){return ID;}
    }
    public record Result(String outcome,int completedObjectives,String mode,boolean equipmentRisk,int teamScore,int enemyScore) implements CustomPayload {
        public Result(String outcome,int completedObjectives,String mode,boolean equipmentRisk){this(outcome,completedObjectives,mode,equipmentRisk,0,0);}
        public static final Id<Result> ID=new Id<>(Identifier.of("svextract","result"));
        public static final PacketCodec<RegistryByteBuf,Result> CODEC=PacketCodec.of(Result::write,Result::read);
        private void write(RegistryByteBuf b){b.writeString(outcome,24);b.writeVarInt(completedObjectives);b.writeString(mode,24);b.writeBoolean(equipmentRisk);b.writeVarInt(teamScore);b.writeVarInt(enemyScore);}
        private static Result read(RegistryByteBuf b){return new Result(b.readString(24),b.readVarInt(),b.readString(24),b.readBoolean(),b.readVarInt(),b.readVarInt());}
        public Id<? extends CustomPayload> getId(){return ID;}
    }
    public static void register(){
        PayloadTypeRegistry.playC2S().register(Hello.ID,Hello.CODEC);
        PayloadTypeRegistry.playC2S().register(LobbyAction.ID,LobbyAction.CODEC);
        PayloadTypeRegistry.playC2S().register(StashAction.ID,StashAction.CODEC);
        PayloadTypeRegistry.playC2S().register(LootClaim.ID,LootClaim.CODEC);
        PayloadTypeRegistry.playS2C().register(Hello.ID,Hello.CODEC);
        PayloadTypeRegistry.playS2C().register(Hud.ID,Hud.CODEC);
        PayloadTypeRegistry.playS2C().register(Result.ID,Result.CODEC);
        PayloadTypeRegistry.playS2C().register(LobbyState.ID,LobbyState.CODEC);
        PayloadTypeRegistry.playS2C().register(StashState.ID,StashState.CODEC);
        PayloadTypeRegistry.playS2C().register(LootState.ID,LootState.CODEC);
    }
    public static Hello hello(){return new Hello(VERSION,SvExtract.VERSION);}
}
