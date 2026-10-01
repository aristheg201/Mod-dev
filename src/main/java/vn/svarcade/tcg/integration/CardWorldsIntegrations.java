package vn.svarcade.tcg.integration;

import net.fabricmc.loader.api.FabricLoader;
import java.util.*;
import java.nio.file.Path;

/** Optional providers are detected once. Only guarded adapters load their APIs. */
public final class CardWorldsIntegrations {
    private static final IntegrationCapabilities CAPABILITIES=new IntegrationCapabilities(FabricLoader.getInstance().getAllMods().stream().map(m->m.getMetadata().getId()).collect(java.util.stream.Collectors.toSet()));
    private static volatile ProviderAspects aspects;
    private static boolean logged;
    public static IntegrationCapabilities capabilities(){return CAPABILITIES;}
    public static synchronized ProviderAspects aspects(){
        if(aspects==null){Map<String,List<Path>> roots=new LinkedHashMap<>();
            FabricLoader.getInstance().getAllMods().forEach(mod->{try{roots.put(mod.getMetadata().getId(),mod.getRootPaths());}catch(UnsupportedOperationException ignored){/* Built-in providers have no asset roots. */}});
            aspects=ProviderAspects.discover(roots);
        }return aspects;
    }
    public static void reload(){synchronized(CardWorldsIntegrations.class){aspects=null;}CobblemonBridge.invalidate();}
    public static synchronized void initialize(){if(logged)return;logged=true;
        var log=org.slf4j.LoggerFactory.getLogger("cardworlds-integrations");
        for(String id:List.of("cobblemon","mega_showdown","placeholder-api","beconomy","cobbledollars","impactor"))
            log.info("CARDWORLDS_INTEGRATION provider={} available={}",id,CAPABILITIES.has(id));
    }
    private CardWorldsIntegrations(){}
}
