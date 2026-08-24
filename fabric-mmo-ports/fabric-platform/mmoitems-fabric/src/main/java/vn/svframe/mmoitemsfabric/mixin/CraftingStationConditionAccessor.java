package vn.svframe.mmoitemsfabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(targets = "vn.svframe.mmoitemsfabric.MMOItemsCraftingStationMod$Condition", remap = false)
public interface CraftingStationConditionAccessor {
    @Accessor("type") String mmoitems$getType();
    @Accessor("params") Map<String, String> mmoitems$getParams();
}
