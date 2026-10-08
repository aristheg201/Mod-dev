package io.github.aristheg201.cobblemonworld.shop;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;

/** Stages complete slot changes; never drops an ungranted remainder into the world. */
public final class InventoryGrant implements PurchaseTransaction.Grant {
    private final ServerPlayer player;
    private final ItemStack product;
    private final List<ItemStack> before = new ArrayList<>();
    private final List<ItemStack> after = new ArrayList<>();
    private boolean touched;
    public InventoryGrant(ServerPlayer player, ItemStack product) { this.player = player; this.product = product; }
    public boolean prepare() {
        before.clear(); after.clear();
        for (int i = 0; i < 36; i++) {
            before.add(player.getInventory().getItem(i).copy());
            after.add(before.get(i).copy());
        }
        int remaining = product.getCount();
        for (int pass = 0; pass < 2; pass++) for (int i = 0; i < after.size() && remaining > 0; i++) {
            var slot = after.get(i);
            if (pass == 0 && !slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, product)) {
                int add = Math.min(remaining, Math.min(64, slot.getMaxStackSize()) - slot.getCount());
                if (add > 0) { slot.grow(add); remaining -= add; }
            } else if (pass == 1 && slot.isEmpty()) {
                int add = Math.min(remaining, Math.min(64, product.getMaxStackSize()));
                after.set(i, product.copyWithCount(add)); remaining -= add;
            }
        }
        return remaining == 0;
    }
    public void commit() {
        for (int i = 0; i < 36; i++) {
            if (!ItemStack.matches(before.get(i), player.getInventory().getItem(i)))
                throw new IllegalStateException("Inventory changed during BeastCoin debit");
        }
        touched = true;
        for (int i = 0; i < 36; i++) player.getInventory().setItem(i, after.get(i));
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
    }
    public void rollback() {
        if (!touched) return;
        for (int i = 0; i < 36; i++) player.getInventory().setItem(i, before.get(i));
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
    }
}
