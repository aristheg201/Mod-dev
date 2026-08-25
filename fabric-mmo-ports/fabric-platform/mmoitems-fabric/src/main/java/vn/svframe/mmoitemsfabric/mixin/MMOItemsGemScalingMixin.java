package vn.svframe.mmoitemsfabric.mixin;

import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vn.svframe.mmoitemsfabric.MMOItemsGameplayMod;
import vn.svframe.mmoitemsfabric.MMOItemsGemScalingRuntime;
import vn.svframe.mmoitemsfabric.runtime.gameplay.ItemStatProfile;

/** Adds the upgrade delta of each socketed gem after the base item stat calculation. */
@Mixin(value = MMOItemsGameplayMod.class, remap = false)
public abstract class MMOItemsGemScalingMixin {
    @Inject(method = "effectiveStats", at = @At("RETURN"), remap = false)
    private static void mmoitems$applyGemUpgradeHistory(ItemStack stack, CallbackInfoReturnable<ItemStatProfile> cir) {
        ItemStatProfile profile = cir.getReturnValue();
        if (profile == null) return;
        MMOItemsGemScalingRuntime.deltaStats(stack).forEach((stat, delta) -> profile.put(stat, profile.get(stat) + delta));
    }
}
