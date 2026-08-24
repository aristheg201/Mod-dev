package vn.svframe.mmoitemsfabric.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.EnchantmentScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vn.svframe.mmoitemsfabric.MMOItemsGameplayMod;
import vn.svframe.mmoitemsfabric.MMOItemsLegacyItemOptions;

/** Prevents enchanting MMOItems whose legacy config disables enchanting. */
@Mixin(EnchantmentScreenHandler.class)
public abstract class EnchantmentRestrictionMixin {
    @Inject(method = "onButtonClick", at = @At("HEAD"), cancellable = true)
    private void mmoitems$disableEnchanting(PlayerEntity player, int id, CallbackInfoReturnable<Boolean> cir) {
        EnchantmentScreenHandler handler = (EnchantmentScreenHandler) (Object) this;
        ItemStack stack = handler.getSlot(0).getStack();
        if (stack.isEmpty() || MMOItemsGameplayMod.template(stack) == null) return;
        if (MMOItemsLegacyItemOptions.bool(stack, "disable-enchanting", false)) cir.setReturnValue(false);
    }
}
