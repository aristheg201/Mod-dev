package vn.svframe.mythiclibfabric.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vn.svframe.mythiclibfabric.FabricDamageBridge;

@Mixin(LivingEntity.class)
abstract class LivingEntityDamageMixin {
    @Inject(method = "modifyAppliedDamage", at = @At("RETURN"), cancellable = true)
    private void mythiclibfabric$applyRpgDamage(DamageSource source, float amount, CallbackInfoReturnable<Float> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        cir.setReturnValue(FabricDamageBridge.modifyAppliedDamage(self, source, cir.getReturnValue()));
    }
}
