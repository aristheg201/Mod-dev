package vn.svframe.mmoitemsfabric.runtime;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import vn.svframe.mmoitemsfabric.runtime.crafting.CraftingQueueRuntime;
import vn.svframe.mmoitemsfabric.runtime.inventory.EquipmentDiffRuntime;
import vn.svframe.mmoitemsfabric.runtime.inventory.ExactModifierLifetime;

public final class M1_5Smoke {
    public static void main(String[] args) {
        verifyEquipmentOrder();
        verifyModifierLifetime();
        verifyCraftingQueue();
        System.out.println("MMOITEMS_INVENTORY_CRAFTING_MODIFIER_RUNTIME=PASS");
    }

    private static void verifyEquipmentOrder() {
        EquipmentDiffRuntime<String> runtime = new EquipmentDiffRuntime<>();
        var oldItem = new EquipmentDiffRuntime.EquippedSnapshot(10, "old");
        var newItem = new EquipmentDiffRuntime.EquippedSnapshot(20, "new");
        runtime.refresh(Map.of("MAIN_HAND", oldItem));
        var events = runtime.refresh(Map.of("MAIN_HAND", newItem));
        require(events.size() == 2, "replacement must emit two transitions");
        require(events.get(0).kind() == EquipmentDiffRuntime.Kind.UNEQUIP, "old must unequip first");
        require(events.get(1).kind() == EquipmentDiffRuntime.Kind.EQUIP, "new must equip second");
        require(runtime.refresh(Map.of("MAIN_HAND", newItem)).isEmpty(), "unchanged hash must be stable");
    }

    private static void verifyModifierLifetime() {
        ExactModifierLifetime lifetime = new ExactModifierLifetime();
        UUID itemA = UUID.randomUUID();
        UUID itemB = UUID.randomUUID();
        var a = new ExactModifierLifetime.Modifier(UUID.randomUUID(), "attack_damage", 5);
        var b = new ExactModifierLifetime.Modifier(UUID.randomUUID(), "attack_damage", 7);
        lifetime.register(itemA, List.of(a));
        lifetime.register(itemB, List.of(b));
        require(lifetime.unregister(itemA).equals(List.of(a)), "must unregister exact item cache");
        require(lifetime.snapshot().equals(List.of(b)), "same stat key on another item must survive");
    }

    private static void verifyCraftingQueue() {
        long now = 1_000_000L;
        CraftingQueueRuntime queue = new CraftingQueueRuntime();
        var first = queue.add("first", 1000, now);
        var second = queue.add("second", 2000, now + 100);
        require(first.start() == now, "start is enqueue time");
        require(first.completion() == now + 1000, "first completion");
        require(second.start() == now + 100, "second start is still enqueue time");
        require(second.completion() == now + 3000, "second completion chains after first");
        queue.remove(first, now + 400);
        require(second.completion() == now + 2400, "removal compacts by min(left, craftingTime)");

        List<Map<String, Object>> records = queue.toRecords();
        UUID runtimeId = second.uniqueId();
        CraftingQueueRuntime restored = new CraftingQueueRuntime();
        Map<String, Long> times = new LinkedHashMap<>();
        times.put("second", 2000L);
        restored.loadRecords(records, id -> times.get(id));
        var loaded = restored.crafts().get(0);
        require(loaded.completion() == second.completion(), "completion must persist as absolute epoch");
        require(!loaded.uniqueId().equals(runtimeId), "runtime UUID must not persist");
        require(loaded.elapsed(now + 500) == 2000L, "original elapsed uses max(craftingTime, now-start)");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
