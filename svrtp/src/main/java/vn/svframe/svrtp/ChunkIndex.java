package vn.svframe.svrtp;

import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import java.io.IOException;
import java.io.*;
import net.minecraft.nbt.*;
import net.minecraft.nbt.visitors.*;
import net.minecraft.world.level.chunk.storage.RegionFileVersion;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.world.level.ChunkPos;

/** Bounded read-only disk index. This class never touches a live world or creates a region file. */
final class ChunkIndex {
    static final int MAX_REGIONS=256,MAX_ENTRIES=65536,MAX_CANDIDATES=16384,MAX_STATUS_READS=8192;
    static final long MAX_INDEX_NANOS=5_000_000_000L;
    record Entry(Path region,long offset,int sectors,int x,int z) {}
    static List<ChunkPos> read(Path folder,Config.Destination d)throws IOException {
        if(!Files.isDirectory(folder))return List.of();
        long deadline=System.nanoTime()+MAX_INDEX_NANOS;
        var allocated=new HashMap<Long,Entry>();int inspected=0;
        try(var files=Files.newDirectoryStream(folder,"r.*.*.mca")) {
            for(Path path:files){
                if(Thread.currentThread().isInterrupted())throw new IOException("Index cancelled");
                String[] parts=path.getFileName().toString().split("\\.");if(parts.length!=4)continue;
                int rx,rz;try{rx=Integer.parseInt(parts[1]);rz=Integer.parseInt(parts[2]);}catch(NumberFormatException e){continue;}
                double nearestX=Math.max(rx*512.0,Math.min(d.centerX,(rx+1)*512.0)),nearestZ=Math.max(rz*512.0,Math.min(d.centerZ,(rz+1)*512.0));
                if(Math.hypot(nearestX-d.centerX,nearestZ-d.centerZ)>d.maxRadius+(ChunkLevel.RADIUS_AROUND_FULL_CHUNK+1)*16 || Files.size(path)<8192)continue;
                if(inspected++>=MAX_REGIONS || allocated.size()>=MAX_ENTRIES)break;
                ByteBuffer header=ByteBuffer.allocate(4096);
                try(var file=FileChannel.open(path,StandardOpenOption.READ)){
                    while(header.hasRemaining()){int n=file.read(header);if(n<0)break;}
                }
                if(header.hasRemaining())continue;header.flip();
                for(int i=0;i<1024 && allocated.size()<MAX_ENTRIES;i++){
                    int location=header.getInt();if((location>>>8)<2 || (location&255)==0)continue;
                    int x=rx*32+(i&31),z=rz*32+(i>>5);
                    allocated.put(ChunkPos.asLong(x,z),new Entry(path,(long)(location>>>8)*4096,location&255,x,z));
                }
            }
        }
        var candidates=new ArrayList<ChunkPos>();int eligible=0;var random=new Random();
        var order=new ArrayList<>(allocated.keySet());Collections.shuffle(order,random);
        var full=new HashMap<Long,ChunkStatus>();
        for(long packed:order){
            if(Thread.currentThread().isInterrupted())throw new IOException("Index cancelled");
            if(System.nanoTime()>deadline || full.size()>=MAX_STATUS_READS)break;
            ChunkPos p=new ChunkPos(packed);double lowX=p.x*16.0+3.5,highX=p.x*16.0+12.5,lowZ=p.z*16.0+3.5,highZ=p.z*16.0+12.5;
            double closestX=Math.max(lowX,Math.min(d.centerX,highX)),closestZ=Math.max(lowZ,Math.min(d.centerZ,highZ));
            double furthestX=Math.max(Math.abs(lowX-d.centerX),Math.abs(highX-d.centerX)),furthestZ=Math.max(Math.abs(lowZ-d.centerZ),Math.abs(highZ-d.centerZ));
            if(Math.hypot(closestX-d.centerX,closestZ-d.centerZ)>d.maxRadius || Math.hypot(furthestX,furthestZ)<d.minRadius)continue;
            // An allocated region entry may only contain structure starts or lighting.
            // Read only Status off-thread; never activate/generate these incomplete chunks.
            if(status(packed,allocated,full)!=ChunkStatus.FULL)continue;
            boolean neighbours=true;int radius=ChunkLevel.RADIUS_AROUND_FULL_CHUNK;
            for(int dx=-radius;dx<=radius && neighbours;dx++)for(int dz=-radius;dz<=radius;dz++) {
                ChunkStatus required=ChunkLevel.getStatusAroundFullChunk(Math.max(Math.abs(dx),Math.abs(dz)));
                if(required==ChunkStatus.EMPTY)continue;
                if(System.nanoTime()>deadline || full.size()>=MAX_STATUS_READS || !status(ChunkPos.asLong(p.x+dx,p.z+dz),allocated,full).isOrAfter(required)){neighbours=false;break;}
            }
            if(!neighbours)continue;
            eligible++;
            if(candidates.size()<MAX_CANDIDATES)candidates.add(p);
            else {int replace=random.nextInt(eligible);if(replace<MAX_CANDIDATES)candidates.set(replace,p);}
        }
        SVRTP.LOG.info("RTP terrain index dimension={} regions={} allocated={} statusReads={} fullCandidates={} elapsedMs={} limited={}",
                d.dimension,inspected,allocated.size(),full.size(),candidates.size(),(System.nanoTime()-(deadline-MAX_INDEX_NANOS))/1_000_000,
                full.size()>=MAX_STATUS_READS || System.nanoTime()>deadline || inspected>=MAX_REGIONS || allocated.size()>=MAX_ENTRIES);
        return List.copyOf(candidates);
    }
    private static ChunkStatus status(long packed,Map<Long,Entry> entries,Map<Long,ChunkStatus> cache)throws IOException {
        ChunkStatus known=cache.get(packed);if(known!=null)return known;
        ChunkStatus result=readStatus(entries.get(packed));cache.put(packed,result);return result;
    }
    private static ChunkStatus readStatus(Entry entry)throws IOException {
        ChunkStatus result=ChunkStatus.EMPTY;
        if(entry!=null)try(var file=FileChannel.open(entry.region,StandardOpenOption.READ)) {
            ByteBuffer prefix=ByteBuffer.allocate(5);file.position(entry.offset);
            while(prefix.hasRemaining() && file.read(prefix)>0){}
            if(!prefix.hasRemaining()) {
                prefix.flip();int length=prefix.getInt(),compression=prefix.get()&255;
                RegionFileVersion version=RegionFileVersion.fromId(compression&127);
                if(version!=null && (length>1 || length==1 && (compression&128)!=0) && length<=entry.sectors*4096-4 && length<=1_048_576) {
                    byte[] bytes;
                    if((compression&128)!=0) {
                        Path external=entry.region.getParent().resolve("c."+entry.x+"."+entry.z+".mcc");
                        bytes=Files.isRegularFile(external) && Files.size(external)<=1_048_576?Files.readAllBytes(external):null;
                    } else {
                        ByteBuffer data=ByteBuffer.allocate(length-1);
                        while(data.hasRemaining() && file.read(data)>0){}
                        bytes=data.hasRemaining()?null:data.array();
                    }
                    if(bytes!=null)try(var input=new DataInputStream(new BufferedInputStream(version.wrap(new ByteArrayInputStream(bytes))))) {
                        var fields=new CollectFields(new FieldSelector(StringTag.TYPE,"Status"));
                        NbtIo.parse(input,fields,NbtAccounter.create(16*1024*1024));
                        if(fields.getResult() instanceof CompoundTag tag) {
                            var stored=ChunkStatus.byName(tag.getString("Status"));if(stored!=null)result=stored;
                        }
                    }
                }
            }
        }catch(IOException | RuntimeException unreadable) {SVRTP.LOG.debug("RTP could not read terrain metadata chunk={},{}",entry.x,entry.z,unreadable);result=ChunkStatus.EMPTY;}
        return result;
    }
    /** Recheck the native loading-ticket dependency halo immediately before activation. */
    static boolean validate(Path folder,ChunkPos center)throws IOException {
        var headers=new HashMap<Long,ByteBuffer>();long deadline=System.nanoTime()+2_000_000_000L;
        int radius=ChunkLevel.RADIUS_AROUND_FULL_CHUNK;
        for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++) {
            if(Thread.currentThread().isInterrupted() || System.nanoTime()>deadline)return false;
            var required=ChunkLevel.getStatusAroundFullChunk(Math.max(Math.abs(dx),Math.abs(dz)));
            if(required==ChunkStatus.EMPTY)continue;
            int x=center.x+dx,z=center.z+dz,rx=x>>5,rz=z>>5;
            Path path=folder.resolve("r."+rx+"."+rz+".mca");long key=ChunkPos.asLong(rx,rz);var header=headers.get(key);
            if(header==null) {
                if(!Files.isRegularFile(path) || Files.size(path)<8192)return false;
                header=ByteBuffer.allocate(4096);
                try(var file=FileChannel.open(path,StandardOpenOption.READ)){while(header.hasRemaining() && file.read(header)>0){}}
                if(header.hasRemaining())return false;header.flip();headers.put(key,header);
            }
            int location=header.getInt(((x&31)+(z&31)*32)*4);
            if((location>>>8)<2 || (location&255)==0)return false;
            if(!readStatus(new Entry(path,(long)(location>>>8)*4096,location&255,x,z)).isOrAfter(required))return false;
        }
        return true;
    }
}
