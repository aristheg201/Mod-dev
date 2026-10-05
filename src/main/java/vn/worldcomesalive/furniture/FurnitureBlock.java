package vn.worldcomesalive.furniture;

import net.minecraft.block.*;
import java.util.Set;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.state.*;
import net.minecraft.state.property.Properties;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.*;
import net.minecraft.world.*;
import com.mojang.serialization.MapCodec;

/** Real craftable furniture with shaped collisions and server-authoritative chair occupancy. */
public final class FurnitureBlock extends HorizontalFacingBlock {
    private final MapCodec<FurnitureBlock> codec;
    public enum Wood implements net.minecraft.util.StringIdentifiable {OAK,SPRUCE,DARK_OAK,ACACIA,MANGROVE;public String asString(){return name().toLowerCase(java.util.Locale.ROOT);}}
    public static final net.minecraft.state.property.EnumProperty<Wood> WOOD=net.minecraft.state.property.EnumProperty.of("wood",Wood.class);
    public final String kind;
    public FurnitureBlock(String kind,Settings settings){super(settings);this.kind=kind;codec=createCodec(s->new FurnitureBlock(kind,s));setDefaultState(stateManager.getDefaultState().with(FACING,Direction.NORTH).with(WOOD,Wood.OAK));}
    @Override protected MapCodec<? extends HorizontalFacingBlock> getCodec(){return codec;}
    @Override protected void appendProperties(StateManager.Builder<Block,BlockState> builder){builder.add(FACING,WOOD);}
    @Override public BlockState getPlacementState(net.minecraft.item.ItemPlacementContext context){return getDefaultState().with(FACING,context.getHorizontalPlayerFacing().getOpposite());}
    @Override protected VoxelShape getOutlineShape(BlockState state,BlockView world,BlockPos pos,ShapeContext context){if(state.isIn(Semantics.WALL_DECORATION))return switch(state.get(FACING)){case EAST->Block.createCuboidShape(0,0,0,2,16,16);case WEST->Block.createCuboidShape(14,0,0,16,16,16);case SOUTH->Block.createCuboidShape(0,0,0,16,16,2);default->Block.createCuboidShape(0,0,14,16,16,16);};if(state.isIn(Semantics.CLUTTER))return Block.createCuboidShape(2,0,2,14,8,14);if(state.isIn(Semantics.SITTABLE)&&!kind.equals("medieval_chair"))return Block.createCuboidShape(1,0,1,15,kind.contains("stool")||kind.contains("bench")?9:24,15);if(Set.of("bottle_shelf","mug_rack","ingredient_shelf","wine_rack","wall_shelf","shelf").contains(kind))return Block.createCuboidShape(0,0,2,16,10,15);if(Set.of("chopping_board","candle_holder","pan").contains(kind))return Block.createCuboidShape(2,0,2,14,kind.equals("candle_holder")?11:2,14);if(kind.equals("rug"))return Block.createCuboidShape(0,0,0,16,.3,16);return switch(kind){case "medieval_chair"->VoxelShapes.union(Block.createCuboidShape(2,0,2,14,9,14),switch(state.get(FACING)){case SOUTH->Block.createCuboidShape(2,9,1,14,21,3);case EAST->Block.createCuboidShape(1,9,2,3,21,14);case WEST->Block.createCuboidShape(13,9,2,15,21,14);default->Block.createCuboidShape(2,9,13,14,21,15);});case "wooden_stool","wooden_bench"->Block.createCuboidShape(2,0,2,14,9,14);case "cooking_pot","book_stack","flower_vase"->Block.createCuboidShape(3,0,3,13,8,13);case "medieval_table","writing_desk"->Block.createCuboidShape(0,0,0,16,14,16);case "medieval_bookshelf"->Block.createCuboidShape(1,0,1,15,16,15);default->VoxelShapes.fullCube();};}
    @Override protected boolean canPathfindThrough(BlockState state,net.minecraft.entity.ai.pathing.NavigationType type){return Set.of("rug","firewood","flour_sack","produce_basket").contains(kind);}
    @Override public void onPlaced(World world,BlockPos pos,BlockState state,net.minecraft.entity.LivingEntity placer,net.minecraft.item.ItemStack stack){super.onPlaced(world,pos,state,placer,stack);if(!world.isClient&&state.isIn(Semantics.FOOD_STORAGE)&&placer instanceof net.minecraft.server.network.ServerPlayerEntity p&&vn.worldcomesalive.server.WorldSimulation.active()!=null)vn.worldcomesalive.server.WorldSimulation.active().domestic.claimContainer(p,pos);}
    @Override protected ActionResult onUse(BlockState state,World world,BlockPos pos,PlayerEntity player,BlockHitResult hit){if(world.isClient)return ActionResult.SUCCESS;
        if(state.isIn(Semantics.SITTABLE))return FurnitureRegistry.sit(player,(net.minecraft.server.world.ServerWorld)world,pos)?ActionResult.CONSUME:ActionResult.FAIL;
        var sim=vn.worldcomesalive.server.WorldSimulation.active();if(sim==null||!(player instanceof net.minecraft.server.network.ServerPlayerEntity p))return ActionResult.PASS;
        var position=new vn.worldcomesalive.model.LivingWorld.Pos(pos.getX(),pos.getY(),pos.getZ());var building=sim.state.settlements.values().stream().flatMap(v->v.buildings.values().stream()).filter(b->b.contains(position)).findFirst().orElse(null);
        if(state.isIn(Semantics.FOOD_STORAGE)||state.isIn(Semantics.DRINK_STORAGE)){if(!sim.lodging.openRoomStorage(p,pos))sim.domestic.openStorage(p,building,pos);return ActionResult.CONSUME;}
        if(kind.equals("notice_board")){vn.worldcomesalive.WorldComesAlive.interactions.openBoard(p,pos);return ActionResult.CONSUME;}
        if(state.isIn(Semantics.READING)){var life=sim.state.players.computeIfAbsent(p.getUuid(),k->new vn.worldcomesalive.model.LivingWorld.PlayerLife());if(sim.state.clock-life.lastStudy>=1200){life.skills.merge("speech",.005,Double::sum);life.lastStudy=sim.state.clock;}p.sendMessage(net.minecraft.text.Text.literal("You spend a moment reading. Speech practice rewards thoughtful study."),true);return ActionResult.CONSUME;}
        if(state.isIn(Semantics.COOKING)&&building!=null){sim.domestic.work(sim.state.settlements.values().stream().filter(v->v.buildings.containsKey(building.id)).findFirst().orElseThrow(),building,"cook",.2);p.sendMessage(net.minecraft.text.Text.literal("The kitchen reserves available ingredients for cooking. Finished meals enter its stock."),true);return ActionResult.CONSUME;}
        if(kind.equals("medieval_counter")&&building!=null){var host=sim.visible().stream().filter(e->sim.npc(e.getUuid()).workplace.equals(building.id)).findFirst().orElse(null);if(host!=null&&p.squaredDistanceTo(host)<=49){vn.worldcomesalive.WorldComesAlive.interactions.open(p,host);return ActionResult.CONSUME;}}
        return ActionResult.PASS;}
}
