package vn.svframe.mmoitemsfabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.UUID;

@Mixin(targets = "vn.svframe.mmoitemsfabric.MMOItemsCraftingStationMod$QueueEntry", remap = false)
public interface CraftingQueueEntryAccessor {
    @Accessor("id") UUID mmoitems$getId();
    @Accessor("player") UUID mmoitems$getPlayer();
    @Accessor("station") String mmoitems$getStation();
    @Accessor("recipe") String mmoitems$getRecipe();
    @Accessor("completion") long mmoitems$getCompletion();

    @Mutable
    @Accessor("completion")
    void mmoitems$setCompletion(long value);
}
