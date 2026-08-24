package vn.svframe.mmoitemsfabric.mixin;

import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vn.svframe.mmoitemsfabric.MMOItemsCraftingStationMod;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Restores original queue cancellation/compaction and output-item behavior. */
@Mixin(value = MMOItemsCraftingStationMod.class, remap = false)
public abstract class CraftingStationQueueParityMixin {
    private static final ThreadLocal<Boolean> MMOITEMS_OUTPUT_ITEM = ThreadLocal.withInitial(() -> true);

    @Inject(method = "claim", at = @At("HEAD"), cancellable = true, remap = false)
    private static void mmoitems$cancelOrPrepareClaim(ServerPlayerEntity player, UUID queueId, CallbackInfo ci) {
        List<Object> queue = CraftingStationAccessors.mmoitems$getQueue();
        Object lock = CraftingStationAccessors.mmoitems$getQueueLock();
        synchronized (lock) {
            for (int index = 0; index < queue.size(); index++) {
                Object raw = queue.get(index);
                CraftingQueueEntryAccessor entry = (CraftingQueueEntryAccessor) raw;
                if (!entry.mmoitems$getId().equals(queueId) || !entry.mmoitems$getPlayer().equals(player.getUuid())) continue;

                CraftingStationRecipeAccessor recipe = recipe(entry);
                MMOITEMS_OUTPUT_ITEM.set(recipe == null || recipe.mmoitems$getOutputItem());
                long now = System.currentTimeMillis();
                if (now >= entry.mmoitems$getCompletion()) return;

                long craftingTime = recipe == null ? 0L : Math.max(0L, recipe.mmoitems$getCraftingTimeSeconds()) * 1000L;
                long delay = Math.min(Math.max(0L, entry.mmoitems$getCompletion() - now), craftingTime);
                String station = entry.mmoitems$getStation();
                UUID owner = entry.mmoitems$getPlayer();
                queue.remove(index);
                if (delay > 0L) {
                    for (Object laterRaw : queue) {
                        CraftingQueueEntryAccessor later = (CraftingQueueEntryAccessor) laterRaw;
                        if (!later.mmoitems$getPlayer().equals(owner) || !later.mmoitems$getStation().equals(station)) continue;
                        if (later.mmoitems$getCompletion() > entry.mmoitems$getCompletion()) {
                            later.mmoitems$setCompletion(Math.max(now, later.mmoitems$getCompletion() - delay));
                        }
                    }
                }
                CraftingStationAccessors.mmoitems$saveQueueAsync();
                MMOITEMS_OUTPUT_ITEM.remove();
                ci.cancel();
                return;
            }
        }
    }

    @Redirect(
            method = "claim",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerInventory;insertStack(Lnet/minecraft/item/ItemStack;)Z"),
            remap = true)
    private static boolean mmoitems$respectOutputItem(PlayerInventory inventory, ItemStack output) {
        return !Boolean.TRUE.equals(MMOITEMS_OUTPUT_ITEM.get()) || inventory.insertStack(output);
    }

    @Inject(method = "claim", at = @At("RETURN"), remap = false)
    private static void mmoitems$clearClaimState(ServerPlayerEntity player, UUID queueId, CallbackInfo ci) {
        MMOITEMS_OUTPUT_ITEM.remove();
    }

    private static CraftingStationRecipeAccessor recipe(CraftingQueueEntryAccessor entry) {
        Map<String, Object> stations = CraftingStationAccessors.mmoitems$getStations();
        Object rawStation = stations.get(entry.mmoitems$getStation());
        if (!(rawStation instanceof CraftingStationStationAccessor station)) return null;
        Object rawRecipe = station.mmoitems$getRecipes().get(entry.mmoitems$getRecipe());
        return rawRecipe instanceof CraftingStationRecipeAccessor recipe ? recipe : null;
    }
}
