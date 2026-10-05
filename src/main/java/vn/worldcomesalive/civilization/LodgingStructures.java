package vn.worldcomesalive.civilization;
import vn.worldcomesalive.model.LivingWorld.*;
import vn.worldcomesalive.world.SettlementStructures.Placement;
import vn.worldcomesalive.furniture.FurnitureRegistry;
import net.minecraft.block.*;
import net.minecraft.block.enums.*;
import net.minecraft.util.math.*;
import java.util.*;
/** Attic accommodation is a separate semantic furnishing pass above the functioning common room. */
public final class LodgingStructures {
    public static void generate(Building b,List<Lodging.Room> rooms,Queue<Placement> q){if(rooms.isEmpty())return;int x=(int)b.origin.x(),y=(int)b.origin.y(),z=(int)b.origin.z();
        for(int dx=1;dx<16;dx++)for(int dz=3;dz<=7;dz++)q.add(new Placement(new BlockPos(x+dx,y+4,z+dz),Blocks.SPRUCE_PLANKS.getDefaultState()));
        for(int dz=6;dz<=10;dz++){int h=10-dz;q.add(new Placement(new BlockPos(x+8,y+h,z+dz),Blocks.SPRUCE_STAIRS.getDefaultState().with(StairsBlock.FACING,Direction.NORTH)));for(int clear=h+1;clear<=h+3;clear++)q.add(new Placement(new BlockPos(x+8,y+clear,z+dz),Blocks.AIR.getDefaultState()));}
        for(int dx:new int[]{7,10})for(int dz=3;dz<=6;dz++)if(dz!=5)for(int h=5;h<=7;h++)q.add(new Placement(new BlockPos(x+dx,y+h,z+dz),Blocks.SPRUCE_PLANKS.getDefaultState()));
        for(var r:rooms){int doorX=(int)r.entrance.x(),doorZ=(int)r.entrance.z();for(var half:DoubleBlockHalf.values())q.add(new Placement(new BlockPos(doorX,y+(half==DoubleBlockHalf.LOWER?5:6),doorZ),Blocks.SPRUCE_DOOR.getDefaultState().with(DoorBlock.HALF,half).with(DoorBlock.FACING,Direction.EAST).with(DoorBlock.OPEN,false)));
            for(Pos bed:r.beds){BlockPos foot=BlockPos.ofFloored(bed.x(),bed.y(),bed.z());q.add(new Placement(foot,Blocks.BLUE_BED.getDefaultState().with(BedBlock.FACING,Direction.NORTH).with(BedBlock.PART,BedPart.FOOT)));q.add(new Placement(foot.north(),Blocks.BLUE_BED.getDefaultState().with(BedBlock.FACING,Direction.NORTH).with(BedBlock.PART,BedPart.HEAD)));}
            prop(q,"storage_chest",r.storage,Direction.NORTH);prop(q,"folded_cloth",new Pos(r.storage.x(),r.storage.y()+1,r.storage.z()),Direction.NORTH);Pos light=new Pos(r.min.x()+1,y+6,r.min.z()+1);prop(q,"wall_sconce",light,Direction.SOUTH);prop(q,"rug",new Pos(r.min.x()+3,y+5,z+6),Direction.NORTH);prop(q,"bedside_table",new Pos(r.min.x()+1,y+5,z+4),Direction.NORTH);prop(q,"candle_holder",new Pos(r.min.x()+1,y+6,z+4),Direction.NORTH);prop(q,"book_stack",new Pos(r.min.x()+1,y+6,z+6),Direction.NORTH);
        }
    }
    private static void prop(Queue<Placement> q,String type,Pos pos,Direction facing){q.add(new Placement(BlockPos.ofFloored(pos.x(),pos.y(),pos.z()),FurnitureRegistry.BLOCKS.get(type).getDefaultState().with(HorizontalFacingBlock.FACING,facing)));}
    private LodgingStructures(){}
}
