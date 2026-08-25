package vn.svframe.mmoitemsfabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(targets = "vn.svframe.mmoitemsfabric.MMOItemsCraftingStationMod$Station", remap = false)
public interface CraftingStationStationAccessor {
    @Accessor("recipes") Map<String, Object> mmoitems$getRecipes();
}
