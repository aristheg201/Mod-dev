package vn.svframe.mmoitemsfabric.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.SmithingScreenHandler;
import net.minecraft.screen.slot.CraftingResultSlot;
import net.minecraft.screen.slot.FurnaceOutputSlot;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vn.svframe.mmoitemsfabric.MMOItemsGameplayMod;
import vn.svframe.mmoitemsfabric.MMOItemsLegacyItemOptions;

/** Enforces legacy operation restrictions on result extraction, including shift-click. */
@Mixin(ScreenHandler.class)
public abstract class ScreenHandlerOperationRestrictionMixin {
    @Inject(method = "onSlotClick", at = @At("HEAD"), cancellable = true)
    private void mmoitems$restrictOperationResult(int slotIndex, int button, SlotActionType actionType,
                                                  PlayerEntity player, CallbackInfo ci) {
        ScreenHandler handler = (ScreenHandler) (Object) this;
        if (slotIndex < 0 || slotIndex >= handler.slots.size()) return;
        Slot slot = handler.getSlot(slotIndex);
        ItemStack result = slot.getStack();
        if (result.isEmpty() || MMOItemsGameplayMod.template(result) == null) return;

        String option = null;
        if (slot instanceof CraftingResultSlot) option = "disable-crafting";
        else if (slot instanceof FurnaceOutputSlot) option = "disable-smelting";
        else if (handler instanceof SmithingScreenHandler && slotIndex == 3) option = "disable-smithing";
        else if (handler instanceof AnvilScreenHandler && slotIndex == 2) option = "disable-repairing";

        if (option != null && MMOItemsLegacyItemOptions.bool(result, option, false)) ci.cancel();
    }
}
