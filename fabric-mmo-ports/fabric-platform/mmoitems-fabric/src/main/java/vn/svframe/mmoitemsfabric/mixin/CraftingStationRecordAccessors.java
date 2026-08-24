package vn.svframe.mmoitemsfabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

public final class CraftingStationRecordAccessors {
    private CraftingStationRecordAccessors() {}

    @Mixin(targets = "vn.svframe.mmoitemsfabric.MMOItemsCraftingStationMod$Station", remap = false)
    public interface Station {
        @Accessor("recipes") Map<String, Object> mmoitems$getRecipes();
    }

    @Mixin(targets = "vn.svframe.mmoitemsfabric.MMOItemsCraftingStationMod$Recipe", remap = false)
    public interface Recipe {
        @Accessor("craftingTimeSeconds") int mmoitems$getCraftingTimeSeconds();
        @Accessor("outputItem") boolean mmoitems$getOutputItem();
        @Accessor("silentCraft") boolean mmoitems$getSilentCraft();
    }
}
