package io.github.aristheg201.cobblemonworld.network;

import io.github.aristheg201.cobblemonworld.CobblemonWorldMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RpgSkillPayload(String skill) implements CustomPacketPayload {
    public static final Type<RpgSkillPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CobblemonWorldMod.MOD_ID, "rpg_skill"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RpgSkillPayload> CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeUtf(payload.skill(), 32),
            buf -> new RpgSkillPayload(buf.readUtf(32))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
