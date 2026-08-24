package vn.svframe.mmoitemsfabric.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vn.svframe.mmoitemsfabric.MMOItemsUpgradeInteraction;

/** Applies consumable upgrades from the cursor onto a single MMOItem stack. */
@Mixin(ScreenHandler.class)
public abstract class ScreenHandlerUpgradeInteractionMixin {
    @Inject(method = "onSlotClick", at = @At("HEAD"), cancellable = true)
    private void mmoitems$cursorUpgrade(int slotIndex, int button, SlotActionType actionType,
                                        PlayerEntity player, CallbackInfo ci) {
        if (actionType != SlotActionType.PICKUP || !(player instanceof ServerPlayerEntity serverPlayer)) return;
        ScreenHandler handler = (ScreenHandler) (Object) this;
        if (slotIndex < 0 || slotIndex >= handler.slots.size()) return;

        ItemStack cursor = handler.getCursorStack();
        Slot slot = handler.getSlot(slotIndex);
        ItemStack target = slot.getStack();
        if (cursor.isEmpty() || target.isEmpty()) return;

        MMOItemsUpgradeInteraction.Result result = MMOItemsUpgradeInteraction.apply(serverPlayer, cursor, target);
        if (result == MMOItemsUpgradeInteraction.Result.NONE) return;

        ci.cancel();
        cursor.decrement(1);
        handler.setCursorStack(cursor.isEmpty() ? ItemStack.EMPTY : cursor);
        if (result == MMOItemsUpgradeInteraction.Result.SUCCESS) {
            slot.setStack(target);
            slot.markDirty();
            serverPlayer.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, 1.0f, 2.0f);
        } else {
            if (!target.isEmpty()) {
                slot.setStack(target);
                slot.markDirty();
            }
            serverPlayer.playSound(SoundEvents.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
        }
        handler.sendContentUpdates();
    }
}
