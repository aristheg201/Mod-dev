package vn.svframe.mmoitemsfabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

/** Event bridge for revision refreshes which do not need a vanilla mixin. */
public final class MMOItemsRevisionMod implements ModInitializer {
    @Override
    public void onInitialize() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> refreshInventory(handler.player));
    }

    private static void refreshInventory(ServerPlayerEntity player) {
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack current = inventory.getStack(slot);
            ItemStack refreshed = MMOItemsRevisionRuntime.refresh(current, MMOItemsRevisionRuntime.Reason.JOIN);
            if (refreshed != null) inventory.setStack(slot, refreshed);
        }
    }
}
