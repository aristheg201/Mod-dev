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
import vn.svframe.mmoitemsfabric.MMOItemsGemInteraction;

@Mixin(ScreenHandler.class)
public abstract class ScreenHandlerGemInteractionMixin {
    @Inject(method = "onSlotClick", at = @At("HEAD"), cancellable = true)
    private void mmoitems$cursorGemstone(int slotIndex, int button, SlotActionType actionType,
                                        PlayerEntity player, CallbackInfo ci) {
        if (actionType != SlotActionType.PICKUP || !(player instanceof ServerPlayerEntity serverPlayer)) return;
        ScreenHandler handler = (ScreenHandler) (Object) this;
        if (slotIndex < 0 || slotIndex >= handler.slots.size()) return;

        ItemStack cursor = handler.getCursorStack();
        Slot slot = handler.getSlot(slotIndex);
        ItemStack target = slot.getStack();
        if (cursor.isEmpty() || target.isEmpty()) return;

        MMOItemsGemInteraction.Result result = MMOItemsGemInteraction.apply(serverPlayer, cursor, target);
        if (result == MMOItemsGemInteraction.Result.NONE) return;

        ci.cancel();
        cursor.decrement(1);
        if (cursor.isEmpty()) handler.setCursorStack(ItemStack.EMPTY);
        else handler.setCursorStack(cursor);

        if (result == MMOItemsGemInteraction.Result.SUCCESS) {
            slot.setStack(target);
            slot.markDirty();
            serverPlayer.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, 1.0f, 2.0f);
        } else {
            serverPlayer.playSound(SoundEvents.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
        }
        handler.sendContentUpdates();
    }
}
