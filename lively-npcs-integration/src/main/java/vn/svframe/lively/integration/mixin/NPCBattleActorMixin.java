package vn.svframe.lively.integration.mixin;

import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.api.battles.model.ai.BattleAI;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.entity.npc.NPCBattleActor;
import com.cobblemon.mod.common.entity.npc.NPCEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vn.svframe.lively.api.LivelyApi;
import vn.svframe.lively.integration.cobblemon.LivelyCobblemonBattleAI;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Mixin(value = NPCBattleActor.class, remap = false)
public abstract class NPCBattleActorMixin {
    @Inject(method = "<init>(Lcom/cobblemon/mod/common/entity/npc/NPCEntity;Ljava/util/List;ILcom/cobblemon/mod/common/api/battles/model/ai/BattleAI;)V", at = @At("TAIL"), remap = false)
    private void lively$installCombatAI(NPCEntity npc, List<? extends BattlePokemon> pokemonList, int skill,
                                        BattleAI original, CallbackInfo ci) {
        if (!npc.getCommandTags().contains("lively") && !npc.getCommandTags().contains("lively_combat")) return;
        ((AIBattleActorAccessor) (Object) this).lively$setBattleAI(new LivelyCobblemonBattleAI(npc.getUuid(), skill));
    }

    @Inject(method = "win", at = @At("TAIL"), remap = false)
    private void lively$rememberWin(List<? extends BattleActor> winners, List<? extends BattleActor> losers, CallbackInfo ci) {
        lively$rememberOutcome("battle_won", losers);
    }

    @Inject(method = "lose", at = @At("TAIL"), remap = false)
    private void lively$rememberLoss(List<? extends BattleActor> winners, List<? extends BattleActor> losers, CallbackInfo ci) {
        lively$rememberOutcome("battle_lost", winners);
    }

    private void lively$rememberOutcome(String type, List<? extends BattleActor> opponents) {
        NPCBattleActor self = (NPCBattleActor) (Object) this;
        NPCEntity npc = self.getNpc();
        if (!npc.getCommandTags().contains("lively") && !npc.getCommandTags().contains("lively_combat")) return;
        if (LivelyApi.states() == null) return;
        String opponentIds = opponents.stream().map(actor -> actor.getUuid().toString()).collect(Collectors.joining(","));
        LivelyApi.states().get(npc.getUuid()).ifPresent(state -> state.remember(
                type,
                Map.of("opponents", opponentIds, "skill", Integer.toString(self.getSkill())),
                0.82D, 1D));
    }
}
