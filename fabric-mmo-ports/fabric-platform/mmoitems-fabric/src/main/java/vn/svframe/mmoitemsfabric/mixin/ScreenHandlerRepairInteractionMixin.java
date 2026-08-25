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
import vn.svframe.mmoitemsfabric.MMOItemsRepairInteraction;

/** Applies repair consumables when dragged from the cursor onto an item. */
@Mixin(ScreenHandler.class)
public abstract class ScreenHandlerRepairInteractionMixin {
    @Inject(method = "onSlotClick", at = @At("HEAD"), cancellable = true)
    private void mmoitems$cursorRepair(int slotIndex, int button, SlotActionType actionType,
                                       PlayerEntity player, CallbackInfo ci) {
        if (actionType != SlotActionType.PICKUP || !(player instanceof ServerPlayerEntity serverPlayer)) return;
        ScreenHandler handler = (ScreenHandler) (Object) this;
        if (slotIndex < 0 || slotIndex >= handler.slots.size()) return;
        ItemStack cursor = handler.getCursorStack();
        Slot slot = handler.getSlot(slotIndex);
        ItemStack target = slot.getStack();
        if (cursor.isEmpty() || target.isEmpty()) return;
        if (MMOItemsRepairInteraction.apply(serverPlayer, cursor, target) != MMOItemsRepairInteraction.Result.SUCCESS) return;

        ci.cancel();
        cursor.decrement(1);
        handler.setCursorStack(cursor.isEmpty() ? ItemStack.EMPTY : cursor);
        slot.setStack(target);
        slot.markDirty();
        serverPlayer.playSound(SoundEvents.BLOCK_ANVIL_USE, 0.8f, 1.25f);
        handler.sendContentUpdates();
    }
}
