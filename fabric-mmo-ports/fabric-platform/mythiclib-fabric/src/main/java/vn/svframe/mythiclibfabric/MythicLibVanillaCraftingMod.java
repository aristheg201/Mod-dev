package vn.svframe.mythiclibfabric;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.AbstractFurnaceScreenHandler;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.SmithingScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.mythiclibfabric.runtime.MythicLibCraftingRuntime;
import vn.svframe.mythiclibfabric.runtime.MythicLibStationMappings;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Native Fabric port of MythicLib 1.7.1's registered vanilla crafting mappings. */
public final class MythicLibVanillaCraftingMod {
    private static final Map<ScreenHandler, ItemStack> OVERRIDDEN = Collections.synchronizedMap(new WeakHashMap<>());
    private static volatile boolean initialized;
    private MythicLibVanillaCraftingMod() {}

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        ServerTickEvents.END_SERVER_TICK.register(MythicLibVanillaCraftingMod::tick);
    }

    public static boolean handleClick(ServerPlayerEntity player, ScreenHandler handler, int slotIndex, SlotActionType actionType) {
        StationView view = view(handler);
        if (view == null || slotIndex != view.mapping.resultSlot()) return false;
        MythicLibCraftingRuntime.SlotAccess access = access(handler, view.mapping);
        var match = MythicLibCraftingRuntime.match(view.mapping.station(), "", access, permission -> MythicLibPermissionBridge.has(player, permission));
        if (match.isEmpty()) return false;
        if (actionType != SlotActionType.PICKUP && actionType != SlotActionType.QUICK_MOVE) return true;
        craft(player, handler, view, actionType == SlotActionType.QUICK_MOVE);
        return true;
    }

    public static void refresh(ScreenHandler handler) {
        StationView view = view(handler);
        if (view == null || view.mapping.resultSlot() >= handler.slots.size()) return;
        Slot resultSlot = handler.getSlot(view.mapping.resultSlot());
        MythicLibCraftingRuntime.SlotAccess access = access(handler, view.mapping);
        var match = MythicLibCraftingRuntime.match(view.mapping.station(), "", access);
        if (match.isPresent()) {
            ItemStack result = match.get().recipe().createResult();
            if (!same(resultSlot.getStack(), result)) resultSlot.setStack(result.copy());
            OVERRIDDEN.put(handler, result.copy());
            handler.sendContentUpdates();
            return;
        }

        ItemStack previous = OVERRIDDEN.remove(handler);
        if (previous != null && same(resultSlot.getStack(), previous)) {
            resultSlot.setStack(ItemStack.EMPTY);
            resultSlot.markDirty();
            handler.sendContentUpdates();
        }
    }

    private static void tick(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) refresh(player.currentScreenHandler);
    }

    private static void craft(ServerPlayerEntity player, ScreenHandler handler, StationView view, boolean craftToCompletion) {
        int crafted = 0;
        do {
            MythicLibCraftingRuntime.SlotAccess access = access(handler, view.mapping);
            var match = MythicLibCraftingRuntime.match(view.mapping.station(), "", access, permission -> MythicLibPermissionBridge.has(player, permission));
            if (match.isEmpty()) break;
            ItemStack proposed = match.get().recipe().createResult();
            if (proposed.isEmpty()) break;
            MythicLibCraftingEvents.BeforeCraft event = MythicLibCraftingEvents.fireBefore(player, match.get().recipe(), proposed, craftToCompletion);
            if (event.cancelled()) break;
            ItemStack output = event.result();
            if (output.isEmpty()) break;

            if (craftToCompletion) {
                if (!canFullyInsert(player, output)) break;
                ItemStack remaining = output.copy();
                player.getInventory().insertStack(remaining);
                if (!remaining.isEmpty()) throw new IllegalStateException("MythicLib crafting capacity preflight diverged for " + match.get().recipe().id());
            } else {
                ItemStack cursor = handler.getCursorStack();
                if (cursor.isEmpty()) handler.setCursorStack(output.copy());
                else {
                    if (!ItemStack.areItemsAndComponentsEqual(cursor, output)) break;
                    int room = cursor.getMaxCount() - cursor.getCount();
                    if (room < output.getCount()) break;
                    cursor.increment(output.getCount());
                    handler.setCursorStack(cursor);
                }
            }

            match.get().consume();
            crafted++;
            MythicLibCraftingEvents.fireAfter(player, match.get().recipe(), output, crafted);
            refresh(handler);
        } while (craftToCompletion && crafted < 64);
        if (crafted > 0) handler.sendContentUpdates();
    }

    private static MythicLibCraftingRuntime.SlotAccess access(ScreenHandler handler, MythicLibStationMappings.Mapping mapping) {
        return new MythicLibCraftingRuntime.SlotAccess() {
            @Override public ItemStack get(int logicalSlot) {
                int raw = logicalToRaw(mapping, logicalSlot);
                return raw < 0 || raw >= handler.slots.size() ? ItemStack.EMPTY : handler.getSlot(raw).getStack();
            }
            @Override public void set(int logicalSlot, ItemStack stack) {
                int raw = logicalToRaw(mapping, logicalSlot);
                if (raw >= 0 && raw < handler.slots.size()) handler.getSlot(raw).setStack(stack);
            }
            @Override public void markDirty() {
                for (int logical = 0; ; logical++) {
                    int raw = logicalToRaw(mapping, logical);
                    if (raw < 0) break;
                    if (raw < handler.slots.size()) handler.getSlot(raw).markDirty();
                }
                handler.sendContentUpdates();
            }
        };
    }

    private static int logicalToRaw(MythicLibStationMappings.Mapping mapping, int logicalSlot) {
        return switch (mapping.kind()) {
            case PLAYER_CRAFTING, WORKBENCH -> mapping.rawMainSlot(logicalSlot);
            case FURNACE -> logicalSlot == 0 ? 0 : logicalSlot == 1 ? 1 : -1;
            case SMITHING_LEGACY -> logicalSlot == 0 ? 0 : logicalSlot == 1 ? 1 : -1;
            case SMITHING_MODERN -> logicalSlot == 0 ? 1 : logicalSlot == 1 ? 0 : logicalSlot == 2 ? 2 : -1;
        };
    }

    private static StationView view(ScreenHandler handler) {
        if (handler instanceof CraftingScreenHandler) return new StationView(MythicLibStationMappings.workbench());
        if (handler instanceof PlayerScreenHandler) return new StationView(MythicLibStationMappings.playerCrafting());
        if (handler instanceof AbstractFurnaceScreenHandler) return new StationView(MythicLibStationMappings.furnace());
        if (handler instanceof SmithingScreenHandler) return new StationView(MythicLibStationMappings.smithingModern());
        return null;
    }

    private static boolean canFullyInsert(ServerPlayerEntity player, ItemStack output) {
        int remaining = output.getCount();
        for (ItemStack stack : player.getInventory().main) {
            if (stack.isEmpty()) remaining -= Math.min(output.getMaxCount(), player.getInventory().getMaxCountPerStack());
            else if (ItemStack.areItemsAndComponentsEqual(stack, output)) {
                remaining -= Math.max(0, Math.min(stack.getMaxCount(), player.getInventory().getMaxCountPerStack()) - stack.getCount());
            }
            if (remaining <= 0) return true;
        }
        return false;
    }

    private static boolean same(ItemStack left, ItemStack right) {
        if (left.isEmpty() || right.isEmpty()) return left.isEmpty() && right.isEmpty();
        return left.getCount() == right.getCount() && ItemStack.areItemsAndComponentsEqual(left, right);
    }
    private record StationView(MythicLibStationMappings.Mapping mapping) {}
}
