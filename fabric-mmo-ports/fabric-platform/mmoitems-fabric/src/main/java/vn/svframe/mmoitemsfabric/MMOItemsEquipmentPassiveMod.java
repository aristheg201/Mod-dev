package vn.svframe.mmoitemsfabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.mmoitemsfabric.runtime.inventory.EquipmentDiffRuntime;
import vn.svframe.mythiclibfabric.MythicLibPassiveMod;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Connects MMOItems' equipment-diff runtime to the real Fabric player inventory.
 * Replacement ordering is UNEQUIP(old) before EQUIP(new), matching the source runtime.
 */
public final class MMOItemsEquipmentPassiveMod implements ModInitializer {
    private static final String NBT_TYPE = "mmoitems_type";
    private static final String NBT_ID = "mmoitems_id";
    private static final Map<UUID, EquipmentDiffRuntime<Integer>> EQUIPMENT = new ConcurrentHashMap<>();

    @Override
    public void onInitialize() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            EquipmentDiffRuntime<Integer> runtime = new EquipmentDiffRuntime<>();
            runtime.refresh(observe(handler.player));
            EQUIPMENT.put(handler.player.getUuid(), runtime);
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                EQUIPMENT.remove(handler.player.getUuid()));

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> EQUIPMENT.clear());

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                EquipmentDiffRuntime<Integer> runtime = EQUIPMENT.computeIfAbsent(
                        player.getUuid(), ignored -> new EquipmentDiffRuntime<>());

                for (EquipmentDiffRuntime.Transition<Integer> transition : runtime.refresh(observe(player))) {
                    String trigger = transition.kind() == EquipmentDiffRuntime.Kind.UNEQUIP
                            ? "UNEQUIP_ARMOR"
                            : "EQUIP_ARMOR";
                    Map<String, Object> context = new LinkedHashMap<>();
                    context.put("trigger", trigger);
                    context.put("armor-slot", transition.slot());
                    context.put("item", transition.item().identity());
                    MythicLibPassiveMod.fire(player.getUuid(), trigger, player.getUuid(), context);
                }
            }
        });
    }

    private static Map<Integer, EquipmentDiffRuntime.EquippedSnapshot> observe(ServerPlayerEntity player) {
        Map<Integer, EquipmentDiffRuntime.EquippedSnapshot> observed = new LinkedHashMap<>();
        int slot = 0;
        for (ItemStack stack : player.getInventory().armor) {
            String identity = identity(stack);
            if (!identity.isEmpty()) {
                observed.put(slot, new EquipmentDiffRuntime.EquippedSnapshot(itemHash(stack), identity));
            }
            slot++;
        }
        return observed;
    }

    private static String identity(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        NbtComponent component = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (component == null) return "";
        NbtCompound nbt = component.copyNbt();
        String type = nbt.getString(NBT_TYPE);
        String id = nbt.getString(NBT_ID);
        if (type.isEmpty() || id.isEmpty()) return "";
        return type + ':' + id;
    }

    private static int itemHash(ItemStack stack) {
        int result = Registries.ITEM.getId(stack.getItem()).hashCode();
        result = 31 * result + stack.getComponents().hashCode();
        return result;
    }
}
