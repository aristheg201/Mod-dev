package vn.svframe.mmocorefabric.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vn.svframe.mmocorefabric.MMOCoreProfessionExperienceMod;

@Mixin(ProjectileEntity.class)
public abstract class ProjectileProfessionMixin {
    @Inject(method = "setOwner", at = @At("TAIL"))
    private void mmocore$captureProjectileLaunch(Entity owner, CallbackInfo ci) {
        if (!((Object) this instanceof PersistentProjectileEntity projectile)) return;
        if (owner instanceof ServerPlayerEntity player) {
            MMOCoreProfessionExperienceMod.recordProjectileLaunch(projectile, player);
        }
    }
}
