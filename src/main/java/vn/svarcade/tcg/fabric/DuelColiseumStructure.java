package vn.svarcade.tcg.fabric;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/** Bounded, symmetric coliseum. Inlays share one floor; galleries never cross the field. */
public final class DuelColiseumStructure {
    public static final int FLOOR_Y=96, ARENA_SPACING=192;
    public static final double BOARD_SURFACE_Y=FLOOR_Y+1.0;
    private static final int FLAGS=0;
    private DuelColiseumStructure(){}
    public static void build(ServerWorld w,BlockPos c){
        fill(w,c.add(-22,-1,-15),c.add(22,-1,15),Blocks.POLISHED_BLACKSTONE.getDefaultState());
        fill(w,c.add(-22,0,-15),c.add(22,0,15),Blocks.DEEPSLATE_TILES.getDefaultState());
        frame(w,c.add(-22,0,-15),c.add(22,0,15),Blocks.SEA_LANTERN.getDefaultState());
        fill(w,c.add(-21,0,-14),c.add(21,0,14),Blocks.SMOOTH_STONE.getDefaultState());
        // Physical inlays remain readable on all clients; the client mesh supplies fine linework and labels.
        for(int side:new int[]{-1,1})for(int i=0;i<5;i++){
            int x=(i-2)*6;
            fill(w,c.add(x-2,0,side*4-1),c.add(x+2,0,side*4+1),Blocks.LIGHT_GRAY_CONCRETE.getDefaultState());
            fill(w,c.add(x-2,0,side*9-1),c.add(x+2,0,side*9+1),Blocks.GRAY_CONCRETE.getDefaultState());
        }
        for(int x:new int[]{-3,3})fill(w,c.add(x-1,0,-1),c.add(x+1,0,1),Blocks.SMOOTH_QUARTZ.getDefaultState());
        // Narrow access paths and separated podiums preserve the established duelist coordinates.
        for(int side:new int[]{-1,1}){
            fill(w,c.add(-4,0,side*16),c.add(4,0,side*22),Blocks.POLISHED_DEEPSLATE.getDefaultState());
            fill(w,c.add(-3,1,side*19),c.add(3,1,side*21),Blocks.SMOOTH_QUARTZ.getDefaultState());
            fill(w,c.add(-3,2,side*22),c.add(3,2,side*22),Blocks.CYAN_STAINED_GLASS.getDefaultState());
        }
        // Open side galleries: each tier is disjoint, with no overhead rings or arches in the camera sightline.
        for(int side:new int[]{-1,1}){
            fill(w,c.add(side*24,0,-13),c.add(side*26,0,13),Blocks.POLISHED_DEEPSLATE.getDefaultState());
            for(int tier=0;tier<4;tier++){
                int near=27+tier*3,far=near+2,y=3+tier*2;
                fill(w,c.add(side*near,y,-13),c.add(side*far,y,13),Blocks.SMOOTH_QUARTZ.getDefaultState());
                fill(w,c.add(side*far,y+1,-13),c.add(side*far,y+1,13),Blocks.BLUE_CONCRETE.getDefaultState());
            }
            for(int z:new int[]{-17,17}){
                fill(w,c.add(side*26,0,z),c.add(side*26,8,z),Blocks.POLISHED_DEEPSLATE.getDefaultState());
                fill(w,c.add(side*26,9,z),c.add(side*26,9,z),Blocks.SEA_LANTERN.getDefaultState());
            }
        }
    }
    private static void frame(ServerWorld w,BlockPos a,BlockPos b,BlockState state){
        for(int x=a.getX();x<=b.getX();x++){place(w,new BlockPos(x,a.getY(),a.getZ()),state);place(w,new BlockPos(x,a.getY(),b.getZ()),state);}
        for(int z=a.getZ()+1;z<b.getZ();z++){place(w,new BlockPos(a.getX(),a.getY(),z),state);place(w,new BlockPos(b.getX(),a.getY(),z),state);}
    }
    private static void fill(ServerWorld w,BlockPos a,BlockPos b,BlockState state){
        for(int x=Math.min(a.getX(),b.getX());x<=Math.max(a.getX(),b.getX());x++)
            for(int y=Math.min(a.getY(),b.getY());y<=Math.max(a.getY(),b.getY());y++)
                for(int z=Math.min(a.getZ(),b.getZ());z<=Math.max(a.getZ(),b.getZ());z++)place(w,new BlockPos(x,y,z),state);
    }
    private static void place(ServerWorld w,BlockPos p,BlockState state){w.setBlockState(p,state,FLAGS);}
}
