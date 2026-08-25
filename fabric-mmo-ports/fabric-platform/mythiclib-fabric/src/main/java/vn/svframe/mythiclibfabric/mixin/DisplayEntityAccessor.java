package vn.svframe.mythiclibfabric.mixin;

import net.minecraft.entity.decoration.DisplayEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(DisplayEntity.class)
public interface DisplayEntityAccessor {
    @Invoker("setBillboardMode")
    void mythiclib$setBillboardMode(DisplayEntity.BillboardMode mode);
}
