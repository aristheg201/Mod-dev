package io.github.aristheg201.cobblemonworld.npc;

import com.cobblemon.mod.common.entity.npc.NPCEntity;
import io.github.aristheg201.cobblemonworld.CobblemonWorldMod;
import io.github.aristheg201.cobblemonworld.narrative.PersonalActors;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;

/** Restores authored behavior on the saved entity, using the placement UUID as identity. */
public final class NpcBindingService {
    private NpcBindingService() {}

    public static void register() {
        // Repair before Cobblemon chooses its native default interaction, even when a
        // player clicks in the first tick after a chunk loads. Let native mobInteract
        // retain its battle/editor guards and execute our repaired configuration once.
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (!level.isClientSide && entity instanceof NPCEntity npc) {
                var placement = placement(npc);
                if (placement != null) restore(npc, placement, false);
            }
            return InteractionResult.PASS;
        });
    }

    public static NpcPlacement placement(NPCEntity npc) {
        return NpcPlacementStore.INSTANCE.findByEntity(npc.getUUID(), npc.level().dimension().location().toString());
    }

    public static boolean restore(NPCEntity npc, NpcPlacement placement, boolean loaded) {
        if (!npc.getUUID().toString().equals(placement.entityUuid())
                || !npc.level().dimension().location().toString().equals(placement.dimension())) return false;
        if (!placement.equals(placement(npc))) return false;
        var definition = NpcDefinitionRegistry.INSTANCE.get(placement.id());
        if (definition == null || definition.specialActor() || PersonalActors.personal(definition.id())
                || PersonalActors.owner(npc) != null) return false;
        // Native "standard" NPCs can distance-despawn even with disabled AI.
        // This flag is serialized by vanilla; authored map NPCs must keep their UUID.
        npc.setPersistenceRequired();
        boolean changed = !(npc.getInteraction() instanceof CWorldNpcInteraction interaction)
                || !definition.id().equals(interaction.definitionId());
        if (!changed && !loaded) return true;

        String previous = npc.getInteraction() == null ? "native class default" : npc.getInteraction().getType();
        npc.setInteraction(new CWorldNpcInteraction(definition.id()));
        npc.setCustomName(Component.literal(definition.displayName()));
        npc.setCustomNameVisible(true);
        npc.setNoAi(true);
        npc.setMovable(false);
        npc.setInvulnerable(true);
        npc.setSkill(5);
        TrainerBattleService.applyAuthoredSkin(npc, definition);
        AnchoredNpcService.authoredPose(npc, placement.yaw());
        // No initialize(), replacement entity, party reset, or progression mutation.
        if (changed) CobblemonWorldMod.LOGGER.info(
                "Restored saved CobblemonWorld NPC id={} uuid={} previousInteraction={} dimension={}",
                definition.id(), npc.getUUID(), previous, placement.dimension());
        return true;
    }
}
