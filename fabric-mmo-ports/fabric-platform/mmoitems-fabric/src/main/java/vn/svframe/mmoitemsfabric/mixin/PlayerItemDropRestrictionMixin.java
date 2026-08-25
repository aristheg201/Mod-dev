package vn.svframe.mmoitemsfabric.mixin;

import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vn.svframe.mmoitemsfabric.MMOItemsGameplayMod;
import vn.svframe.mmoitemsfabric.MMOItemsLegacyItemOptions;

/** Enforces disable-drop and disable-death-drop on the authoritative server drop path. */
@Mixin(PlayerEntity.class)
public abstract class PlayerItemDropRestrictionMixin {
    @Inject(method = "dropItem(Lnet/minecraft/item/ItemStack;ZZ)Lnet/minecraft/entity/ItemEntity;", at = @At("HEAD"), cancellable = true)
    private void mmoitems$preventRestrictedDrop(ItemStack stack, boolean throwRandomly, boolean retainOwnership,
                                                 CallbackInfoReturnable<ItemEntity> cir) {
        if (!((Object) this instanceof ServerPlayerEntity player) || stack == null || stack.isEmpty()) return;
        if (MMOItemsGameplayMod.template(stack) == null) return;
        boolean death = player.isDead() || player.getHealth() <= 0.0f;
        String option = death ? "disable-death-drop" : "disable-drop";
        if (MMOItemsLegacyItemOptions.bool(stack, option, false)) cir.setReturnValue(null);
    }
}
