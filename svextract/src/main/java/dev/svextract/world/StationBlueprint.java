package dev.svextract.world;

import net.minecraft.block.*;
import net.minecraft.util.math.BlockPos;
import java.util.*;

/** Original multi-level railway architecture. Pure geometry; never reads world state. */
public final class StationBlueprint {
    public record Change(BlockPos position,BlockState state) {}
    private final LinkedHashMap<BlockPos,BlockState> blocks=new LinkedHashMap<>();
    private void box(int x1,int y1,int z1,int x2,int y2,int z2,Block block) {
        for(int x=x1;x<=x2;x++)for(int y=y1;y<=y2;y++)for(int z=z1;z<=z2;z++)blocks.put(new BlockPos(x,y,z),block.getDefaultState());
    }
    private void room(int x1,int z1,int x2,int z2,Block wall) {
        box(x1,65,z1,x2,69,z1,wall);box(x1,65,z2,x2,69,z2,wall);
        box(x1,65,z1,x1,69,z2,wall);box(x2,65,z1,x2,69,z2,wall);
        box(x1,70,z1,x2,70,z2,Blocks.DEEPSLATE_TILES);
    }
    public static List<Change> redline() {
        StationBlueprint b=new StationBlueprint();
        b.box(-36,62,-28,36,63,28,Blocks.DEEPSLATE_BRICKS);
        b.box(-35,64,-27,35,64,27,Blocks.POLISHED_ANDESITE);
        b.box(-36,64,-28,36,67,-28,Blocks.CRACKED_DEEPSLATE_BRICKS);
        b.box(-36,64,28,36,67,28,Blocks.CRACKED_DEEPSLATE_BRICKS);
        b.box(-36,64,-28,-36,68,28,Blocks.DEEPSLATE_BRICKS);
        b.box(36,64,-28,36,68,28,Blocks.DEEPSLATE_BRICKS);
        for(int z:new int[]{-12,12}) {
            b.box(-34,64,z-2,34,64,z+2,Blocks.GRAVEL);
            for(int x=-34;x<=34;x++) {
                b.blocks.put(new BlockPos(x,65,z),Blocks.RAIL.getDefaultState().with(RailBlock.SHAPE,net.minecraft.block.enums.RailShape.EAST_WEST));
                if(x%4==0)b.box(x,64,z-2,x,64,z+2,Blocks.DARK_OAK_PLANKS);
            }
            b.box(-34,64,z-4,34,64,z-3,Blocks.YELLOW_CONCRETE);
            b.box(-34,64,z+3,34,64,z+4,Blocks.YELLOW_CONCRETE);
        }
        for(int x=-32;x<=32;x+=8) {
            b.box(x,65,-24,x,74,-24,Blocks.IRON_BLOCK);
            b.box(x,65,24,x,74,24,Blocks.IRON_BLOCK);
            b.box(x,74,-24,x,74,24,Blocks.DEEPSLATE_TILES);
            b.box(x,73,-2,x,73,2,Blocks.SEA_LANTERN);
        }
        b.box(-32,75,-24,32,75,-20,Blocks.DEEPSLATE_TILES);
        b.box(-32,75,20,32,75,24,Blocks.DEEPSLATE_TILES);
        b.room(-20,3,-6,10,Blocks.BRICKS);
        b.box(-14,65,3,-12,67,3,Blocks.AIR);
        b.box(-17,66,3,-16,67,3,Blocks.GLASS_PANE);
        b.box(-12,65,8,-12,65,8,Blocks.REDSTONE_LAMP);
        b.box(-20,69,3,-6,69,3,Blocks.RED_CONCRETE);
        b.box(-10,65,20,-10,65,20,Blocks.LEVER);
        b.box(24,65,-20,24,65,-20,Blocks.LEVER);
        // Research train: two enclosed carriages, glazed windows, wheels and walkable interior.
        for(int start:new int[]{8,20}) {
            b.box(start,65,14,start+10,65,18,Blocks.IRON_BLOCK);
            b.box(start,66,14,start+10,69,14,Blocks.RED_CONCRETE);
            b.box(start,66,18,start+10,69,18,Blocks.RED_CONCRETE);
            b.box(start,70,14,start+10,70,18,Blocks.SMOOTH_STONE);
            for(int x=start+2;x<start+9;x+=3) {
                b.box(x,67,14,x+1,68,14,Blocks.GLASS);
                b.box(x,67,18,x+1,68,18,Blocks.GLASS);
                b.box(x,64,13,x,64,13,Blocks.POLISHED_BLACKSTONE);
                b.box(x,64,19,x,64,19,Blocks.POLISHED_BLACKSTONE);
            }
            b.box(start+4,65,14,start+5,67,14,Blocks.AIR);
        }
        b.box(16,65,16,16,65,16,Blocks.CHISELED_BOOKSHELF);
        b.box(24,65,16,24,65,16,Blocks.LODESTONE);
        // Evacuation engine beside the departure platform.
        b.box(-30,65,-14,-20,68,-10,Blocks.GREEN_CONCRETE);
        b.box(-28,66,-13,-23,67,-11,Blocks.AIR);
        b.box(-19,65,-12,-19,65,-12,Blocks.LEVER);
        b.box(-30,69,-14,-20,69,-10,Blocks.IRON_BLOCK);
        int[][] caches={{-32,-4},{-4,-4},{4,-4},{32,-4},{-20,16},{-8,22},{18,22},{30,-20},{18,-16},{-26,-18}};
        for(int[] cache:caches){
            b.box(cache[0],65,cache[1],cache[0],65,cache[1],dev.svextract.Content.SUPPLY_CRATE);
            b.box(cache[0],66,cache[1],cache[0],66,cache[1],Blocks.CHAIN);
        }
        b.box(-2,65,-1,2,65,-1,Blocks.SMOOTH_STONE_SLAB);
        b.box(-3,66,0,-3,66,0,Blocks.LANTERN);
        return b.changes();
    }
    public static List<Change> haven() {
        StationBlueprint b=new StationBlueprint();
        b.box(-24,62,-24,24,64,24,Blocks.STONE_BRICKS);
        b.box(-23,64,-23,23,64,23,Blocks.POLISHED_DEEPSLATE);
        b.box(-24,65,-24,24,66,-24,Blocks.STONE_BRICKS);
        b.box(-24,65,24,24,66,24,Blocks.STONE_BRICKS);
        b.box(-24,65,-24,-24,66,24,Blocks.STONE_BRICKS);
        b.box(24,65,-24,24,66,24,Blocks.STONE_BRICKS);
        b.room(-10,-16,10,-6,Blocks.WHITE_CONCRETE);
        b.box(-2,65,-6,2,68,-6,Blocks.AIR);
        b.box(-10,69,-6,10,69,-6,Blocks.BLUE_CONCRETE);
        b.box(-1,65,-10,1,65,-10,Blocks.OAK_PLANKS);
        b.box(5,65,-8,5,65,-8,dev.svextract.Content.STASH_TERMINAL);
        for(int x:new int[]{-18,18})for(int z:new int[]{-18,18}) {
            b.box(x,65,z,x,69,z,Blocks.POLISHED_DEEPSLATE);
            b.box(x,70,z,x,70,z,Blocks.SEA_LANTERN);
        }
        b.box(-2,64,-24,2,64,24,Blocks.SMOOTH_STONE);
        b.box(-24,64,-2,24,64,2,Blocks.SMOOTH_STONE);
        return b.changes();
    }
    private List<Change> changes() { return blocks.entrySet().stream().map(e->new Change(e.getKey(),e.getValue())).toList(); }
}
