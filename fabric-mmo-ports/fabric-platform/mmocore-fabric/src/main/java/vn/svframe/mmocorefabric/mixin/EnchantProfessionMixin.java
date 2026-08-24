package vn.svframe.mmocorefabric.mixin;

import net.minecraft.enchantment.EnchantmentLevelEntry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.screen.EnchantmentScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vn.svframe.mmocorefabric.MMOCoreSpecialProfessionExperienceMod;

import java.util.List;

@Mixin(EnchantmentScreenHandler.class)
public abstract class EnchantProfessionMixin {
    @Unique private static final ThreadLocal<ServerPlayerEntity> mmocore$enchanter = new ThreadLocal<>();
    @Unique private static final ThreadLocal<List<EnchantmentLevelEntry>> mmocore$generated = new ThreadLocal<>();

    @Inject(method = "onButtonClick", at = @At("HEAD"))
    private void mmocore$beginEnchant(PlayerEntity player, int id, CallbackInfoReturnable<Boolean> cir) {
        mmocore$generated.remove();
        if (player instanceof ServerPlayerEntity serverPlayer) mmocore$enchanter.set(serverPlayer);
        else mmocore$enchanter.remove();
    }

    @Inject(method = "generateEnchantments", at = @At("RETURN"))
    private void mmocore$captureEnchantments(DynamicRegistryManager registryManager, ItemStack stack, int slot, int level,
                                              CallbackInfoReturnable<List<EnchantmentLevelEntry>> cir) {
        if (mmocore$enchanter.get() == null || cir.getReturnValue() == null) return;
        mmocore$generated.set(List.copyOf(cir.getReturnValue()));
    }

    @Inject(method = "onButtonClick", at = @At("RETURN"))
    private void mmocore$finishEnchant(PlayerEntity player, int id, CallbackInfoReturnable<Boolean> cir) {
        try {
            ServerPlayerEntity serverPlayer = mmocore$enchanter.get();
            List<EnchantmentLevelEntry> generated = mmocore$generated.get();
            if (Boolean.TRUE.equals(cir.getReturnValue()) && serverPlayer != null && generated != null && !generated.isEmpty()) {
                MMOCoreSpecialProfessionExperienceMod.awardEnchant(serverPlayer, generated);
            }
        } finally {
            mmocore$enchanter.remove();
            mmocore$generated.remove();
        }
    }
}
