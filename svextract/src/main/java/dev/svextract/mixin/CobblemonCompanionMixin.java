package dev.svextract.mixin;

import dev.svextract.SvExtract;
import dev.svextract.compat.CobblemonAdapter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets="com.cobblemon.mod.common.pokemon.Pokemon",remap=false)
public abstract class CobblemonCompanionMixin {
    @Inject(method="sendOut",at=@At("HEAD"),cancellable=true,remap=false)
    private void svextract$oneCompanion(CallbackInfoReturnable<Object> ci){
        if(SvExtract.runtime()==null)return;
        try{
            java.util.UUID owner=CobblemonAdapter.owner(this);
            if(owner!=null && !SvExtract.runtime().allowCompanion(owner,CobblemonAdapter.identity(this)))ci.setReturnValue(null);
        }catch(ReflectiveOperationException e){
            SvExtract.LOGGER.error("Cannot validate Cobblemon companion identity; send-out blocked",e);ci.setReturnValue(null);
        }
    }
}
