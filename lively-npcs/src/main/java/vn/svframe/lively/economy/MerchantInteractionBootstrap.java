package vn.svframe.lively.economy;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import vn.svframe.lively.api.LivelyApi;
import vn.svframe.lively.society.SocietyApi;

/** Merchant interaction runs before the generic dialogue callback and falls through for non-shops. */
public final class MerchantInteractionBootstrap implements ModInitializer {
    @Override public void onInitialize() {
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient || hand != Hand.MAIN_HAND || !(player instanceof ServerPlayerEntity serverPlayer)) return ActionResult.PASS;
            if (entity.getCommandTags().contains("lively_native_interaction") || LivelyApi.npcs() == null || SocietyApi.commerce() == null) {
                return ActionResult.PASS;
            }
            var npc = LivelyApi.npcs().npcForEntity(entity.getUuid());
            if (npc.isEmpty()) return ActionResult.PASS;
            return SocietyApi.commerce().present(serverPlayer, npc.get()) ? ActionResult.SUCCESS : ActionResult.PASS;
        });
    }
}
