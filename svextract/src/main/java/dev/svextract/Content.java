package dev.svextract;

import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public final class Content {
    public static final net.minecraft.entity.EntityType<dev.svextract.mission.CombatBotEntity> COMBAT_BOT=Registry.register(Registries.ENTITY_TYPE,Identifier.of("svextract","combat_bot"),
        net.minecraft.entity.EntityType.Builder.create(dev.svextract.mission.CombatBotEntity::new,net.minecraft.entity.SpawnGroup.MONSTER).dimensions(0.6f,1.95f).maxTrackingRange(8).build("svextract:combat_bot"));
    private static dev.svextract.world.SupplyCrateBlock crate(String name){
        var block=Registry.register(Registries.BLOCK,Identifier.of("svextract",name),new dev.svextract.world.SupplyCrateBlock());
        Registry.register(Registries.ITEM,Identifier.of("svextract",name),new net.minecraft.item.BlockItem(block,new Item.Settings()));return block;
    }
    public static final dev.svextract.world.SupplyCrateBlock SUPPLY_CRATE=crate("supply_crate"),BATTLEGROUND_CRATE=crate("battleground_crate"),STASH_TERMINAL=crate("stash_terminal");
    public static final Item DATA_CORE=Registry.register(Registries.ITEM,Identifier.of("svextract","data_core"),new Item(new Item.Settings().maxCount(1)));
    public static final SoundEvent EVACUATION=Registry.register(Registries.SOUND_EVENT,Identifier.of("svextract","evacuation"),SoundEvent.of(Identifier.of("svextract","evacuation")));
    public static void initialize(){net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry.register(COMBAT_BOT,net.minecraft.entity.mob.PillagerEntity.createPillagerAttributes());}
}
