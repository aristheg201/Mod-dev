package vn.svarcade.tcg.mixin;

import com.cobblemon.mod.common.entity.pokeball.EmptyPokeBallEntity;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vn.svarcade.tcg.physical.BlankCapture;

/** Cobblemon 1.8.1 emits its captured event AFTER storage insertion. Block that insertion for scoped card attempts. */
@Mixin(EmptyPokeBallEntity.class)
public abstract class BlankCaptureMixin {
    @Inject(method="drop",at=@At("HEAD"),cancellable=true,remap=false,require=1)
    private void cardworlds$noPokeballRefund(CallbackInfo callback) {
        EmptyPokeBallEntity ball=(EmptyPokeBallEntity)(Object)this;
        if(BlankCapture.isCardAttempt(ball)){ball.discard();ball.getCaptureFuture().complete(false);callback.cancel();}
    }
    @Redirect(method="shakeBall$lambda$0",remap=false,
        at=@At(value="INVOKE",target="Lcom/cobblemon/mod/common/api/storage/party/PlayerPartyStore;add(Lcom/cobblemon/mod/common/pokemon/Pokemon;)Z",remap=false),require=1)
    private static boolean cardworlds$sealInsteadOfStore(PlayerPartyStore party,Pokemon pokemon,
            PokemonEntity entity,EmptyPokeBallEntity ball,ServerPlayerEntity player) {
        if(BlankCapture.isCardAttempt(ball))return BlankCapture.seal(ball,entity,player,pokemon);
        return party.add(pokemon);
    }
}
