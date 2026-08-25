package vn.svframe.mythiclibfabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.LinkedHashMap;
import java.util.Map;

/** Native Fabric armor-change bridge for MythicLib passive triggers. */
public final class MythicLibArmorPassiveMod implements ModInitializer {
    @Override
    public void onInitialize() {
        ServerEntityEvents.EQUIPMENT_CHANGE.register((entity, slot, previousStack, currentStack) -> {
            if (!(entity instanceof ServerPlayerEntity player) || !isArmor(slot)) return;
            if (ItemStack.areEqual(previousStack, currentStack)) return;

            int armorSlot = armorIndex(slot);
            if (!previousStack.isEmpty()) {
                PassiveSkillRuntime.fire(player.getUuid(), LegacyTriggerType.UNEQUIP_ARMOR, player.getUuid(),
                        context("UNEQUIP_ARMOR", armorSlot, slot, previousStack));
            }
            if (!currentStack.isEmpty()) {
                PassiveSkillRuntime.fire(player.getUuid(), LegacyTriggerType.EQUIP_ARMOR, player.getUuid(),
                        context("EQUIP_ARMOR", armorSlot, slot, currentStack));
            }
        });
    }

    private static boolean isArmor(EquipmentSlot slot) {
        return slot == EquipmentSlot.FEET || slot == EquipmentSlot.LEGS
                || slot == EquipmentSlot.CHEST || slot == EquipmentSlot.HEAD;
    }

    private static int armorIndex(EquipmentSlot slot) {
        return switch (slot) {
            case FEET -> 0;
            case LEGS -> 1;
            case CHEST -> 2;
            case HEAD -> 3;
            default -> -1;
        };
    }

    private static Map<String, Object> context(String trigger, int index, EquipmentSlot slot, ItemStack stack) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("trigger", trigger);
        out.put("armor-slot", index);
        out.put("equipment-slot", slot.getName());
        out.put("item", Registries.ITEM.getId(stack.getItem()).toString());
        out.put("item-count", stack.getCount());
        return out;
    }
}
