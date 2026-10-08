package io.github.aristheg201.cobblemonworld.narrative;

import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.api.pokemon.Natures;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.api.abilities.Abilities;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import java.util.*;

public final class CompetitiveTeams {
    public static final Stats[] STATS={Stats.HP,Stats.ATTACK,Stats.DEFENCE,Stats.SPECIAL_ATTACK,Stats.SPECIAL_DEFENCE,Stats.SPEED};
    public static Pokemon create(ServerPlayer owner,NarrativeRegistry.Member m){
        if(Natures.getNature(m.nature())==null || Abilities.get(m.ability())==null)throw new IllegalStateException("Invalid nature/ability for "+m.species());
        for(String move:m.moves())if(Moves.getByName(move)==null)throw new IllegalStateException("Unknown move "+move+" for "+m.species());
        if(m.moves().length!=4 || m.evs()==null || m.evs().length!=6 || Arrays.stream(m.evs()).sum()>510)throw new IllegalStateException("Invalid trainer spread "+m.species());
        Pokemon p=PokemonProperties.Companion.parse(m.species()+(m.properties()==null?"":" "+m.properties())+" level="+m.level()+" nature="+m.nature()+" ability="+m.ability()+" moves="+String.join(",",m.moves())).create(owner);
        if(!p.getSpecies().getName().equalsIgnoreCase(m.species()))throw new IllegalStateException("Species did not resolve "+m.species());
        boolean legalAbility = false; for (var candidate : p.getForm().getAbilities()) if (candidate.getTemplate().equals(Abilities.get(m.ability()))) legalAbility = true;
        if (!legalAbility || !p.getAbility().getName().equals(m.ability())) throw new IllegalStateException("Illegal authored ability " + m.species() + ":" + m.ability());
        for (String move : m.moves()) if (!p.getForm().getMoves().getAllLegalMoves().contains(Moves.getByName(move))) throw new IllegalStateException("Illegal authored move " + m.species() + ":" + move);
        for(int i=0;i<6;i++){
            int iv=m.ivOverrides()==null?31:m.ivOverrides().getOrDefault(STATS[i].getShowdownId(),31);
            if(iv<0 || iv>31 || m.evs()[i]<0 || m.evs()[i]>252)throw new IllegalStateException("Illegal IV/EV "+m.species());
            p.setIV(STATS[i],iv);p.setEV(STATS[i],m.evs()[i]);
        }
        if(m.item()!=null && !m.item().isBlank()){
            var id=ResourceLocation.parse(m.item());if(!BuiltInRegistries.ITEM.containsKey(id))throw new IllegalStateException("Trainer item unavailable "+id);
            p.swapHeldItem(new ItemStack(BuiltInRegistries.ITEM.get(id)),false,false);
        }
        p.heal();return p;
    }
}
