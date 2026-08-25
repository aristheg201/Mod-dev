package vn.svframe.mythiclibfabric.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vn.svframe.mythiclibfabric.MythicLibPassiveMod;

import java.util.LinkedHashMap;
import java.util.Map;

/** Fires SHOOT_BOW only when a crossbow actually launches its loaded projectiles. */
@Mixin(CrossbowItem.class)
public abstract class CrossbowTriggerMixin {
    @Inject(method = "shootAll", at = @At("TAIL"))
    private void mythiclib$crossbowShot(World world,
                                        LivingEntity shooter,
                                        Hand hand,
                                        ItemStack stack,
                                        float speed,
                                        float divergence,
                                        @Nullable LivingEntity target,
                                        CallbackInfo ci) {
        if (world.isClient || !(shooter instanceof ServerPlayerEntity player)) return;

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("item", Registries.ITEM.getId(stack.getItem()).toString());
        context.put("item-count", stack.getCount());
        context.put("hand", hand.name());
        context.put("speed", speed);
        context.put("divergence", divergence);
        if (target != null) context.put("target-uuid", target.getUuidAsString());
        MythicLibPassiveMod.fire(player.getUuid(), "SHOOT_BOW",
                target == null ? player.getUuid() : target.getUuid(), context);
    }
}
