package io.github.aristheg201.cobblemonworld.qa;

import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.entity.npc.NPCBattleActor;
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor;
import io.github.aristheg201.cobblemonworld.npc.CWorldNpcInteraction;
import io.github.aristheg201.cobblemonworld.narrative.*;
import net.minecraft.core.registries.BuiltInRegistries;
import java.util.*;
import java.nio.file.*;

/** Inspect the native battle's actual Pokémon copies, before the first move. Opt-in QA only. */
public final class LiveTrainerAudit {
    public static void register(){CobblemonEvents.BATTLE_STARTED_POST.subscribe(Priority.LOWEST,event->{
        for(var actor:event.getBattle().getActors())if(actor instanceof NPCBattleActor npc && npc.getNpc().getInteraction() instanceof CWorldNpcInteraction interaction){
            var player=java.util.stream.StreamSupport.stream(event.getBattle().getActors().spliterator(),false).filter(a->a instanceof PlayerBattleActor).map(a->((PlayerBattleActor)a).getEntity()).filter(Objects::nonNull).findFirst().orElseThrow();
            String id=interaction.definitionId();
            if(NarrativeEngine.state(player).storyFlags.contains("main_story_complete") && NarrativeRegistry.INSTANCE.teams.containsKey(id+"_postgame"))id+="_postgame";
            var team=NarrativeRegistry.INSTANCE.teams.get(id);if(team==null || team.members().length!=npc.getPokemonList().size())throw new IllegalStateException("Live roster count mismatch "+id);
            var rows=new ArrayList<Map<String,Object>>();
            for(int i=0;i<team.members().length;i++){
                var authored=team.members()[i];var actual=npc.getPokemonList().get(i).getEffectedPokemon();
                if(!actual.getSpecies().getName().equalsIgnoreCase(authored.species()) || !actual.getNature().getName().getPath().equals(authored.nature()) || !actual.getAbility().getName().equals(authored.ability()))throw new IllegalStateException("Live species/nature/ability mismatch "+id);
                var ivs=new ArrayList<Integer>();var evs=new ArrayList<Integer>();
                for(int stat=0;stat<6;stat++){
                    int iv=actual.getIvs().get(CompetitiveTeams.STATS[stat]),ev=actual.getEvs().get(CompetitiveTeams.STATS[stat]);
                    if(iv!=(authored.ivOverrides()==null?31:authored.ivOverrides().getOrDefault(CompetitiveTeams.STATS[stat].getShowdownId(),31)) || ev!=authored.evs()[stat])throw new IllegalStateException("Live IV/EV mismatch "+id);
                    ivs.add(iv);evs.add(ev);
                }
                var moves=actual.getMoveSet().getMoves().stream().map(m->m.getName()).toList();
                if(!new HashSet<>(moves).equals(new HashSet<>(List.of(authored.moves()))))throw new IllegalStateException("Live moves mismatch "+id+" "+actual.getSpecies().getName()+" "+moves);
                String item=BuiltInRegistries.ITEM.getKey(actual.getHeldItem$common().getItem()).toString();
                if(!item.equals(authored.item()))throw new IllegalStateException("Live held item mismatch "+id+" "+item);
                rows.add(Map.of("species",actual.getSpecies().getName(),"level",actual.getLevel(),"ivs",ivs,"evs",evs,"nature",authored.nature(),"ability",actual.getAbility().getName(),"moves",moves,"item",item));
            }
            try{Files.writeString(Path.of("live-trainer-teams.jsonl"),new com.google.gson.Gson().toJson(Map.of("trainer",id,"battle",event.getBattle().getBattleId().toString(),"skill",npc.getNpc().getSkill(),"members",rows))+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}catch(Exception e){throw new IllegalStateException("Cannot preserve live trainer QA evidence",e);}
            System.out.println("CWORLD_NARRATIVE_QA_LIVE_STATS_VERIFIED trainer="+id+" members="+rows.size());
        }
        return kotlin.Unit.INSTANCE;
    });}
}
