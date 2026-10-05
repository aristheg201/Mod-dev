package vn.worldcomesalive.server;
import com.google.gson.*;
import vn.worldcomesalive.model.LivingWorld;
import java.io.*;
import java.nio.file.*;

/** Atomic checkpoints with a last-good backup. Never silently replace unreadable identities. */
public final class WorldStore {
    public static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private final Path file;
    public WorldStore(Path directory){file=directory.resolve("world-comes-alive.json");}
    public LivingWorld load() throws IOException {
        if(!Files.exists(file))return new LivingWorld();
        try{return read(file);}catch(IOException|RuntimeException primary){Path backup=file.resolveSibling(file.getFileName()+".backup");if(!Files.exists(backup))throw new IOException("Cannot read living world; refusing to reset NPC identities",primary);return read(backup);}
    }
    private LivingWorld read(Path p)throws IOException{try(Reader r=Files.newBufferedReader(p)){LivingWorld w=JSON.fromJson(r,LivingWorld.class);if(w==null||w.schema!=1||w.npcs==null||w.settlements==null)throw new IOException("Unsupported living-world checkpoint");return w;}}
    public void save(LivingWorld world)throws IOException{Files.createDirectories(file.getParent());Path temp=file.resolveSibling(file.getFileName()+".tmp");try(Writer out=Files.newBufferedWriter(temp)){JSON.toJson(world,out);}try(var channel=java.nio.channels.FileChannel.open(temp,StandardOpenOption.WRITE)){channel.force(true);}if(Files.exists(file))Files.copy(file,file.resolveSibling(file.getFileName()+".backup"),StandardCopyOption.REPLACE_EXISTING);try{Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}}
}
