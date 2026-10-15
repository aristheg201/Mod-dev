package dev.svextract.config;

import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.*;

/** Storage limits are fixed for a running server; reload cannot truncate existing ownership. */
public record StashConfig(int slots, int requestIntervalTicks,int maxConcurrentTransfers) {
    public static StashConfig load(Path path) throws IOException {
        var json=new GsonBuilder().setPrettyPrinting().create();
        if(!Files.exists(path)){Files.createDirectories(path.getParent());Files.writeString(path,json.toJson(new StashConfig(54,10,2))+"\n");}
        try {
            var tree=com.google.gson.JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            if(!tree.has("maxConcurrentTransfers"))tree.addProperty("maxConcurrentTransfers",2);
            StashConfig config=json.fromJson(tree,StashConfig.class);
            if(config==null||config.slots<27||config.slots>108||config.slots%27!=0||config.requestIntervalTicks<4||config.requestIntervalTicks>100||config.maxConcurrentTransfers<1||config.maxConcurrentTransfers>4)
                throw new IllegalArgumentException("slots must be 27, 54, 81 or 108; requestIntervalTicks must be 4–100; maxConcurrentTransfers must be 1–4");
            return config;
        }catch(RuntimeException e){throw new IOException(path+": "+e.getMessage(),e);}
    }
}
