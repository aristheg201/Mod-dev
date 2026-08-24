package vn.svframe.mmocorefabric.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.CraftingResultSlot;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vn.svframe.mmocorefabric.MMOCoreProfessionExperienceMod;

@Mixin(CraftingResultSlot.class)
public abstract class CraftingResultProfessionMixin {
    @Shadow @Final private PlayerEntity player;

    @Inject(method = "onCrafted(Lnet/minecraft/item/ItemStack;I)V", at = @At("TAIL"))
    private void mmocore$craftProfessionSource(ItemStack stack, int amount, CallbackInfo ci) {
        if (!(player instanceof ServerPlayerEntity serverPlayer) || amount <= 0) return;
        MMOCoreProfessionExperienceMod.awardCraft(serverPlayer, stack.copy(), amount);
    }
}
