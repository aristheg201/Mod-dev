package io.github.aristheg201.cobblemonworld.item;

import io.github.aristheg201.cobblemonworld.CobblemonWorldMod;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;


public final class ModItems {
    public static final Item TRAINER_PHONE = net.minecraft.core.Registry.register(
            BuiltInRegistries.ITEM,
            ResourceLocation.fromNamespaceAndPath(CobblemonWorldMod.MOD_ID, "trainer_phone"),
            new TrainerPhoneItem(new Item.Properties().stacksTo(1))
    );

    private ModItems() {}

    public static void register() {
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
                .register(entries -> entries.accept(TRAINER_PHONE));
    }
}
