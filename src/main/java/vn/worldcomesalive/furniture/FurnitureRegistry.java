package vn.worldcomesalive.furniture;

import net.minecraft.block.*;
import net.minecraft.entity.*;
import net.minecraft.item.*;
import net.minecraft.registry.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import java.util.*;

public final class FurnitureRegistry {
    public static final Map<String,FurnitureBlock> BLOCKS=new LinkedHashMap<>();
    public static final FurnitureBlock CHAIR=register("medieval_chair"),TABLE=register("medieval_table"),CABINET=register("medieval_cabinet"),BOOKSHELF=register("medieval_bookshelf"),COUNTER=register("medieval_counter");
    public static final TableSettingBlock SETTING=Registry.register(Registries.BLOCK,Identifier.of("worldcomesalive","table_setting"),new TableSettingBlock(AbstractBlock.Settings.create().nonOpaque().noCollision().strength(.2f)));
    public static final EntityType<SeatEntity> SEAT=Registry.register(Registries.ENTITY_TYPE,Identifier.of("worldcomesalive","seat"),EntityType.Builder.create(SeatEntity::new,SpawnGroup.MISC).dimensions(.1f,.1f).disableSaving().disableSummon().maxTrackingRange(8).build("worldcomesalive:seat"));
    public static final FurnitureBlock STOOL=register("wooden_stool"),BENCH=register("wooden_bench"),DESK=register("writing_desk"),POT=register("cooking_pot"),BOTTLES=register("bottle_shelf"),MUGS=register("mug_rack"),BOOKS=register("book_stack"),VASE=register("flower_vase");
    static {register("dining_chair");register("tavern_stool");register("armchair");register("throne");register("outdoor_bench");register("small_table");register("dining_table");register("long_tavern_table");register("work_table");register("bedside_table");register("cabinet");register("cupboard");register("wardrobe");register("dresser");register("shelf");register("food_pantry");register("wine_rack");register("barrel_rack");register("crate");register("storage_chest");register("bar_counter");register("serving_counter");register("preparation_counter");register("keg");register("chopping_board");register("pan");register("oven_hearth");register("drying_rack");register("ingredient_shelf");register("rug");register("candle_holder");register("wall_shelf");register("coat_rack");register("tapestry");}
    static {register("idle_tableware");register("folded_cloth");register("personal_belongings");register("hanging_herbs");register("produce_basket");register("water_pitcher");register("plate_stack");register("firewood");register("wall_sconce");register("paper_quill");register("flour_sack");register("cookware_rack");register("serving_tray_prop");register("mug_cluster");register("bread_basket");register("card_set");register("tool_samples");register("tool_rack");register("ore_crate");}
    static {register("hay_storage");register("notice_board");}
    private static FurnitureBlock register(String name){Identifier id=Identifier.of("worldcomesalive",name);var block=new FurnitureBlock(name,AbstractBlock.Settings.copy(Blocks.OAK_PLANKS).nonOpaque().luminance(state->name.equals("candle_holder")||name.equals("wall_sconce")?10:name.equals("oven_hearth")?12:0));Registry.register(Registries.BLOCK,id,block);Registry.register(Registries.ITEM,id,new BlockItem(block,new Item.Settings()));BLOCKS.put(name,block);return block;}
    public static void initialize(){net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents.modifyEntriesEvent(ItemGroups.BUILDING_BLOCKS).register(entries->BLOCKS.values().forEach(entries::add));}
    public static boolean sit(Entity passenger,ServerWorld world,BlockPos pos){
        if(!world.getBlockState(pos).isIn(Semantics.SITTABLE)||passenger.squaredDistanceTo(pos.toCenterPos())>9)return false;
        var existing=world.getEntitiesByType(SEAT,new Box(pos).expand(.2),e->!e.isRemoved());
        if(existing.stream().anyMatch(e->!e.getPassengerList().isEmpty()))return false;
        SeatEntity seat=existing.isEmpty()?SEAT.create(world):existing.getFirst();if(seat==null)return false;
        seat.chair=pos.toImmutable();seat.refreshPositionAndAngles(pos.getX()+.5,pos.getY()+.45,pos.getZ()+.5,(world.getBlockState(pos).contains(HorizontalFacingBlock.FACING)?world.getBlockState(pos).get(HorizontalFacingBlock.FACING):Direction.NORTH).asRotation(),0);
        if(existing.isEmpty())world.spawnEntity(seat);
        if(!passenger.startRiding(seat,true)){seat.discard();return false;}passenger.setYaw(seat.getYaw());return true;
    }
    private FurnitureRegistry(){}
}
