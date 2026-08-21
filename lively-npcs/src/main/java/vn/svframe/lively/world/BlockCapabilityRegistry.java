package vn.svframe.lively.world;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Semantic capability catalog. Integration maps concrete Minecraft blocks to these capabilities. */
public final class BlockCapabilityRegistry {
    private final ConcurrentHashMap<String, Set<String>> capabilities=new ConcurrentHashMap<>();
    public BlockCapabilityRegistry(){registerDefaults();}
    private void registerDefaults(){
        register("minecraft:chest",Set.of("storage")); register("minecraft:bed",Set.of("sleep"));
        register("minecraft:lectern",Set.of("read","teach")); register("minecraft:furnace",Set.of("smelt"));
        register("minecraft:crafting_table",Set.of("craft")); register("minecraft:bell",Set.of("gather"));
        register("minecraft:door",Set.of("entrance","openable"));
    }
    public void register(String id,Set<String> caps){capabilities.put(id,Set.copyOf(caps));}
    public Set<String> capabilities(String id){return capabilities.getOrDefault(id,Set.of());}
    public Map<String,Set<String>> snapshot(){return Map.copyOf(capabilities);}
}
