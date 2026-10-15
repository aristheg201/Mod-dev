package dev.svextract;

import dev.svextract.world.ImportedStructure;
import net.minecraft.nbt.*;
import java.io.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ImportedStructureTest {
    private static NbtCompound structure(){
        NbtCompound root=new NbtCompound();NbtList size=new NbtList();for(int n:new int[]{2,2,2})size.add(NbtInt.of(n));root.put("size",size);
        NbtList palette=new NbtList();NbtCompound p=new NbtCompound();p.putString("Name","minecraft:stone");palette.add(p);root.put("palette",palette);
        NbtList blocks=new NbtList();NbtCompound b=new NbtCompound();NbtList pos=new NbtList();for(int i=0;i<3;i++)pos.add(NbtInt.of(0));b.put("pos",pos);b.putInt("state",0);b.put("nbt",new NbtCompound());blocks.add(b);root.put("blocks",blocks);return root;
    }
    private static ImportedStructure.Template read(NbtCompound root)throws IOException {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();NbtIo.writeCompressed(root,bytes);return ImportedStructure.read(new ByteArrayInputStream(bytes.toByteArray()));
    }
    @Test void preservesGeometryAndCountsExcludedPayloads()throws Exception {
        var t=read(structure());assertEquals(1,t.cells().size());assertEquals("minecraft:stone",t.palette().getFirst().getString("Name"));assertEquals(1,t.omittedPayloads());
    }
    @Test void rejectsDuplicateAndOutOfBoundsCells()throws Exception {
        var duplicate=structure();var b=duplicate.getList("blocks",NbtElement.COMPOUND_TYPE);b.add(b.getCompound(0).copy());assertThrows(IOException.class,()->read(duplicate));
        var bad=structure();bad.getList("blocks",NbtElement.COMPOUND_TYPE).getCompound(0).putInt("state",2);assertThrows(IOException.class,()->read(bad));
    }
    @Test void rejectsUnboundedGeometry() {
        var s=structure();s.getList("size",NbtElement.INT_TYPE).set(0,NbtInt.of(129));assertThrows(IOException.class,()->read(s));
    }
}
