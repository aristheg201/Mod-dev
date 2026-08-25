package vn.svframe.mmoitemsfabric.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.AbstractFurnaceScreenHandler;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.EnchantmentScreenHandler;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.SmithingScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vn.svframe.mmoitemsfabric.MMOItemsLegacyItemOptions;

/** Enforces legacy disable-* station flags on authoritative inventory clicks. */
@Mixin(ScreenHandler.class)
public abstract class ScreenHandlerRestrictionMixin {
    @Inject(method = "onSlotClick", at = @At("HEAD"), cancellable = true)
    private void mmoitems$restrictStationUse(int slotIndex, int button, SlotActionType actionType,
                                             PlayerEntity player, CallbackInfo ci) {
        ScreenHandler handler = (ScreenHandler) (Object) this;
        if (slotIndex < 0 || slotIndex >= handler.slots.size()) return;

        ItemStack cursor = handler.getCursorStack();
        ItemStack clicked = handler.getSlot(slotIndex).getStack();

        if (handler instanceof CraftingScreenHandler) {
            if (slotIndex == 0 && hasRestrictedInput(handler, 1, 9, "disable-crafting")) { ci.cancel(); return; }
            if (!cursor.isEmpty() && slotIndex >= 1 && slotIndex <= 9 && disabled(cursor, "disable-crafting")) { ci.cancel(); return; }
            if (actionType == SlotActionType.QUICK_MOVE && disabled(clicked, "disable-crafting")) { ci.cancel(); return; }
        }
        if (handler instanceof PlayerScreenHandler) {
            if (slotIndex == 0 && hasRestrictedInput(handler, 1, 4, "disable-crafting")) { ci.cancel(); return; }
            if (!cursor.isEmpty() && slotIndex >= 1 && slotIndex <= 4 && disabled(cursor, "disable-crafting")) { ci.cancel(); return; }
        }
        if (handler instanceof AbstractFurnaceScreenHandler) {
            if (!cursor.isEmpty() && slotIndex == 0 && disabled(cursor, "disable-smelting")) { ci.cancel(); return; }
            if (actionType == SlotActionType.QUICK_MOVE && disabled(clicked, "disable-smelting")) { ci.cancel(); return; }
        }
        if (handler instanceof EnchantmentScreenHandler) {
            if (!cursor.isEmpty() && slotIndex == 0 && disabled(cursor, "disable-enchanting")) { ci.cancel(); return; }
            if (actionType == SlotActionType.QUICK_MOVE && disabled(clicked, "disable-enchanting")) { ci.cancel(); return; }
        }
        if (handler instanceof AnvilScreenHandler) {
            if (slotIndex == 2 && hasRestrictedInput(handler, 0, 1, "disable-repairing")) { ci.cancel(); return; }
            if (!cursor.isEmpty() && slotIndex <= 1 && disabled(cursor, "disable-repairing")) { ci.cancel(); return; }
            if (actionType == SlotActionType.QUICK_MOVE && disabled(clicked, "disable-repairing")) { ci.cancel(); return; }
        }
        if (handler instanceof SmithingScreenHandler) {
            if (slotIndex == 3 && hasRestrictedInput(handler, 0, 2, "disable-smithing")) { ci.cancel(); return; }
            if (!cursor.isEmpty() && slotIndex <= 2 && disabled(cursor, "disable-smithing")) { ci.cancel(); return; }
            if (actionType == SlotActionType.QUICK_MOVE && disabled(clicked, "disable-smithing")) ci.cancel();
        }
    }

    private static boolean hasRestrictedInput(ScreenHandler handler, int first, int last, String option) {
        int maximum = Math.min(last, handler.slots.size() - 1);
        for (int index = Math.max(0, first); index <= maximum; index++) {
            Slot slot = handler.getSlot(index);
            if (slot.hasStack() && disabled(slot.getStack(), option)) return true;
        }
        return false;
    }

    private static boolean disabled(ItemStack stack, String option) {
        return stack != null && !stack.isEmpty() && MMOItemsLegacyItemOptions.bool(stack, option, false);
    }
}
