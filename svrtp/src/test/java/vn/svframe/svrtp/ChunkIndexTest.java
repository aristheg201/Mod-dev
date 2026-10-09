package vn.svframe.svrtp;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.ByteBuffer;
import java.io.*;
import java.util.zip.DeflaterOutputStream;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import static org.junit.jupiter.api.Assertions.*;

class ChunkIndexTest {
    @org.junit.jupiter.api.BeforeAll static void bootstrap(){net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();}
    @TempDir Path dir;
    void region(boolean hole)throws Exception {
        var b=ByteBuffer.allocate(1026*4096);int sector=2;int radius=ChunkLevel.RADIUS_AROUND_FULL_CHUNK;
        for(int z=0;z<32;z++)for(int x=0;x<32;x++)
            if(Math.max(Math.abs(x-16),Math.abs(z-16))<=radius && !(hole && x==16 && z==15)) {
                byte[] payload=payload(ChunkLevel.getStatusAroundFullChunk(Math.max(Math.abs(x-16),Math.abs(z-16))).getName());b.putInt((x+z*32)*4,(sector<<8)|1);
                b.position(sector*4096);b.putInt(payload.length+1);b.put((byte)2);b.put(payload);sector++;
            }
        Files.write(dir.resolve("r.0.0.mca"),b.array());
    }
    byte[] payload(String status)throws Exception {
        var bytes=new ByteArrayOutputStream();try(var output=new DataOutputStream(new DeflaterOutputStream(bytes))) {
            output.writeByte(10);output.writeUTF("");output.writeByte(8);output.writeUTF("Status");output.writeUTF(status);output.writeByte(0);
        }return bytes.toByteArray();
    }
    @Test void incompleteGeneratedChunkDoesNotCountAsFullTerrain()throws Exception {
        region(false);byte[] bytes=Files.readAllBytes(dir.resolve("r.0.0.mca"));var b=ByteBuffer.wrap(bytes);
        int sector=b.getInt((16+16*32)*4)>>>8;byte[] payload=payload("minecraft:light");b.position(sector*4096);b.putInt(payload.length+1);b.put((byte)2);b.put(payload);
        Files.write(dir.resolve("r.0.0.mca"),bytes);assertTrue(ChunkIndex.read(dir,new Config.Destination("test:world",0,1000)).isEmpty());
    }
    @Test void onlyExistingCompleteNeighbourhoodIsIndexed()throws Exception {
        region(false);var d=new Config.Destination("test:world",0,1000);var found=ChunkIndex.read(dir,d);
        assertEquals(1,found.size());assertEquals(16,found.getFirst().x);assertEquals(16,found.getFirst().z);
        assertTrue(ChunkIndex.validate(dir,found.getFirst()));
    }
    @Test void missingNeighbourhoodIsRejected()throws Exception {
        region(true);assertTrue(ChunkIndex.read(dir,new Config.Destination("test:world",0,1000)).isEmpty());
    }
    @Test void emptyRegionsAreNotCreatedOrAccepted()throws Exception {
        Files.write(dir.resolve("r.0.0.mca"),new byte[0]);var before=Files.readAllBytes(dir.resolve("r.0.0.mca"));
        assertTrue(ChunkIndex.read(dir,new Config.Destination("test:world",0,1000)).isEmpty());assertArrayEquals(before,Files.readAllBytes(dir.resolve("r.0.0.mca")));
        Path missing=dir.resolve("missing");assertTrue(ChunkIndex.read(missing,new Config.Destination("test:world",0,1000)).isEmpty());assertFalse(Files.exists(missing));
    }
    @Test void configuredRangeIsPreserved()throws Exception {
        region(false);assertTrue(ChunkIndex.read(dir,new Config.Destination("test:world",1500,12000)).isEmpty());
    }
}
