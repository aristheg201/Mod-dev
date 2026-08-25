package vn.svframe.mmocorefabric.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.ForgingScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vn.svframe.mmocorefabric.MMOCoreSpecialProfessionExperienceMod;

/** Awards the legacy repair profession source when an anvil output is actually taken. */
@Mixin(AnvilScreenHandler.class)
public abstract class RepairProfessionMixin {
    @Inject(method = "onTakeOutput", at = @At("HEAD"))
    private void mmocore$repairProfessionSource(PlayerEntity player, ItemStack output, CallbackInfo ci) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) return;
        ForgingScreenHandler handler = (ForgingScreenHandler) (Object) this;
        ItemStack input = handler.getSlot(0).getStack();
        if (input.isEmpty() || output.isEmpty()) return;
        MMOCoreSpecialProfessionExperienceMod.awardRepair(serverPlayer, input.copy(), output.copy());
    }
}
