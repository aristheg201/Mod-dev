package vn.svframe.mmoitemsfabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
import vn.svframe.mmoitemsfabric.MMOItemsCraftingStationMod;

import java.util.List;
import java.util.Map;

@Mixin(value = MMOItemsCraftingStationMod.class, remap = false)
public interface CraftingStationAccessors {
    @Accessor("QUEUE")
    static List<Object> mmoitems$getQueue() { throw new AssertionError(); }

    @Accessor("QUEUE_LOCK")
    static Object mmoitems$getQueueLock() { throw new AssertionError(); }

    @Accessor("STATIONS")
    static Map<String, Object> mmoitems$getStations() { throw new AssertionError(); }

    @Invoker("saveQueueAsync")
    static void mmoitems$saveQueueAsync() { throw new AssertionError(); }
}
