package vn.svframe.lively.integration.mixin;

import com.cobblemon.mod.common.api.battles.model.ai.BattleAI;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.entity.npc.NPCBattleActor;
import com.cobblemon.mod.common.entity.npc.NPCEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vn.svframe.lively.integration.cobblemon.LivelyCobblemonBattleAI;

import java.util.List;

@Mixin(NPCBattleActor.class)
public abstract class NPCBattleActorMixin {
    @Inject(method = "<init>(Lcom/cobblemon/mod/common/entity/npc/NPCEntity;Ljava/util/List;ILcom/cobblemon/mod/common/api/battles/model/ai/BattleAI;)V", at = @At("TAIL"))
    private void lively$installCombatAI(NPCEntity npc, List<? extends BattlePokemon> pokemonList, int skill,
                                        BattleAI original, CallbackInfo ci) {
        if (!npc.getCommandTags().contains("lively") && !npc.getCommandTags().contains("lively_combat")) return;
        ((AIBattleActorAccessor) (Object) this).lively$setBattleAI(new LivelyCobblemonBattleAI(npc.getUuid(), skill));
    }
}
