package vn.svframe.lively.integration.mixin;

import com.cobblemon.mod.common.api.battles.model.actor.AIBattleActor;
import com.cobblemon.mod.common.api.battles.model.ai.BattleAI;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = AIBattleActor.class, remap = false)
public interface AIBattleActorAccessor {
    @Mutable
    @Accessor(value = "battleAI", remap = false)
    void lively$setBattleAI(BattleAI battleAI);
}
