package vn.worldcomesalive.agriculture;
import vn.worldcomesalive.model.LivingWorld.*;
import vn.worldcomesalive.world.SettlementStructures.Placement;
import vn.worldcomesalive.furniture.*;
import net.minecraft.block.*;
import net.minecraft.block.enums.*;
import net.minecraft.util.math.*;
import java.util.*;
/** Functional lots, agricultural belt, restrained tracks and civic focal point. */
public final class Landscape {
    private static void place(Queue<Placement> q,int x,int y,int z,BlockState state){q.add(new Placement(new BlockPos(x,y,z),state));}
    private static void piece(Queue<Placement> q,String id,int x,int y,int z,Direction direction){place(q,x,y,z,FurnitureRegistry.BLOCKS.get(id).getDefaultState().with(HorizontalFacingBlock.FACING,direction));}
    public static void generate(Settlement s,net.minecraft.server.world.ServerWorld world,Queue<Placement> q){
        for(var p:s.fields.values()){
            for(Pos cell:p.cells){int x=(int)cell.x(),z=(int)cell.z(),y=(int)cell.y();world.getChunk(x>>4,z>>4);int top=world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,x,z)-1;for(int fill=Math.min(top,y);fill<y;fill++)place(q,x,fill,z,Blocks.DIRT.getDefaultState());for(int clear=y+1;clear<=Math.max(y+3,top+1);clear++)place(q,x,clear,z,Blocks.AIR.getDefaultState());place(q,x,y,z,Blocks.FARMLAND.getDefaultState().with(FarmlandBlock.MOISTURE,7));var crop=p.crop.equals("minecraft:carrot")?Blocks.CARROTS:p.crop.equals("minecraft:potato")?Blocks.POTATOES:Blocks.WHEAT;place(q,x,y+1,z,crop.getDefaultState().with(CropBlock.AGE,p.state.equals("MATURE")?7:3));}
            int ox=(int)p.origin.x(),oy=(int)p.origin.y(),oz=(int)p.origin.z();for(int z=0;z<p.depth;z++)place(q,ox+p.width,oy,oz+z,Blocks.WATER.getDefaultState());
            for(int x=-1;x<=p.width+1;x++){place(q,ox+x,oy+1,oz-1,Blocks.OAK_FENCE.getDefaultState());place(q,ox+x,oy+1,oz+p.depth,Blocks.OAK_FENCE.getDefaultState());}for(int z=0;z<p.depth;z++){place(q,ox-1,oy+1,oz+z,Blocks.OAK_FENCE.getDefaultState());place(q,ox+p.width+1,oy+1,oz+z,Blocks.OAK_FENCE.getDefaultState());}
            place(q,ox+p.width/2,oy+1,oz-1,Blocks.OAK_FENCE_GATE.getDefaultState().with(FenceGateBlock.OPEN,true));
        }
        for(var p:s.pastures.values()){int x=(int)p.origin.x(),y=(int)p.origin.y(),z=(int)p.origin.z();for(int dx=0;dx<p.width;dx++)for(int dz=0;dz<p.depth;dz++){world.getChunk((x+dx)>>4,(z+dz)>>4);place(q,x+dx,y,z+dz,Blocks.GRASS_BLOCK.getDefaultState());for(int h=1;h<=3;h++)place(q,x+dx,y+h,z+dz,Blocks.AIR.getDefaultState());if(dx==0||dz==0||dx==p.width-1||dz==p.depth-1)place(q,x+dx,y+1,z+dz,Blocks.OAK_FENCE.getDefaultState());}place(q,x+p.width/2,y+1,z,Blocks.OAK_FENCE_GATE.getDefaultState().with(FenceGateBlock.OPEN,false));piece(q,"hay_storage",x+2,y+1,z+2,Direction.NORTH);place(q,x+2,y,z+3,Blocks.WATER.getDefaultState());}
        for(Building b:s.buildings.values()){int x=(int)b.origin.x(),y=(int)b.origin.y()+1,z=(int)b.origin.z();piece(q,"firewood",x+b.width-2,y,z+b.depth,Direction.NORTH);piece(q,"outdoor_bench",x+1,y,z+b.depth,Direction.NORTH);piece(q,b.type.equals("forge")?"ore_crate":b.type.equals("tavern")?"keg":"produce_basket",x+b.width-1,y,z+b.depth,Direction.NORTH);
            for(int xx=2;xx<b.width-2;xx+=3)piece(q,"flower_vase",x+xx,y+1,z+b.depth-1,Direction.NORTH);
            if(b.type.equals("tavern")){for(int dz=1;dz<=6;dz++){place(q,x+b.width+3,y-1,z+dz,Blocks.DIRT_PATH.getDefaultState());place(q,x+b.width+6,y,z+dz,Blocks.OAK_FENCE.getDefaultState());}for(int xx=3;xx<=6;xx++)place(q,x+b.width+xx,y+3,z+3,Blocks.SPRUCE_SLAB.getDefaultState());piece(q,"hay_storage",x+b.width+4,y,z+2,Direction.NORTH);}
        }
        // A recognisable civic green and covered market sit beside, not across, the main road.
        int cx=(int)s.center.x(),cy=(int)s.center.y(),cz=(int)s.center.z();for(int x=-2;x<=2;x++)for(int z=4;z<=8;z++)place(q,cx+x,cy,cz+z,Blocks.MOSSY_COBBLESTONE.getDefaultState());
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){place(q,cx+dx,cy+1,cz+6+dz,dx==0&&dz==0?Blocks.WATER.getDefaultState():Blocks.STONE_BRICK_WALL.getDefaultState());place(q,cx+dx,cy+4,cz+6+dz,Blocks.SPRUCE_SLAB.getDefaultState());}
        place(q,cx-1,cy+2,cz+5,Blocks.OAK_FENCE.getDefaultState());place(q,cx-1,cy+3,cz+5,Blocks.OAK_FENCE.getDefaultState());place(q,cx+1,cy+2,cz+7,Blocks.OAK_FENCE.getDefaultState());place(q,cx+1,cy+3,cz+7,Blocks.OAK_FENCE.getDefaultState());
        for(int x=7;x<=11;x++){piece(q,"serving_counter",cx+x,cy+1,cz+7,Direction.SOUTH);place(q,cx+x,cy+4,cz+7,((x&1)==0?Blocks.RED_WOOL:Blocks.WHITE_WOOL).getDefaultState());if(x==7||x==11)for(int h=1;h<=3;h++)place(q,cx+x,cy+h,cz+8,Blocks.OAK_FENCE.getDefaultState());}piece(q,"produce_basket",cx+8,cy+2,cz+7,Direction.NORTH);piece(q,"bread_basket",cx+10,cy+2,cz+7,Direction.NORTH);piece(q,"notice_board",cx-5,cy+1,cz+7,Direction.SOUTH);
        // An orchard reads as countryside rather than an abrupt edge of equally-sized boxes.
        for(int i=0;i<6;i++){int x=cx-22+(i%3)*5,z=cz+57+(i/3)*5;world.getChunk(x>>4,z>>4);int y=world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,x,z)-1;for(int h=1;h<=4;h++)place(q,x,y+h,z,Blocks.OAK_LOG.getDefaultState());for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)if(Math.abs(dx)+Math.abs(dz)<4)for(int h=3;h<=5;h++)place(q,x+dx,y+h,z+dz,Blocks.OAK_LEAVES.getDefaultState().with(LeavesBlock.PERSISTENT,true));}
    }
    public static void roads(Settlement s,net.minecraft.server.world.ServerWorld world,Queue<Placement> q){
        int cx=(int)s.center.x(),cz=(int)s.center.z();int min=s.buildings.values().stream().mapToInt(b->(int)b.origin.x()-cx-2).min().orElse(-40),max=s.buildings.values().stream().mapToInt(b->(int)b.origin.x()-cx+b.width+2).max().orElse(40);
        for(int x=min;x<=max;x++){int bend=(int)Math.round(Math.sin((x+Math.floorMod(s.seed,20))/22.0)*2);for(int width=-1;width<=1;width++)ground(world,q,cx+x,cz+bend+width,Blocks.COARSE_DIRT.getDefaultState());}
        for(Building b:s.buildings.values()){int x=(int)b.point(Marker.ENTRANCE).x(),dest=(int)b.point(Marker.ENTRANCE).z()+2,side=(int)b.origin.x()-2;int curve=cz+(int)Math.round(Math.sin((side-cx+Math.floorMod(s.seed,20))/22.0)*2);for(int z=Math.min(curve,dest);z<=Math.max(curve,dest);z++){ground(world,q,side,z,Blocks.DIRT_PATH.getDefaultState());if(b.type.equals("tavern")||b.type.equals("barn"))ground(world,q,side-1,z,Blocks.COARSE_DIRT.getDefaultState());}for(int xx=side;xx<=x;xx++)ground(world,q,xx,dest,Blocks.DIRT_PATH.getDefaultState());for(int z=dest-2;z<=dest;z++)ground(world,q,x,z,Blocks.DIRT_PATH.getDefaultState());}
        for(var p:s.fields.values())if(p.width>=12){int x=(int)p.origin.x()+p.width/2,z=(int)p.origin.z()-2;for(int zz=cz+26;zz<=z;zz++)ground(world,q,x,zz,Blocks.COARSE_DIRT.getDefaultState());for(int xx=(int)s.center.x()-49;xx<=x;xx++)ground(world,q,xx,cz+27,Blocks.DIRT_PATH.getDefaultState());}
    }
    private static void ground(net.minecraft.server.world.ServerWorld world,Queue<Placement> q,int x,int z,BlockState state){world.getChunk(x>>4,z>>4);int y=world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,x,z)-1;for(int n=0;n<3&&world.getBlockState(new BlockPos(x,y,z)).isIn(net.minecraft.registry.tag.BlockTags.REPLACEABLE);n++)y--;q.add(new Placement(new BlockPos(x,y,z),state));if(!world.getBlockState(new BlockPos(x,y+1,z)).isAir())q.add(new Placement(new BlockPos(x,y+1,z),Blocks.AIR.getDefaultState()));}
    private Landscape(){}
}
