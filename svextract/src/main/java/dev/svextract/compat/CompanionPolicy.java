package dev.svextract.compat;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** One selected UUID, never a clone or a replacement Pokémon record. */
public final class CompanionPolicy {
    private final Map<UUID,UUID> selected=new ConcurrentHashMap<>();
    public void select(UUID owner,List<UUID> party){
        if(party.size()>1)throw new IllegalArgumentException("Extraction permits one Pokémon. Store the others in your Cobblemon PC before deployment.");
        selected.remove(owner);if(!party.isEmpty())selected.put(owner,party.getFirst());
    }
    public boolean allows(UUID owner,UUID pokemon){return pokemon!=null&&pokemon.equals(selected.get(owner));}
    public UUID selected(UUID owner){return selected.get(owner);}
    public void release(UUID owner){selected.remove(owner);}
    public void clear(){selected.clear();}
}
