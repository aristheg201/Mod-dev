package dev.svextract.mixin;

import dev.svextract.mission.NpcRoles;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Covers damage sources which deliberately bypass vanilla invulnerability. */
@Mixin(LivingEntity.class)
public abstract class ServiceNpcGuardMixin {
    @Inject(method="damage",at=@At("HEAD"),cancellable=true)
    private void svextract$protectService(DamageSource source,float amount,CallbackInfoReturnable<Boolean> callback){
        LivingEntity entity=(LivingEntity)(Object)this;
        if(!entity.getWorld().isClient&&NpcRoles.protectedService(entity))callback.setReturnValue(false);
    }
    @Inject(method="onDeath",at=@At("HEAD"),cancellable=true)
    private void svextract$preventServiceDeath(DamageSource source,CallbackInfo callback){
        LivingEntity entity=(LivingEntity)(Object)this;
        if(!entity.getWorld().isClient&&NpcRoles.protectedService(entity)){
            entity.setHealth(entity.getMaxHealth());callback.cancel();
        }
    }
}
