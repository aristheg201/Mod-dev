package dev.svextract.mixin;

import dev.svextract.compat.ActorGunAdapter;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.registry.RegistryKeys;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Labels Point Blank's existing damage invocation for SVFrameLib's single combat hook.
 * Does not issue a second damage call or recompute damage, armor, critical hits or headshots.
 */
@Pseudo
@Mixin(targets="com.vicmatskiv.pointblank.item.HurtingItem",remap=false)
public abstract class PointBlankDamageTypeMixin {
    @ModifyVariable(method="hurtEntity",at=@At("STORE"),ordinal=0,remap=false)
    private DamageSource svextract$projectileType(DamageSource source){
        LivingEntity actor=ActorGunAdapter.HIT_ACTOR.get();
        if(actor==null && source.getAttacker() instanceof LivingEntity living)actor=living;
        if(actor==null)return source;
        return new DamageSource(actor.getWorld().getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(DamageTypes.MOB_PROJECTILE),actor,actor);
    }
}
