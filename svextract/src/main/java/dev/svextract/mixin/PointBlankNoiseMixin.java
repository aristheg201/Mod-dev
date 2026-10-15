package dev.svextract.mixin;

import dev.svextract.SvExtract;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Point Blank 2.2.0 public fire-sound hook, verified against its published Fabric JAR.
 * Optional target; does not alter ammo, recoil, projectiles or hit damage.
 */
@Pseudo
@Mixin(targets="com.vicmatskiv.pointblank.feature.SoundFeature",remap=false)
public abstract class PointBlankNoiseMixin {
    @Inject(method="playFireSound",at=@At("HEAD"),remap=false)
    private static void svextract$shot(PlayerEntity player,ItemStack stack,CallbackInfo ci){
        if(player instanceof ServerPlayerEntity p && SvExtract.runtime()!=null)SvExtract.runtime().noise(p,48);
    }
}
