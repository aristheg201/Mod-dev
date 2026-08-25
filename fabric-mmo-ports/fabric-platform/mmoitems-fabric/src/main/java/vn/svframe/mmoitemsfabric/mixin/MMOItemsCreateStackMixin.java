package vn.svframe.mmoitemsfabric.mixin;

import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vn.svframe.mmoitemsfabric.MMOItemsFabricMod;
import vn.svframe.mmoitemsfabric.MMOItemsGameplayMod;
import vn.svframe.mmoitemsfabric.MMOItemsRevisionRuntime;

@Mixin(value = MMOItemsFabricMod.class, remap = false)
public abstract class MMOItemsCreateStackMixin {
    @Inject(method = "createStack", at = @At("RETURN"), remap = false)
    private static void mmoitems$hydrateCreatedStack(String type, String id, int amount,
                                                     CallbackInfoReturnable<ItemStack> cir) {
        ItemStack stack = cir.getReturnValue();
        MMOItemsGameplayMod.hydrate(stack);
        MMOItemsRevisionRuntime.stampFresh(stack);
    }
}
