package io.github.aristheg201.cobblemonworld.network;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
/** No effect, destination, price or story flag is accepted from the client. */
public record ConversationChoicePayload(UUID session, int revision, String choice) implements CustomPacketPayload {
    public static final Type<ConversationChoicePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("cobblemonworld","conversation_choice"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ConversationChoicePayload> CODEC = StreamCodec.of((b,p)->{b.writeUUID(p.session);b.writeVarInt(p.revision);b.writeUtf(p.choice,128);},b->new ConversationChoicePayload(b.readUUID(),b.readVarInt(),b.readUtf(128)));
    public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
