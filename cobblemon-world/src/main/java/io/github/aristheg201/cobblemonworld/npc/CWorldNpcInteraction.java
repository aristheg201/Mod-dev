package io.github.aristheg201.cobblemonworld.npc;

import com.cobblemon.mod.common.api.npc.configuration.NPCInteractConfiguration;
import com.cobblemon.mod.common.entity.npc.NPCEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public final class CWorldNpcInteraction implements NPCInteractConfiguration {
    public static final String TYPE = "cworld";
    private String definitionId = "";

    public CWorldNpcInteraction() {}

    public CWorldNpcInteraction(String definitionId) {
        this.definitionId = definitionId == null ? "" : definitionId;
    }

    public String definitionId() {
        return definitionId;
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public boolean interact(NPCEntity npc, ServerPlayer player) {
        return TrainerBattleService.interact(npc, player, definitionId);
    }

    @Override
    public void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeUtf(definitionId, 128);
    }

    @Override
    public void decode(RegistryFriendlyByteBuf buffer) {
        definitionId = buffer.readUtf(128);
    }

    @Override
    public void writeToNBT(CompoundTag compoundTag) {
        compoundTag.putString("CWorldDefinition", definitionId);
    }

    @Override
    public void readFromNBT(CompoundTag compoundTag) {
        definitionId = compoundTag.getString("CWorldDefinition");
    }

    @Override
    public boolean isDifferentTo(NPCInteractConfiguration other) {
        return !(other instanceof CWorldNpcInteraction cworld) || !definitionId.equals(cworld.definitionId);
    }
}
