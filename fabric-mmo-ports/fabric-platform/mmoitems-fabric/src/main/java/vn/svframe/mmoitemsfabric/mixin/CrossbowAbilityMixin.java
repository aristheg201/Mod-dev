package vn.svframe.mmoitemsfabric.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vn.svframe.mmoitemsfabric.MMOItemsFabricMod;

import java.util.LinkedHashMap;
import java.util.Map;

/** Fires MMOItems SHOOT_BOW from the exact crossbow stack that launched projectiles. */
@Mixin(CrossbowItem.class)
public abstract class CrossbowAbilityMixin {
    @Inject(method = "shootAll", at = @At("TAIL"))
    private void mmoitems$crossbowShot(World world,
                                       LivingEntity shooter,
                                       Hand hand,
                                       ItemStack stack,
                                       float speed,
                                       float divergence,
                                       @Nullable LivingEntity target,
                                       CallbackInfo ci) {
        if (world.isClient || !(shooter instanceof ServerPlayerEntity player)) return;
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("hand", hand.name());
        context.put("speed", speed);
        context.put("divergence", divergence);
        if (target != null) context.put("target", target.getUuid());
        MMOItemsFabricMod.fireItemStackTrigger(player, stack, "SHOOT_BOW", context);
    }
}
