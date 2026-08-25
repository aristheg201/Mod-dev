package vn.svframe.mythiclibfabric;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import vn.svframe.mythiclibfabric.runtime.MythicLibWorkbenchLayout;
import vn.svframe.mythiclibfabric.runtime.MythicLibCraftingRuntime;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Native server-side MythicLib 1.7.1 Super/Mega Workbench implementation. */
public final class MythicLibWorkbenchMod {
    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();
    private static volatile boolean initialized;
    private MythicLibWorkbenchMod() {}

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        ServerTickEvents.END_SERVER_TICK.register(MythicLibWorkbenchMod::tick);
    }
    public static boolean openSuper(ServerPlayerEntity player) { return open(player, MythicLibWorkbenchLayout.superWorkbench(), "Super Workbench"); }
    public static boolean openMega(ServerPlayerEntity player) { return open(player, MythicLibWorkbenchLayout.megaWorkbench(), "Mega Workbench"); }
    public static boolean isOpen(ServerPlayerEntity player) {
        Session session = SESSIONS.get(player.getUuid());
        return session != null && session.handler != null && player.currentScreenHandler == session.handler;
    }

    public static boolean handleClick(ServerPlayerEntity player, ScreenHandler handler, int slotIndex, SlotActionType actionType) {
        Session session = SESSIONS.get(player.getUuid());
        if (session == null || session.handler != handler) return false;
        if (slotIndex < 0 || slotIndex >= handler.slots.size()) return false;
        int topSize = session.layout.size();
        if (slotIndex < topSize && session.layout.isEdgeSlot(slotIndex)) return true;
        if (slotIndex < topSize && session.layout.isResultSlot(slotIndex)) {
            if (actionType == SlotActionType.PICKUP || actionType == SlotActionType.QUICK_MOVE) craftResult(player, session, actionType == SlotActionType.QUICK_MOVE);
            return true;
        }
        if (actionType != SlotActionType.QUICK_MOVE) return false;
        Slot source = handler.getSlot(slotIndex);
        if (!source.hasStack()) return true;
        if (slotIndex >= topSize) shiftIntoWorkbench(session, source);
        else if (session.layout.isInputSlot(slotIndex)) shiftIntoPlayer(player, source);
        refreshResult(session);
        handler.sendContentUpdates();
        return true;
    }

    private static boolean open(ServerPlayerEntity player, MythicLibWorkbenchLayout layout, String title) {
        Session old = SESSIONS.remove(player.getUuid());
        if (old != null) returnInputs(player, old);
        Session session = new Session(layout, new SimpleInventory(layout.size()));
        fillEdges(session);
        refreshResult(session);
        SESSIONS.put(player.getUuid(), session);
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory((syncId, playerInventory, ignored) -> {
            GenericContainerScreenHandler handler = layout.size() == 45
                    ? new GenericContainerScreenHandler(ScreenHandlerType.GENERIC_9X5, syncId, playerInventory, session.inventory, 5)
                    : new GenericContainerScreenHandler(ScreenHandlerType.GENERIC_9X6, syncId, playerInventory, session.inventory, 6);
            session.handler = handler;
            return handler;
        }, Text.literal(title)));
        return true;
    }
    private static void fillEdges(Session session) { for (int slot : session.layout.edgeSlots()) session.inventory.setStack(slot, edgeItem()); }
    private static ItemStack edgeItem() {
        ItemStack stack = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        stack.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal(" "));
        return stack;
    }

    private static void craftResult(ServerPlayerEntity player, Session session, boolean craftToCompletion) {
        int crafted = 0;
        do {
            MythicLibCraftingRuntime.SlotAccess access = slotAccess(session);
            var match = MythicLibCraftingRuntime.match(MythicLibCraftingRuntime.Station.CUSTOM, stationKey(session.layout), access, permission -> MythicLibPermissionBridge.has(player, permission));
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
                if (!remaining.isEmpty()) throw new IllegalStateException("MythicLib workbench capacity preflight diverged for " + match.get().recipe().id());
            } else {
                ItemStack cursor = session.handler.getCursorStack();
                if (cursor.isEmpty()) session.handler.setCursorStack(output.copy());
                else {
                    if (!ItemStack.areItemsAndComponentsEqual(cursor, output)) break;
                    int room = cursor.getMaxCount() - cursor.getCount();
                    if (room < output.getCount()) break;
                    cursor.increment(output.getCount());
                    session.handler.setCursorStack(cursor);
                }
            }
            match.get().consume();
            crafted++;
            MythicLibCraftingEvents.fireAfter(player, match.get().recipe(), output, crafted);
            refreshResult(session);
        } while (craftToCompletion && crafted < 64);
        if (crafted > 0 && session.handler != null) session.handler.sendContentUpdates();
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

    private static void shiftIntoWorkbench(Session session, Slot source) {
        ItemStack sourceStack = source.getStack();
        if (sourceStack.isEmpty()) return;
        for (int raw : session.layout.inputSlots()) {
            ItemStack target = session.inventory.getStack(raw);
            if (target.isEmpty() || !ItemStack.areItemsAndComponentsEqual(target, sourceStack)) continue;
            int room = Math.min(target.getMaxCount(), session.inventory.getMaxCountPerStack()) - target.getCount();
            if (room <= 0) continue;
            int moved = Math.min(room, sourceStack.getCount());
            target.increment(moved); sourceStack.decrement(moved); session.inventory.markDirty();
            if (sourceStack.isEmpty()) break;
        }
        if (!sourceStack.isEmpty()) for (int raw : session.layout.inputSlots()) {
            if (!session.inventory.getStack(raw).isEmpty()) continue;
            int moved = Math.min(sourceStack.getCount(), Math.min(sourceStack.getMaxCount(), session.inventory.getMaxCountPerStack()));
            session.inventory.setStack(raw, sourceStack.copyWithCount(moved));
            sourceStack.decrement(moved);
            if (sourceStack.isEmpty()) break;
        }
        source.setStack(sourceStack.isEmpty() ? ItemStack.EMPTY : sourceStack); source.markDirty();
    }
    private static void shiftIntoPlayer(ServerPlayerEntity player, Slot source) {
        ItemStack remaining = source.getStack().copy();
        player.getInventory().insertStack(remaining);
        source.setStack(remaining.isEmpty() ? ItemStack.EMPTY : remaining); source.markDirty();
    }
    private static void tick(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            Session session = SESSIONS.get(player.getUuid());
            if (session == null || session.handler == null) continue;
            if (player.currentScreenHandler == session.handler) { refreshResult(session); continue; }
            if (SESSIONS.remove(player.getUuid(), session)) returnInputs(player, session);
        }
    }
    private static MythicLibCraftingRuntime.SlotAccess slotAccess(Session session) {
        return new MythicLibCraftingRuntime.SlotAccess() {
            @Override public ItemStack get(int logicalSlot) { int raw = logicalToRaw(session.layout, logicalSlot); return raw < 0 ? ItemStack.EMPTY : session.inventory.getStack(raw); }
            @Override public void set(int logicalSlot, ItemStack stack) { int raw = logicalToRaw(session.layout, logicalSlot); if (raw >= 0) session.inventory.setStack(raw, stack); }
            @Override public void markDirty() { session.inventory.markDirty(); }
        };
    }
    private static void refreshResult(Session session) {
        session.inventory.setStack(session.layout.resultSlot(), MythicLibCraftingRuntime.result(MythicLibCraftingRuntime.Station.CUSTOM, stationKey(session.layout), slotAccess(session)));
    }
    private static int logicalToRaw(MythicLibWorkbenchLayout layout, int logicalSlot) {
        int[] input = layout.inputSlots(); return logicalSlot < 0 || logicalSlot >= input.length ? -1 : input[logicalSlot];
    }
    private static String stationKey(MythicLibWorkbenchLayout layout) { return layout.kind() == MythicLibWorkbenchLayout.Kind.SUPER ? "swb" : "mwb"; }
    private static void returnInputs(ServerPlayerEntity player, Session session) {
        for (int raw : session.layout.inputSlots()) {
            ItemStack stack = session.inventory.removeStack(raw);
            if (!stack.isEmpty()) player.getInventory().offerOrDrop(stack);
        }
        session.inventory.setStack(session.layout.resultSlot(), ItemStack.EMPTY);
    }
    private static final class Session {
        final MythicLibWorkbenchLayout layout;
        final SimpleInventory inventory;
        volatile ScreenHandler handler;
        private Session(MythicLibWorkbenchLayout layout, SimpleInventory inventory) { this.layout = layout; this.inventory = inventory; }
    }
}
