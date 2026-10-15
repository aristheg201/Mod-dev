package svperf.trainerbattle.mixin;

import kiwiapollo.cobblemontrainerbattle.battle.BattleHistory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import svperf.trainerbattle.DirtySaveController;

@Pseudo
@Mixin(targets = "kiwiapollo.cobblemontrainerbattle.battle.BattleHistory", remap = false)
public abstract class BattleHistoryMixin {
    @Inject(method = "setVictoryCount", at = @At("TAIL"), remap = false)
    private void svperf$victory(int count, CallbackInfo ci) {
        DirtySaveController.changed((BattleHistory) (Object) this);
    }

    @Inject(method = "setDefeatCount", at = @At("TAIL"), remap = false)
    private void svperf$defeat(int count, CallbackInfo ci) {
        DirtySaveController.changed((BattleHistory) (Object) this);
    }

    @Inject(method = "setKillCount", at = @At("TAIL"), remap = false)
    private void svperf$kill(int count, CallbackInfo ci) {
        DirtySaveController.changed((BattleHistory) (Object) this);
    }
}
