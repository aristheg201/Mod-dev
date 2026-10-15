package dev.svextract.world;

import net.minecraft.nbt.*;
import net.minecraft.registry.Registries;
import net.minecraft.block.BlockState;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import java.io.*;
import java.util.*;

/** Bounded template decoding before admissions. No world/chunk copying or mutation here. */
public final class ImportedStructure {
    public record Cell(int x,int y,int z,int state) {}
    public record Template(int x,int y,int z,List<NbtCompound> palette,List<Cell> cells,int omittedPayloads) {}
    public static Template read(InputStream input) throws IOException {
        NbtCompound root=NbtIo.readCompressed(input,NbtSizeTracker.of(32L*1024*1024));
        NbtList size=root.getList("size",NbtElement.INT_TYPE);
        if(size.size()!=3)throw new IOException("Imported structure needs exactly three size coordinates");
        int x=size.getInt(0),y=size.getInt(1),z=size.getInt(2);
        if(x<1||y<1||z<1||x>128||y>128||z>128||(long)x*y*z>1_000_000)
            throw new IOException("Imported structure exceeds 128 blocks per axis or 1,000,000 cells");
        NbtList palette=root.getList("palette",NbtElement.COMPOUND_TYPE),blocks=root.getList("blocks",NbtElement.COMPOUND_TYPE);
        if(palette.isEmpty()||palette.size()>4096||blocks.size()>1_000_000)throw new IOException("Invalid bounded structure palette/block count");
        List<NbtCompound> states=new ArrayList<>(palette.size());
        for(int i=0;i<palette.size();i++)states.add(palette.getCompound(i).copy());
        BitSet occupied=new BitSet(x*y*z);List<Cell> cells=new ArrayList<>(blocks.size());int omitted=root.getList("entities",NbtElement.COMPOUND_TYPE).size();
        for(int i=0;i<blocks.size();i++) {
            NbtCompound b=blocks.getCompound(i);NbtList p=b.getList("pos",NbtElement.INT_TYPE);
            if(p.size()!=3)throw new IOException("Cell "+i+" has no three-coordinate position");
            int bx=p.getInt(0),by=p.getInt(1),bz=p.getInt(2),state=b.getInt("state");
            if(bx<0||by<0||bz<0||bx>=x||by>=y||bz>=z||!b.contains("state",NbtElement.INT_TYPE)||state<0||state>=states.size())
                throw new IOException("Cell "+i+" has an out-of-bounds position or palette index");
            int key=(by*z+bz)*x+bx;if(occupied.get(key))throw new IOException("Duplicate structure cell "+bx+","+by+","+bz);occupied.set(key);
            if(b.contains("nbt"))omitted++;
            cells.add(new Cell(bx,by,bz,state));
        }
        return new Template(x,y,z,List.copyOf(states),List.copyOf(cells),omitted);
    }
    /** Registry access belongs to the main server initialization thread. Fail on missing blocks/properties. */
    public static List<StationBlueprint.Change> resolve(Template template,BlockPos origin) throws IOException {
        List<BlockState> states=new ArrayList<>(template.palette.size());
        for(NbtCompound definition:template.palette) {
            Identifier id=Identifier.tryParse(definition.getString("Name"));
            if(id==null||!Registries.BLOCK.containsId(id))throw new IOException("Unregistered imported block: "+definition.getString("Name"));
            if(Set.of("minecraft:spawner","minecraft:jigsaw","minecraft:structure_block","minecraft:command_block","minecraft:chain_command_block","minecraft:repeating_command_block").contains(id.toString())) {
                states.add(net.minecraft.block.Blocks.AIR.getDefaultState());continue;
            }
            BlockState state=Registries.BLOCK.get(id).getDefaultState();NbtCompound properties=definition.getCompound("Properties");
            for(String key:properties.getKeys()) {
                Property<?> property=state.getBlock().getStateManager().getProperty(key);
                if(property==null)throw new IOException(id+": unsupported imported property "+key);
                state=with(state,property,properties.getString(key),id);
            }
            states.add(state);
        }
        List<StationBlueprint.Change> result=new ArrayList<>(template.cells.size());
        for(Cell c:template.cells)result.add(new StationBlueprint.Change(origin.add(c.x,c.y,c.z),states.get(c.state)));
        return result;
    }
    private static <T extends Comparable<T>> BlockState with(BlockState state,Property<T> property,String value,Identifier id) throws IOException {
        T parsed=property.parse(value).orElseThrow(()->new IOException(id+": unsupported "+property.getName()+"="+value));
        return state.with(property,parsed);
    }
}
