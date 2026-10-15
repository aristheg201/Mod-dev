package dev.svextract.mixin;

import dev.svextract.SvExtract;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.UUID;

/** Prevent Point Blank's own packets from spending ammo or resolving hits during protected writes. */
@Pseudo
@Mixin(targets="com.vicmatskiv.pointblank.item.GunItem",remap=false)
public abstract class PointBlankTransactionGuardMixin {
    @Inject(method="handleClientHitScanFireRequest",at=@At("HEAD"),cancellable=true,remap=false)
    private void svextract$hitscan(ServerPlayerEntity p,@Coerce Object mode,UUID item,int slot,int sequence,boolean aiming,boolean left,long time,CallbackInfo ci){if(SvExtract.locked(p)||SvExtract.runtime()!=null&&SvExtract.runtime().frozen(p.getUuid()))ci.cancel();}
    @Inject(method="handleClientProjectileFireRequest",at=@At("HEAD"),cancellable=true,remap=false)
    private void svextract$projectile(ServerPlayerEntity p,@Coerce Object mode,UUID item,int slot,int sequence,boolean aiming,double x,double y,double z,double dx,double dy,double dz,int target,long time,CallbackInfo ci){if(SvExtract.locked(p)||SvExtract.runtime()!=null&&SvExtract.runtime().frozen(p.getUuid()))ci.cancel();}
    @Inject(method="handleClientReloadRequest",at=@At("HEAD"),cancellable=true,remap=false)
    private void svextract$reload(ServerPlayerEntity p,ItemStack stack,UUID item,int slot,@Coerce Object mode,CallbackInfo ci){if(SvExtract.locked(p)||SvExtract.runtime()!=null&&SvExtract.runtime().frozen(p.getUuid()))ci.cancel();}
}
