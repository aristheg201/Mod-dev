package vn.svarcade.tcg.integration;

import java.util.*;

/** Immutable provider detection; contains no optional or client classes. */
public record IntegrationCapabilities(Set<String> mods) {
    public IntegrationCapabilities { mods=Set.copyOf(mods); }
    public boolean has(String id) { return mods.contains(id); }
    public Set<String> capabilities() {
        Set<String> result=new TreeSet<>();
        if(has("cobblemon"))result.addAll(List.of("COBBLEMON_SPECIES","COBBLEMON_FORMS","COBBLEMON_ASPECTS","COBBLEMON_MODELS","COBBLEMON_ANIMATIONS","COBBLEMON_PARTICLES","COBBLEMON_LOCALIZED_NAMES"));
        if(has("mega_showdown"))result.add("MEGA_SHOWDOWN");
        if(has("placeholder-api"))result.add("PLACEHOLDERS");
        for(String id:List.of("beconomy","cobbledollars","impactor"))if(has(id))result.add(id.toUpperCase(Locale.ROOT));
        return Set.copyOf(result);
    }
}
