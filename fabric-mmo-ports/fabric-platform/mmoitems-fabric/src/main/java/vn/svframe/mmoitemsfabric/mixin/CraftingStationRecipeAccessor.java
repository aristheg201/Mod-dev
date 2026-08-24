package vn.svframe.mmoitemsfabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "vn.svframe.mmoitemsfabric.MMOItemsCraftingStationMod$Recipe", remap = false)
public interface CraftingStationRecipeAccessor {
    @Accessor("craftingTimeSeconds") int mmoitems$getCraftingTimeSeconds();
    @Accessor("outputItem") boolean mmoitems$getOutputItem();
    @Accessor("silentCraft") boolean mmoitems$getSilentCraft();
}
