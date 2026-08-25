package vn.svframe.mmocorefabric.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vn.svframe.mmocorefabric.MMOCoreProfessionExperienceMod;

@Mixin(ItemStack.class)
public abstract class ItemStackConsumeProfessionMixin {
    @Inject(method = "finishUsing", at = @At("HEAD"))
    private void mmocore$consumeProfessionSource(World world, LivingEntity user, CallbackInfoReturnable<ItemStack> cir) {
        if (world.isClient || !(user instanceof ServerPlayerEntity player)) return;
        ItemStack consumed = ((ItemStack) (Object) this).copy();
        MMOCoreProfessionExperienceMod.awardConsumed(player, consumed);
    }
}
