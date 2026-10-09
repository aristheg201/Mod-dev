package vn.svframe.svrtp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;

/** Immutable after validation; reload swaps a complete snapshot, never an active job's rules. */
public final class Config {
    static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    public int configVersion = 1;
    public Map<String, Destination> destinations = new LinkedHashMap<>();
    public Performance performance = new Performance();
    public Prices prices = new Prices();
    public Permissions permissions = new Permissions();
    public String language = "vi";
    public boolean resourcePackGui = false;
    public Config() {
        destinations.put("resource", new Destination("",1500,12000));
        destinations.put("nether", new Destination("minecraft:the_nether",300,6000));
        destinations.put("end", new Destination("minecraft:the_end",1500,10000));
    }
    public static final class Destination {
        public boolean enabled = true;
        public String dimension;
        public int minRadius, maxRadius, cooldownSeconds = 60;
        public double centerX = 0, centerZ = 0;
        public Destination(String dimension,int min,int max) { this.dimension=dimension;minRadius=min;maxRadius=max; }
    }
    public static final class Performance {
        public int maxConcurrentRequests=2,maxAttemptsPerRequest=24,requestTimeoutSeconds=15;
        public int safeLocationCacheSize=256,maxValidationWorkPerTick=4;
        public boolean allowChunkGeneration=false;
    }
    public static final class Prices { public long beastCoin=5,cobbleDollars=50000; }
    public static final class Permissions {
        public String use="svrtp.use",resource="svrtp.use.resource",nether="svrtp.use.nether",end="svrtp.use.end",admin="svrtp.admin";
    }
    public void validate() {
        require(configVersion==1,"Unsupported configVersion");
        require(destinations!=null && destinations.keySet().equals(Set.of("resource","nether","end")),"Exactly resource, nether and end are required");
        require(performance!=null && prices!=null && permissions!=null,"Missing configuration section");
        for(var entry:destinations.entrySet()) {
            var d=entry.getValue();require(d!=null,"Missing destination");
            require(d.dimension!=null && (d.dimension.isBlank() || d.dimension.matches("[a-z0-9_.-]+:[a-z0-9_/.-]+")),"Invalid dimension ID");
            require(d.minRadius>=0 && d.maxRadius>=d.minRadius && d.maxRadius<=1000000,"Invalid radius");
            require(Double.isFinite(d.centerX) && Double.isFinite(d.centerZ) && Math.abs(d.centerX)<=28000000 && Math.abs(d.centerZ)<=28000000,"Invalid center");
            require(d.cooldownSeconds>=0 && d.cooldownSeconds<=86400,"Invalid cooldown");
        }
        var p=performance;
        require(p.maxConcurrentRequests>=1 && p.maxConcurrentRequests<=8,"Concurrency must be 1..8");
        require(p.maxAttemptsPerRequest>=1 && p.maxAttemptsPerRequest<=128,"Attempts must be 1..128");
        require(p.requestTimeoutSeconds>=1 && p.requestTimeoutSeconds<=60,"Timeout must be 1..60");
        require(p.maxValidationWorkPerTick>=1 && p.maxValidationWorkPerTick<=16,"Work budget must be 1..16");
        require(p.safeLocationCacheSize>=0 && p.safeLocationCacheSize<=1024,"Cache must be 0..1024");
        require(!p.allowChunkGeneration,"Chunk generation is not supported: use pregenerated terrain");
        require(prices.beastCoin>0 && prices.beastCoin<=1000000 && prices.cobbleDollars>0 && prices.cobbleDollars<=1000000000,"Invalid price");
        require(Set.of("vi","en").contains(language),"Language must be vi or en");
        for(String node:List.of(permissions.use,permissions.resource,permissions.nether,permissions.end,permissions.admin))
            require(node!=null && node.matches("[a-zA-Z0-9_.-]+"),"Invalid permission node");
    }
    static void require(boolean ok,String reason) { if(!ok)throw new IllegalArgumentException(reason); }
    static Config load(Path path)throws IOException {
        if(!Files.exists(path)) { Files.createDirectories(path.getParent());Files.writeString(path,JSON.toJson(new Config())); }
        Config c=JSON.fromJson(Files.readString(path),Config.class);
        require(c!=null,"Empty configuration");c.validate();return c;
    }
}
