package dev.svextract.mixin;

import dev.svextract.SvExtract;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.function.BooleanSupplier;

@Mixin(MinecraftServer.class)
public abstract class ServerTickTimerMixin {
    @Unique private long svextract$start,svextract$previous,svextract$interval;
    @Inject(method="tick",at=@At("HEAD"))
    private void svextract$start(BooleanSupplier shouldKeepTicking,CallbackInfo ci){
        svextract$start=System.nanoTime();svextract$interval=svextract$previous==0?50_000_000L:svextract$start-svextract$previous;svextract$previous=svextract$start;
    }
    @Inject(method="tick",at=@At("RETURN"))
    private void svextract$record(BooleanSupplier shouldKeepTicking,CallbackInfo ci){
        if(SvExtract.runtime()!=null)SvExtract.runtime().recordTick(System.nanoTime()-svextract$start,svextract$interval);
    }
}
