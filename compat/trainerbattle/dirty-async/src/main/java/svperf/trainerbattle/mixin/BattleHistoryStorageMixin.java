package svperf.trainerbattle.mixin;

import kiwiapollo.cobblemontrainerbattle.battle.BattleHistory;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import svperf.trainerbattle.DirtySaveController;

import java.util.UUID;

@Pseudo
@Mixin(targets = "kiwiapollo.cobblemontrainerbattle.battle.BattleHistoryStorage", remap = false)
public abstract class BattleHistoryStorageMixin {
    @Inject(method = "onServerStarted", at = @At("TAIL"), remap = false)
    private void svperf$serverStarted(MinecraftServer server, CallbackInfo ci) {
        DirtySaveController.started();
    }

    @Inject(method = "get", at = @At("RETURN"), remap = false)
    private void svperf$bindOwner(UUID player, Identifier trainer,
                                  CallbackInfoReturnable<BattleHistory> cir) {
        DirtySaveController.attach(cir.getReturnValue(), player);
    }

    @Inject(method = "onEndTick", at = @At("HEAD"), cancellable = true, remap = false)
    private void svperf$tick(MinecraftServer server, CallbackInfo ci) {
        ci.cancel(); // Suppress the original every-player, every-20-minute save.
        DirtySaveController.tick(server);
    }

    @Inject(method = "onServerStopped", at = @At("HEAD"), cancellable = true, remap = false)
    private void svperf$stop(MinecraftServer server, CallbackInfo ci) {
        ci.cancel(); // Never execute the original blocking, all-player shutdown save.
        DirtySaveController.stopped(server);
    }
}
