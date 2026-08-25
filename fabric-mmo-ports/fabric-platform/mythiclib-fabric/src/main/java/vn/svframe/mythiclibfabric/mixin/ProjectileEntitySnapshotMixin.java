package vn.svframe.mythiclibfabric.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vn.svframe.mythiclibfabric.PassiveSkillRuntime;
import vn.svframe.mythiclibfabric.runtime.ProjectilePassiveSnapshotHolder;

@Mixin(ProjectileEntity.class)
public abstract class ProjectileEntitySnapshotMixin implements ProjectilePassiveSnapshotHolder {
    @Unique
    private PassiveSkillRuntime.Snapshot mythiclib$passiveSnapshot;

    @Inject(method = "setOwner", at = @At("TAIL"))
    private void mythiclib$capturePassiveSnapshot(Entity owner, CallbackInfo ci) {
        if (!((Object) this instanceof PersistentProjectileEntity)) return;
        if (owner instanceof ServerPlayerEntity player) {
            mythiclib$passiveSnapshot = PassiveSkillRuntime.snapshot(player.getUuid());
        } else {
            mythiclib$passiveSnapshot = null;
        }
    }

    @Override
    public PassiveSkillRuntime.Snapshot mythiclib$getPassiveSnapshot() {
        return mythiclib$passiveSnapshot;
    }

    @Override
    public void mythiclib$setPassiveSnapshot(PassiveSkillRuntime.Snapshot snapshot) {
        mythiclib$passiveSnapshot = snapshot;
    }
}
