package dev.svextract.mixin;

import dev.svextract.SvExtract;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Two clock reads, no per-shot allocation or extra world query. Measures real server fire processing. */
@Pseudo
@Mixin(targets="com.vicmatskiv.pointblank.item.GunItem",remap=false)
public abstract class PointBlankTimingMixin {
    @Unique private long svextract$requestStarted;
    @Inject(method={"handleClientHitScanFireRequest","handleClientProjectileFireRequest"},at=@At("HEAD"),remap=false)
    private void svextract$startFire(CallbackInfo ci){svextract$requestStarted=System.nanoTime();}
    @Inject(method={"handleClientHitScanFireRequest","handleClientProjectileFireRequest"},at=@At("RETURN"),remap=false)
    private void svextract$recordFire(CallbackInfo ci){
        if(SvExtract.runtime()!=null)SvExtract.runtime().recordPointBlankRequest(System.nanoTime()-svextract$requestStarted);
    }
}
