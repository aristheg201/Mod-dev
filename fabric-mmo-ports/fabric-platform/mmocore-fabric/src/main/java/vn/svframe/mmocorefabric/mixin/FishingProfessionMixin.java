package vn.svframe.mmocorefabric.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import vn.svframe.mmocorefabric.MMOCoreProfessionExperienceMod;

@Mixin(FishingBobberEntity.class)
public abstract class FishingProfessionMixin {
    @Redirect(
            method = "use",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/World;spawnEntity(Lnet/minecraft/entity/Entity;)Z",
                    ordinal = 0))
    private boolean mmocore$fishProfessionSource(World world, Entity entity, ItemStack usedItem) {
        FishingBobberEntity bobber = (FishingBobberEntity) (Object) this;
        if (entity instanceof ItemEntity item && bobber.getPlayerOwner() instanceof ServerPlayerEntity player) {
            MMOCoreProfessionExperienceMod.awardFish(player, item.getStack().copy());
        }
        return world.spawnEntity(entity);
    }
}
