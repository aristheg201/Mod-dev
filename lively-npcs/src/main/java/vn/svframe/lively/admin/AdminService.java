package vn.svframe.lively.admin;

import vn.svframe.lively.actor.ActorId;
import vn.svframe.lively.api.LivelyApi;
import vn.svframe.lively.event.WorldEventEngine;
import vn.svframe.lively.world.SemanticStructureRegistry;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Command-agnostic admin surface. Fabric/other integrations only parse commands and call these validated methods. */
public final class AdminService {
    public Map<String,Object> status(){return Map.of("actors",LivelyApi.actors().snapshot().actors().size(),"structures",LivelyApi.structures().snapshot().structures().size(),"events",LivelyApi.events().activeEvents().size(),"quests",LivelyApi.quests().snapshot().quests().size(),"metrics",LivelyApi.profiler().snapshot().size());}
    public SemanticStructureRegistry.Structure createStructure(String id,String type,SemanticStructureRegistry.Bounds bounds,Set<String> capabilities,Map<String,String> points,String parent,String town){return LivelyApi.structures().register(new SemanticStructureRegistry.Structure(id,type,bounds,capabilities,points,parent,town,SemanticStructureRegistry.OperationalState.OPEN,0));}
    public Optional<WorldEventEngine.WorldEvent> startEvent(WorldEventEngine.Category category,String seed,String structure,Set<ActorId> participants,double intensity,Duration duration,Map<String,String> facts){return LivelyApi.events().start(new WorldEventEngine.EventProposal(category,seed,structure,participants,intensity,duration,facts));}
    public Map<String,?> performance(){return LivelyApi.profiler().snapshot();}
}
