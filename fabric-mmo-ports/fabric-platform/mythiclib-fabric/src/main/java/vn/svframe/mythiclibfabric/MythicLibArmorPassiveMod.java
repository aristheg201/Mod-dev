package vn.svframe.mythiclibfabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Native Fabric armor-change bridge for MythicLib passive triggers. */
public final class MythicLibArmorPassiveMod implements ModInitializer {
    private static final Map<UUID, List<ArmorState>> LAST_ARMOR = new ConcurrentHashMap<>();

    @Override
    public void onInitialize() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                LAST_ARMOR.put(handler.getPlayer().getUuid(), snapshot(handler.getPlayer())));

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                LAST_ARMOR.remove(handler.getPlayer().getUuid()));

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> LAST_ARMOR.clear());

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                UUID owner = player.getUuid();
                List<ArmorState> current = snapshot(player);
                List<ArmorState> previous = LAST_ARMOR.put(owner, current);
                if (previous == null) continue;

                int count = Math.min(previous.size(), current.size());
                for (int slot = 0; slot < count; slot++) {
                    ArmorState before = previous.get(slot);
                    ArmorState after = current.get(slot);
                    if (before.equals(after)) continue;

                    if (!before.empty()) {
                        PassiveSkillRuntime.fire(owner, LegacyTriggerType.UNEQUIP_ARMOR, owner,
                                context("UNEQUIP_ARMOR", slot, before));
                    }
                    if (!after.empty()) {
                        PassiveSkillRuntime.fire(owner, LegacyTriggerType.EQUIP_ARMOR, owner,
                                context("EQUIP_ARMOR", slot, after));
                    }
                }
            }
        });
    }

    private static List<ArmorState> snapshot(ServerPlayerEntity player) {
        List<ArmorState> out = new ArrayList<>(4);
        int slot = 0;
        for (ItemStack stack : player.getInventory().armor) {
            out.add(ArmorState.of(slot++, stack));
        }
        return List.copyOf(out);
    }

    private static Map<String, Object> context(String trigger, int slot, ArmorState state) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("trigger", trigger);
        out.put("armor-slot", slot);
        out.put("item", state.itemId());
        out.put("item-count", state.count());
        return out;
    }

    private record ArmorState(int slot, String itemId, int count) {
        static ArmorState of(int slot, ItemStack stack) {
            if (stack == null || stack.isEmpty()) return new ArmorState(slot, "", 0);
            return new ArmorState(slot, Registries.ITEM.getId(stack.getItem()).toString(), stack.getCount());
        }

        boolean empty() {
            return itemId.isEmpty();
        }
    }
}
