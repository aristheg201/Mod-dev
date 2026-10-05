package vn.worldcomesalive.world;
import vn.worldcomesalive.model.LivingWorld.*;
import vn.worldcomesalive.data.WorldContent;
import net.minecraft.block.*;
import net.minecraft.block.enums.*;
import net.minecraft.registry.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import java.util.*;

/** Reusable semantic buildings. The block queue is applied with a fixed server-thread budget. */
public final class SettlementStructures {
    public record Placement(BlockPos pos,BlockState state) {}
    private static void add(Queue<Placement> q,int x,int y,int z,BlockState b){q.add(new Placement(new BlockPos(x,y,z),b));}
    private static Block block(String id){return Registries.BLOCK.get(Identifier.of(id));}
    public static void clearSite(Settlement s,ServerWorld world,Queue<Placement> q){
        int minX=s.buildings.values().stream().mapToInt(b->(int)b.origin.x()-4).min().orElse(0),maxX=s.buildings.values().stream().mapToInt(b->(int)b.origin.x()+b.width+4).max().orElse(0);
        int minZ=s.buildings.values().stream().mapToInt(b->(int)b.origin.z()-4).min().orElse(0),maxZ=s.buildings.values().stream().mapToInt(b->(int)b.origin.z()+b.depth+12).max().orElse(0);
        int ground=(int)s.center.y();
        for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++){
            world.getChunk(x>>4,z>>4);
            for(int y=ground+1;y<=ground+32;y++){
                BlockPos pos=new BlockPos(x,y,z);BlockState state=world.getBlockState(pos);
                if(state.isIn(net.minecraft.registry.tag.BlockTags.LOGS)||state.isIn(net.minecraft.registry.tag.BlockTags.LEAVES)||state.isIn(net.minecraft.registry.tag.BlockTags.REPLACEABLE)||(!state.isAir()&&y<ground+4&&state.isIn(net.minecraft.registry.tag.BlockTags.DIRT)))q.add(new Placement(pos,Blocks.AIR.getDefaultState()));
            }
        }
    }
    public static void roads(Settlement s,ServerWorld world,Queue<Placement> q){
        int y=(int)s.center.y(),z=(int)s.center.z();int min=s.buildings.values().stream().mapToInt(b->(int)b.origin.x()-3).min().orElse(0),max=s.buildings.values().stream().mapToInt(b->(int)b.origin.x()+b.width+3).max().orElse(0);
        for(int x=min;x<=max;x++)for(int dz=-2;dz<=2;dz++)ground(world,q,x,y,z+dz,Blocks.GRAVEL.getDefaultState());
        for(Building b:s.buildings.values()){
            int x=(int)b.point(Marker.ENTRANCE).x(),dest=(int)b.point(Marker.ENTRANCE).z();
            // Service paths run outside each plot, then meet the front door; roads never cut through interiors.
            int side=(int)b.origin.x()-2;
            for(int zz=Math.min(z,dest+2);zz<=Math.max(z,dest+2);zz++)for(int dx=-1;dx<=1;dx++)ground(world,q,side+dx,y,zz,Blocks.GRAVEL.getDefaultState());
            for(int xx=side;xx<=x;xx++)for(int dz=-1;dz<=1;dz++)ground(world,q,xx,y,dest+2+dz,Blocks.GRAVEL.getDefaultState());
            for(int zz=dest;zz<=dest+2;zz++)ground(world,q,x,y,zz,Blocks.GRAVEL.getDefaultState());
            b.mark(Marker.PATROL_POINT,new Pos(side+.5,y+1,z+.5));
        }
        for(int x=min+4;x<max;x+=12){add(q,x,y+1,z-3,Blocks.COBBLESTONE_WALL.getDefaultState());add(q,x,y+2,z-3,Blocks.LANTERN.getDefaultState());}
    }
    private static void ground(ServerWorld w,Queue<Placement> q,int x,int y,int z,BlockState surface){
        w.getChunk(x>>4,z>>4);int top=w.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,x,z)-1;
        for(int yy=Math.max(y-16,Math.min(top,y-1));yy<y;yy++)add(q,x,yy,z,Blocks.COBBLESTONE.getDefaultState());
        add(q,x,y,z,surface);for(int yy=y+1;yy<=Math.min(top+2,y+12);yy++)add(q,x,yy,z,Blocks.AIR.getDefaultState());
    }
    public static void building(Building b,WorldContent.Region region,ServerWorld world,Queue<Placement> q){
        int ox=(int)b.origin.x(),y=(int)b.origin.y(),oz=(int)b.origin.z();BlockState stone=block("minecraft:"+region.stone()).getDefaultState(),wood=block("minecraft:"+region.wood()+"_planks").getDefaultState(),log=block("minecraft:"+region.wood()+"_log").getDefaultState();
        if(log.isAir())log=Blocks.OAK_LOG.getDefaultState();
        for(int x=0;x<b.width;x++)for(int z=0;z<b.depth;z++){
            ground(world,q,ox+x,y,oz+z,stone);
            for(int h=1;h<=b.height+4;h++)add(q,ox+x,y+h,oz+z,Blocks.AIR.getDefaultState());
            for(int h=1;h<=b.height;h++)if(x==0||z==0||x==b.width-1||z==b.depth-1){boolean corner=(x==0||x==b.width-1)&&(z==0||z==b.depth-1);boolean beam=corner||h==b.height;BlockState wall=beam?log:h==1?stone:Blocks.WHITE_TERRACOTTA.getDefaultState();if(h==2&&!corner&&(x%3==2||z%3==2))wall=Blocks.GLASS_PANE.getDefaultState();add(q,ox+x,y+h,oz+z,wall);}
            add(q,ox+x,y,oz+z,wood);
        }
        Block stair=block("minecraft:"+region.wood()+"_stairs");
        for(int x=-1;x<=b.width;x++)for(int z=-1;z<=b.depth;z++){
            int rise=Math.min(z+1,b.depth-z);int roofY=y+b.height+Math.min(rise,4);
            BlockState roof=rise>=4?wood:stair.getDefaultState().with(StairsBlock.FACING,z<b.depth/2?Direction.SOUTH:Direction.NORTH);
            add(q,ox+x,roofY,oz+z,roof);
        }
        int doorX=ox+b.width/2,doorZ=oz+b.depth-1;
        Block door=block("minecraft:"+region.wood()+"_door");
        add(q,doorX,y+1,doorZ,door.getDefaultState().with(DoorBlock.FACING,Direction.SOUTH).with(DoorBlock.HALF,DoubleBlockHalf.LOWER).with(DoorBlock.OPEN,true));
        add(q,doorX,y+2,doorZ,door.getDefaultState().with(DoorBlock.FACING,Direction.SOUTH).with(DoorBlock.HALF,DoubleBlockHalf.UPPER).with(DoorBlock.OPEN,true));
        add(q,ox+1,y+3,oz+1,Blocks.LANTERN.getDefaultState());
        for(Pos p:b.markers.getOrDefault(Marker.BED,List.of())){int xx=(int)p.x(),zz=(int)p.z();add(q,xx,y+1,zz,Blocks.RED_BED.getDefaultState().with(BedBlock.FACING,Direction.NORTH).with(BedBlock.PART,BedPart.FOOT));add(q,xx,y+1,zz-1,Blocks.RED_BED.getDefaultState().with(BedBlock.FACING,Direction.NORTH).with(BedBlock.PART,BedPart.HEAD));}
        if(b.markers.containsKey(Marker.STORAGE)){add(q,ox+1,y+1,oz+b.depth-3,Blocks.CHEST.getDefaultState());b.markers.put(Marker.STORAGE,List.of(new Pos(ox+1.5,y+1,oz+b.depth-2.5)));}
        if(b.markers.containsKey(Marker.WORKSTATION)){Block work=switch(b.profession){case "blacksmith"->Blocks.ANVIL;case "baker"->Blocks.SMOKER;case "farmer"->Blocks.COMPOSTER;case "healer"->Blocks.BREWING_STAND;default->Blocks.CRAFTING_TABLE;};add(q,ox+b.width-2,y+1,oz+3,work.getDefaultState());b.markers.put(Marker.WORKSTATION,List.of(new Pos(ox+b.width-3.5,y+1,oz+3.5)));}
        if(b.markers.containsKey(Marker.SHOP_COUNTER)){for(int x=2;x<b.width-2;x++)add(q,ox+x,y+1,oz+b.depth-4,Blocks.OAK_SLAB.getDefaultState());b.markers.put(Marker.CUSTOMER_POINT,List.of(new Pos(ox+b.width/2.0,y+1,oz+b.depth-2.5)));}
        if(b.type.equals("tavern")){add(q,ox+3,y+1,oz+5,Blocks.CARTOGRAPHY_TABLE.getDefaultState());add(q,ox+2,y+1,oz+5,Blocks.OAK_STAIRS.getDefaultState().with(StairsBlock.FACING,Direction.EAST));add(q,ox+4,y+1,oz+5,Blocks.OAK_STAIRS.getDefaultState().with(StairsBlock.FACING,Direction.WEST));b.markers.put(Marker.CARD_DUEL_TABLE,List.of(new Pos(ox+3.5,y+1,oz+5.5)));b.markers.put(Marker.SOCIAL_POINT,List.of(new Pos(ox+5.5,y+1,oz+6.5)));}
        if(b.type.equals("farm")){for(int x=0;x<9;x++)for(int z=0;z<6;z++){ground(world,q,ox+x,y,oz+b.depth+5+z,Blocks.FARMLAND.getDefaultState());add(q,ox+x,y+1,oz+b.depth+5+z,Blocks.WHEAT.getDefaultState().with(CropBlock.AGE,7));}for(int z=0;z<6;z++)add(q,ox+9,y,oz+b.depth+5+z,Blocks.WATER.getDefaultState());b.markers.put(Marker.FARM_FIELD,List.of(new Pos(ox+4.5,y+1,oz+b.depth+7.5)));}
    }
    private SettlementStructures(){}
}
