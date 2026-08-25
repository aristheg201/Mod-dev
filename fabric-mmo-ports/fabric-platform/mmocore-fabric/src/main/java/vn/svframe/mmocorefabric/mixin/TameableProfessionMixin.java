package vn.svframe.mmocorefabric.mixin;

import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vn.svframe.mmocorefabric.MMOCoreProfessionExperienceMod;

@Mixin(TameableEntity.class)
public abstract class TameableProfessionMixin {
    @Inject(method = "setOwner", at = @At("TAIL"))
    private void mmocore$tameProfessionSource(PlayerEntity owner, CallbackInfo ci) {
        if (!(owner instanceof ServerPlayerEntity player)) return;
        MMOCoreProfessionExperienceMod.awardTame(player, (TameableEntity) (Object) this);
    }
}
