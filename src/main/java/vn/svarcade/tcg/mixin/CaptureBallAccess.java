package vn.svarcade.tcg.mixin;
import com.cobblemon.mod.common.entity.pokeball.EmptyPokeBallEntity;
import net.minecraft.util.hit.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(EmptyPokeBallEntity.class)
public interface CaptureBallAccess {
    @Invoker("onEntityHit") void cardworlds$hit(EntityHitResult hit);
}
