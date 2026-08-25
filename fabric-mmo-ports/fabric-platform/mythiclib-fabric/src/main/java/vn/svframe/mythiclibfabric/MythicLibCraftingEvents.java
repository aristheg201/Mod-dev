package vn.svframe.mythiclibfabric;

import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.mythiclibfabric.runtime.MythicLibCraftingRuntime;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Cancellable native Fabric event surface equivalent to MythicCraftItemEvent. */
public final class MythicLibCraftingEvents {
    @FunctionalInterface public interface BeforeListener { void before(BeforeCraft event); }
    @FunctionalInterface public interface AfterListener { void after(AfterCraft event); }

    public static final class BeforeCraft {
        private final ServerPlayerEntity player;
        private final MythicLibCraftingRuntime.Recipe recipe;
        private final boolean craftToCompletion;
        private ItemStack result;
        private boolean cancelled;

        private BeforeCraft(ServerPlayerEntity player, MythicLibCraftingRuntime.Recipe recipe, ItemStack result, boolean craftToCompletion) {
            this.player = player;
            this.recipe = recipe;
            this.result = result.copy();
            this.craftToCompletion = craftToCompletion;
        }
        public ServerPlayerEntity player() { return player; }
        public MythicLibCraftingRuntime.Recipe recipe() { return recipe; }
        public boolean craftToCompletion() { return craftToCompletion; }
        public ItemStack result() { return result.copy(); }
        public void setResult(ItemStack result) { this.result = result == null ? ItemStack.EMPTY : result.copy(); }
        public boolean cancelled() { return cancelled; }
        public void cancel() { cancelled = true; }
        public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    }

    public record AfterCraft(ServerPlayerEntity player, MythicLibCraftingRuntime.Recipe recipe, ItemStack result, int craftNumber) { }

    private static final List<BeforeListener> BEFORE = new CopyOnWriteArrayList<>();
    private static final List<AfterListener> AFTER = new CopyOnWriteArrayList<>();
    private MythicLibCraftingEvents() {}

    public static AutoCloseable registerBefore(BeforeListener listener) { BEFORE.add(listener); return () -> BEFORE.remove(listener); }
    public static AutoCloseable registerAfter(AfterListener listener) { AFTER.add(listener); return () -> AFTER.remove(listener); }

    static BeforeCraft fireBefore(ServerPlayerEntity player, MythicLibCraftingRuntime.Recipe recipe, ItemStack result, boolean completion) {
        BeforeCraft event = new BeforeCraft(player, recipe, result, completion);
        for (BeforeListener listener : BEFORE) listener.before(event);
        return event;
    }
    static void fireAfter(ServerPlayerEntity player, MythicLibCraftingRuntime.Recipe recipe, ItemStack result, int number) {
        AfterCraft event = new AfterCraft(player, recipe, result.copy(), number);
        for (AfterListener listener : AFTER) listener.after(event);
    }
}
