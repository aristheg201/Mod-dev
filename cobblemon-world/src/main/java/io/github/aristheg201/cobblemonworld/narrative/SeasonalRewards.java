package io.github.aristheg201.cobblemonworld.narrative;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import io.github.aristheg201.cobblemonworld.progression.ProgressionStore;
import net.minecraft.server.level.ServerPlayer;
import java.util.UUID;

/** Two independent one-time seasonal entitlements. No player commands or free introductory rewards. */
public final class SeasonalRewards {
    public static final String SEASON="weather_duo_01";
    public static boolean claim(ServerPlayer p,String species){

        var state=NarrativeEngine.state(p);var n=state.narrative;
        NarrativeRegistry.Season season=null;NarrativeRegistry.Reward reward=null;
        for(var candidate:NarrativeRegistry.INSTANCE.data.seasons())for(var entitlement:candidate.rewards())if(entitlement.id().equals(species)){season=candidate;reward=entitlement;}
        if(season==null || reward==null || (!n.chains.containsKey(season.chain()) && !n.completedChains.contains(season.chain())))return false;
        for(String required:season.requirements())if(!state.storyFlags.contains(required))return false;
        if(!n.finished.contains("battle:"+reward.battle()))return false;
        String key=season.id()+":"+reward.id();var previous=n.claims.get(key);
        if(previous!=null && previous.status().equals("delivered"))return true;
        UUID id=previous==null?UUID.randomUUID():UUID.fromString(previous.pokemonUuid());
        var storage=Cobblemon.INSTANCE.getStorage();
        for(var store:storage.getParties(p.getUUID(),p.registryAccess()))if(store.get(id)!=null){n.claims.put(key,new NarrativeState.Claim(id.toString(),"delivered"));ProgressionStore.INSTANCE.save();return true;}
        for(var store:storage.getPCs(p.getUUID(),p.registryAccess()))if(store.get(id)!=null){n.claims.put(key,new NarrativeState.Claim(id.toString(),"delivered"));ProgressionStore.INSTANCE.save();return true;}
        n.claims.put(key,new NarrativeState.Claim(id.toString(),"reserved"));
        if(!ProgressionStore.INSTANCE.saveChecked())return false;
        var pokemon=PokemonProperties.Companion.parse(reward.properties()).create(p);
        if(!pokemon.getSpecies().getName().equalsIgnoreCase(reward.species()))return false;
        pokemon.setUuid(id);for(int i=0;i<6;i++)pokemon.setIV(CompetitiveTeams.STATS[i],reward.ivs()[i]);pokemon.heal();
        var party=storage.getParty(p);boolean full=0==java.util.stream.IntStream.range(0,6).filter(i->party.get(i)==null).count();
        boolean granted=full?storage.getPC(p).add(pokemon):party.add(pokemon);
        if(!granted)return false;
        // A delivered entitlement is never recreated merely because its Pokemon was traded/released.
        n.claims.put(key,new NarrativeState.Claim(id.toString(),"delivered"));ProgressionStore.INSTANCE.save();
        io.github.aristheg201.cobblemonworld.CobblemonWorldMod.LOGGER.info("Season reward player={} season={} species={} uuid={} storage={}",p.getUUID(),season.id(),reward.species(),id,full?"PC":"party");return true;
    }
}
