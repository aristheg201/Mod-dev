package io.github.aristheg201.cobblemonworld.network;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
public record ConversationPayload(String json) implements CustomPacketPayload {
    public static final Type<ConversationPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("cobblemonworld","conversation"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ConversationPayload> CODEC = StreamCodec.of((b,p)->b.writeUtf(p.json,65536),b->new ConversationPayload(b.readUtf(65536)));
    public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
