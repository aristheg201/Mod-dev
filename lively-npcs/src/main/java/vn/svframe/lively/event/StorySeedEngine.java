package vn.svframe.lively.event;

import vn.svframe.lively.actor.ActorId;
import vn.svframe.lively.actor.ActorRegistry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Admin defines seeds/tone. Lively turns world tension into bounded proposals rather than prewriting stories. */
public final class StorySeedEngine {
    public record Seed(String id, WorldEventEngine.Category category, double weight, boolean enabled, Map<String,String> rules) {
        public Seed { Objects.requireNonNull(id); Objects.requireNonNull(category); weight=unit(weight); rules=Map.copyOf(rules); }
    }
    public record AntagonistCandidate(ActorId actor,double score,String reason,Map<String,String> facts) { public AntagonistCandidate { score=unit(score); facts=Map.copyOf(facts); } }
    private final ConcurrentHashMap<String,Seed> seeds=new ConcurrentHashMap<>();
    public void register(Seed seed){seeds.put(seed.id(),seed);} public void remove(String id){seeds.remove(id);} public Map<String,Seed> snapshot(){return Map.copyOf(seeds);}

    public List<WorldEventEngine.EventProposal> propose(Map<String,Double> signals,String structureId,Set<ActorId> actors,int max) {
        List<WorldEventEngine.EventProposal> out=new ArrayList<>();
        for(Seed seed:seeds.values().stream().filter(Seed::enabled).sorted(Comparator.comparingDouble(Seed::weight).reversed()).toList()){
            if(out.size()>=Math.max(0,Math.min(16,max)))break;
            double signal=unit(signals.getOrDefault(seed.id(),signals.getOrDefault(seed.category().name().toLowerCase(java.util.Locale.ROOT),0D)));
            double score=signal*.7D+seed.weight()*.3D;if(score<.35D)continue;
            out.add(new WorldEventEngine.EventProposal(seed.category(),seed.id(),structureId,actors,score,
                    java.time.Duration.ofMinutes(Math.max(10,Math.min(1440,Math.round(30+score*360)))),seed.rules()));
        }return List.copyOf(out);
    }

    public List<AntagonistCandidate> antagonistCandidates(ActorRegistry registry, Set<ActorId> candidates, Map<String,Double> worldSignals) {
        double crisis=unit(worldSignals.getOrDefault("economic_crisis",0D)); double conflict=unit(worldSignals.getOrDefault("faction_conflict",0D));
        return candidates.stream().map(actor->registry.get(actor).map(snapshot->{
            double ambition=unit(snapshot.social("ambition")); double morality=unit(snapshot.social("morality")); double influence=unit(snapshot.social("influence")); double fear=unit(snapshot.social("fear"));
            double score=unit(ambition*.32D+(1D-morality)*.24D+influence*.18D+(1D-fear)*.08D+crisis*.10D+conflict*.08D);
            return new AntagonistCandidate(actor,score,"emergent_antagonist",Map.of("ambition",Double.toString(ambition),"morality",Double.toString(morality)));
        }).orElse(new AntagonistCandidate(actor,0D,"unknown_actor",Map.of()))).filter(c->c.score()>=.55D).sorted(Comparator.comparingDouble(AntagonistCandidate::score).reversed()).toList();
    }
    private static double unit(double v){return Math.max(0D,Math.min(1D,v));}
}
