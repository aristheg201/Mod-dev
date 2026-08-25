package vn.svframe.mmoitemsfabric.mixin;

import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vn.svframe.mmoitemsfabric.MMOItemsFabricMod;
import vn.svframe.mmoitemsfabric.MMOItemsRequirementGate;
import vn.svframe.mmoitemsfabric.runtime.ability.AbilityDefinition;

import java.util.Map;
import java.util.UUID;

/** Prevents every ability ingress from bypassing the item's legacy requirements. */
@Mixin(value = MMOItemsFabricMod.class, remap = false)
public abstract class MMOItemsRequirementTriggerMixin {
    @Inject(method = "triggerStack", at = @At("HEAD"), cancellable = true, remap = false)
    private static void mmoitems$checkRequirements(ServerPlayerEntity player, ItemStack stack,
                                                   AbilityDefinition.Trigger trigger, UUID target,
                                                   Map<String, Object> extra, CallbackInfo ci) {
        if (!MMOItemsRequirementGate.canUse(player, stack)) ci.cancel();
    }
}
