package vn.svframe.mmoitemsfabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(targets = "vn.svframe.mmoitemsfabric.MMOItemsCraftingStationMod$Recipe", remap = false)
public interface CraftingStationRecipeAccessor {
    @Accessor("conditions") List<Object> mmoitems$getConditions();
    @Accessor("craftingTimeSeconds") int mmoitems$getCraftingTimeSeconds();
    @Accessor("outputItem") boolean mmoitems$getOutputItem();
    @Accessor("silentCraft") boolean mmoitems$getSilentCraft();
}
