package vn.svframe.lively.event;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class StoryArcEngine {
    public enum State { ACTIVE, PAUSED, RESOLVED, ABANDONED }
    public record Arc(UUID id,String seed,String title,int phase,int maxPhase,double tension,State state,List<UUID> events,Map<String,String> facts,Instant updatedAt){
        public Arc{events=List.copyOf(events);facts=Map.copyOf(facts);tension=Math.max(0D,Math.min(1D,tension));}
    }
    private final ConcurrentHashMap<UUID,Arc> arcs=new ConcurrentHashMap<>();
    public Arc start(String seed,String title,int maxPhase,Map<String,String> facts){Arc a=new Arc(UUID.randomUUID(),seed,title,1,Math.max(1,maxPhase),0.2D,State.ACTIVE,List.of(),facts,Instant.now());arcs.put(a.id(),a);return a;}
    public Optional<Arc> attachEvent(UUID arcId,UUID eventId,double tensionDelta){return Optional.ofNullable(arcs.computeIfPresent(arcId,(k,a)->{var ev=new java.util.ArrayList<>(a.events());ev.add(eventId);double t=Math.max(0,Math.min(1,a.tension()+tensionDelta));int p=a.phase();if(t>0.75&&p<a.maxPhase())p++;State s=(p>=a.maxPhase()&&t<0.25)?State.RESOLVED:a.state();return new Arc(a.id(),a.seed(),a.title(),p,a.maxPhase(),t,s,ev,a.facts(),Instant.now());}));}
    public Optional<Arc> state(UUID id,State s){return Optional.ofNullable(arcs.computeIfPresent(id,(k,a)->new Arc(a.id(),a.seed(),a.title(),a.phase(),a.maxPhase(),a.tension(),s,a.events(),a.facts(),Instant.now())));}
    public List<Arc> active(){return arcs.values().stream().filter(a->a.state()==State.ACTIVE).toList();}
    public Map<UUID,Arc> snapshot(){return Map.copyOf(arcs);} public void restore(Map<UUID,Arc> s){arcs.clear();arcs.putAll(s);}
}
