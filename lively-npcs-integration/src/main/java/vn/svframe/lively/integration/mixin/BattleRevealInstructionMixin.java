package vn.svframe.lively.integration.mixin;

import com.cobblemon.mod.common.api.battles.interpreter.Effect;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.battles.interpreter.instructions.AbilityInstruction;
import com.cobblemon.mod.common.battles.interpreter.instructions.EndItemInstruction;
import com.cobblemon.mod.common.battles.interpreter.instructions.ItemInstruction;
import com.cobblemon.mod.common.battles.interpreter.instructions.MoveInstruction;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vn.svframe.lively.integration.cobblemon.CobblemonBattleKnowledge;

/** Observes only public interpreter instructions emitted by Showdown. */
public final class BattleRevealInstructionMixin {
    private BattleRevealInstructionMixin() {}

    @Mixin(value = MoveInstruction.class, remap = false)
    public static abstract class MoveReveal {
        @Inject(method = "invoke", at = @At("TAIL"), remap = false)
        private void lively$observeMove(PokemonBattle battle, CallbackInfo ci) {
            MoveInstruction self = (MoveInstruction) (Object) this;
            BattlePokemon user = self.getUserPokemon();
            if (user != null) CobblemonBattleKnowledge.observeMove(battle, user, self.getMove());
        }
    }

    @Mixin(value = AbilityInstruction.class, remap = false)
    public static abstract class AbilityReveal {
        @Inject(method = "invoke", at = @At("HEAD"), remap = false)
        private void lively$observeAbility(PokemonBattle battle, CallbackInfo ci) {
            AbilityInstruction self = (AbilityInstruction) (Object) this;
            try {
                BattlePokemon user = self.getMessage().battlePokemon(0, battle);
                Effect effect = self.getMessage().effectAt(1);
                if (user != null && effect != null) CobblemonBattleKnowledge.observeAbility(battle, user, effect);
            } catch (RuntimeException ignored) { }
        }
    }

    @Mixin(value = ItemInstruction.class, remap = false)
    public static abstract class ItemReveal {
        @Inject(method = "invoke", at = @At("HEAD"), remap = false)
        private void lively$observeItem(PokemonBattle battle, CallbackInfo ci) {
            ItemInstruction self = (ItemInstruction) (Object) this;
            try {
                BattlePokemon user = self.getMessage().battlePokemon(0, battle);
                Effect effect = self.getMessage().effectAt(1);
                if (user != null && effect != null) CobblemonBattleKnowledge.observeItem(battle, user, effect);
            } catch (RuntimeException ignored) { }
        }
    }

    @Mixin(value = EndItemInstruction.class, remap = false)
    public static abstract class EndItemReveal {
        @Inject(method = "invoke", at = @At("HEAD"), remap = false)
        private void lively$observeEndItem(PokemonBattle battle, CallbackInfo ci) {
            EndItemInstruction self = (EndItemInstruction) (Object) this;
            try {
                BattlePokemon user = self.getMessage().battlePokemon(0, battle);
                Effect effect = self.getMessage().effectAt(1);
                if (user != null && effect != null) CobblemonBattleKnowledge.observeItem(battle, user, effect);
            } catch (RuntimeException ignored) { }
        }
    }
}
