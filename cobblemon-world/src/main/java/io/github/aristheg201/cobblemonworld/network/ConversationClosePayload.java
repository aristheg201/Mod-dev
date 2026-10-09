package io.github.aristheg201.cobblemonworld.network;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** A failed action keeps its screen open; only the server confirms successful completion. */
public record ConversationClosePayload(UUID session) implements CustomPacketPayload {
    public static final Type<ConversationClosePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("cobblemonworld", "conversation_close"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ConversationClosePayload> CODEC = StreamCodec.of((b,p) -> b.writeUUID(p.session), b -> new ConversationClosePayload(b.readUUID()));
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
