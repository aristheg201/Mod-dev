package dev.svextract.world;

import net.minecraft.block.*;
import net.minecraft.util.math.BlockPos;
import java.util.*;

/** Original industrial map with connected streets, warehouses, a foundry and a service underpass. */
public final class BlackglassBlueprint {
    private final LinkedHashMap<BlockPos,BlockState> blocks=new LinkedHashMap<>();
    private void box(int x,int y,int z,int ex,int ey,int ez,Block block){
        for(int a=x;a<=ex;a++)for(int b=y;b<=ey;b++)for(int c=z;c<=ez;c++)blocks.put(new BlockPos(a,b,c),block.getDefaultState());
    }
    private void building(int x,int z,int ex,int ez,int height,Block wall){
        box(x,65,z,ex,65+height,z,wall);box(x,65,ez,ex,65+height,ez,wall);
        box(x,65,z,x,65+height,ez,wall);box(ex,65,z,ex,65+height,ez,wall);
        box(x,66+height,z,ex,66+height,ez,Blocks.DEEPSLATE_TILES);
        for(int a=x+2;a<ex-1;a+=4)box(a,67,z,a+1,68,z,Blocks.GLASS);
    }
    public static List<StationBlueprint.Change> create(){
        BlackglassBlueprint b=new BlackglassBlueprint();
        b.box(-52,62,-52,52,63,52,Blocks.DEEPSLATE);
        b.box(-51,64,-51,51,64,51,Blocks.COARSE_DIRT);
        b.box(-52,64,-52,52,68,-52,Blocks.CRACKED_DEEPSLATE_BRICKS);
        b.box(-52,64,52,52,68,52,Blocks.CRACKED_DEEPSLATE_BRICKS);
        b.box(-52,64,-52,-52,68,52,Blocks.DEEPSLATE_BRICKS);
        b.box(52,64,-52,52,68,52,Blocks.DEEPSLATE_BRICKS);
        b.box(-49,64,-6,49,64,5,Blocks.GRAY_CONCRETE);
        b.box(-5,64,-49,5,64,49,Blocks.GRAY_CONCRETE);
        for(int x=-48;x<=48;x+=8)b.box(x,64,-1,x+3,64,-1,Blocks.YELLOW_CONCRETE);
        // Electrical works: southern doorway and terminal; an alternate open service yard.
        b.building(-24,2,-5,18,5,Blocks.BRICKS);b.box(-14,65,2,-11,68,2,Blocks.AIR);
        b.box(-24,65,12,-24,67,14,Blocks.AIR);b.box(-12,65,8,-12,65,8,Blocks.REDSTONE_LAMP);
        // Research warehouse with two entrances and optional recovery objects.
        b.building(12,8,35,27,7,Blocks.LIGHT_GRAY_CONCRETE);
        b.box(12,65,14,12,68,17,Blocks.AIR);b.box(23,65,27,26,68,27,Blocks.AIR);
        b.box(16,65,16,16,65,16,Blocks.CHISELED_BOOKSHELF);b.box(24,65,16,24,65,16,Blocks.LODESTONE);
        // Foundry: high chimneys, open loading bays and an elevated exposed gantry.
        b.building(-34,-43,-10,-22,9,Blocks.POLISHED_BLACKSTONE_BRICKS);
        b.box(-27,65,-22,-20,70,-22,Blocks.AIR);
        for(int x:new int[]{-31,-16}){b.box(x,75,-38,x+2,86,-36,Blocks.BRICKS);b.box(x+1,76,-37,x+1,86,-37,Blocks.AIR);}
        b.box(-31,71,-27,31,71,-25,Blocks.IRON_BLOCK);
        for(int x=-31;x<=31;x+=6)b.box(x,72,-25,x,72,-25,Blocks.IRON_BARS);
        for(int i=0;i<7;i++)b.box(25+i,65+i,-28,25+i,65+i,-26,Blocks.SMOOTH_STONE);
        // Southern freight lanes have gaps, flanking paths and low cover.
        for(int row=0;row<3;row++)for(int lane=0;lane<3;lane++){
            int x=-38+lane*26,z=28+row*7;
            b.box(x,65,z,x+15,68,z+4,(row+lane)%2==0?Blocks.BLUE_CONCRETE:Blocks.ORANGE_CONCRETE);
            b.box(x+1,65,z+1,x+14,67,z+3,Blocks.AIR);b.box(x,65,z+1,x,67,z+2,Blocks.AIR);
        }
        // Portal frame and lift platform are physical, server-controlled evacuation sites.
        b.box(-22,64,-15,-16,64,-9,Blocks.POLISHED_BLACKSTONE);
        b.box(-22,65,-15,-22,69,-15,Blocks.OBSIDIAN);b.box(-16,65,-15,-16,69,-15,Blocks.OBSIDIAN);
        b.box(-22,69,-15,-16,69,-15,Blocks.OBSIDIAN);b.box(-19,65,-12,-19,65,-12,Blocks.LEVER);
        b.box(37,64,-39,47,64,-29,Blocks.IRON_BLOCK);b.box(43,65,-35,43,65,-35,Blocks.LEVER);
        int[][] caches={{-32,-4},{-4,-4},{4,-4},{32,-4},{-20,16},{-8,22},{18,22},{30,-20},{18,-16},{-26,-18}};
        for(int[] cache:caches)b.box(cache[0],65,cache[1],cache[0],65,cache[1],dev.svextract.Content.SUPPLY_CRATE);
        for(int x:new int[]{-44,44})for(int z:new int[]{-44,44}){b.box(x,65,z,x,70,z,Blocks.IRON_BLOCK);b.box(x,71,z,x,71,z,Blocks.SEA_LANTERN);}
        // A genuine descending service route, rather than a decorative underground room.
        for(int step=0;step<16;step++){
            int z=-8+step,y=63-step;
            b.box(-42,y,z,-39,64,z,Blocks.AIR);b.box(-42,y-1,z,-39,y-1,z,Blocks.STONE_BRICKS);
        }
        b.box(-42,47,8,2,47,11,Blocks.STONE_BRICKS);b.box(-42,48,8,2,51,11,Blocks.AIR);
        // An added safe catwalk crosses the imported chamber above its original lava floor.
        b.box(-2,47,-20,2,47,20,Blocks.STONE_BRICKS);b.box(-2,48,-20,2,51,20,Blocks.AIR);
        for(int z=-20;z<=20;z+=8){b.box(-3,48,z,-3,48,z,Blocks.LANTERN);b.box(3,48,z,3,48,z,Blocks.LANTERN);}
        return b.blocks.entrySet().stream().map(e->new StationBlueprint.Change(e.getKey(),e.getValue())).toList();
    }
}
