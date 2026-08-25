package vn.svframe.mmoitemsfabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.TypedActionResult;

/** Denies vanilla item use when requirements fail or disable-interaction is set, while MMOItems callbacks still run first. */
public final class MMOItemsInteractionRestrictionMod implements ModInitializer {
    @Override
    public void onInitialize() {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getStackInHand(hand);
            if (!world.isClient && player instanceof ServerPlayerEntity serverPlayer && denyVanillaUse(serverPlayer, stack))
                return TypedActionResult.fail(stack);
            return TypedActionResult.pass(stack);
        });
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (!world.isClient && player instanceof ServerPlayerEntity serverPlayer && denyVanillaUse(serverPlayer, player.getStackInHand(hand)))
                return ActionResult.FAIL;
            return ActionResult.PASS;
        });
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (!world.isClient && player instanceof ServerPlayerEntity serverPlayer && denyVanillaUse(serverPlayer, player.getStackInHand(hand)))
                return ActionResult.FAIL;
            return ActionResult.PASS;
        });
    }

    private static boolean denyVanillaUse(ServerPlayerEntity player, ItemStack stack) {
        if (stack == null || stack.isEmpty() || MMOItemsGameplayMod.template(stack) == null) return false;
        if (!MMOItemsRequirementGate.canUse(player, stack)) return true;
        return MMOItemsLegacyItemOptions.bool(stack, "disable-interaction", false);
    }
}
