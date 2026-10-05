package vn.worldcomesalive.furniture;
import net.minecraft.block.*;
import net.minecraft.state.*;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.*;
import net.minecraft.world.*;
import com.mojang.serialization.MapCodec;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
/** One non-ticking block per occupied table, not per fork, mug or dish. */
public final class TableSettingBlock extends Block {
    public enum Course implements StringIdentifiable {MEAL,DRINK,BOTH,DIRTY;public String asString(){return name().toLowerCase(java.util.Locale.ROOT);}}
    public static final EnumProperty<Course> COURSE=EnumProperty.of("course",Course.class);
    public static final MapCodec<TableSettingBlock> CODEC=createCodec(TableSettingBlock::new);
    public TableSettingBlock(Settings settings){super(settings);setDefaultState(stateManager.getDefaultState().with(COURSE,Course.MEAL));}
    @Override protected MapCodec<? extends Block> getCodec(){return CODEC;}
    @Override protected void appendProperties(StateManager.Builder<Block,BlockState> builder){builder.add(COURSE);}
    @Override protected VoxelShape getOutlineShape(BlockState s,BlockView w,BlockPos p,ShapeContext c){return Block.createCuboidShape(2,0,2,14,6,14);}
    @Override protected VoxelShape getCollisionShape(BlockState s,BlockView w,BlockPos p,ShapeContext c){return VoxelShapes.empty();}
    @Override protected ActionResult onUse(BlockState state,World world,BlockPos pos,PlayerEntity player,BlockHitResult hit){if(world.isClient)return ActionResult.SUCCESS;var sim=vn.worldcomesalive.server.WorldSimulation.active();if(sim==null||!(player instanceof net.minecraft.server.network.ServerPlayerEntity p))return ActionResult.PASS;return sim.domestic.consume(p,pos)?ActionResult.CONSUME:ActionResult.FAIL;}
}
