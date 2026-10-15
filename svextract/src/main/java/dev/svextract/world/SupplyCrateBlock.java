package dev.svextract.world;

import net.minecraft.block.*;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;

/** Static baked geometry: no block entity, ticker, container or client-owned loot. */
public final class SupplyCrateBlock extends Block {
    public static final BooleanProperty OPEN=BooleanProperty.of("open");
    private static final VoxelShape SHAPE=Block.createCuboidShape(0,0,0,16,14,16);
    public SupplyCrateBlock(){super(AbstractBlock.Settings.create().strength(3.0f).nonOpaque());setDefaultState(getStateManager().getDefaultState().with(OPEN,false));}
    @Override protected void appendProperties(StateManager.Builder<Block,BlockState> builder){builder.add(OPEN);}
    @Override protected VoxelShape getOutlineShape(BlockState state,BlockView world,BlockPos pos,ShapeContext context){return SHAPE;}
}
