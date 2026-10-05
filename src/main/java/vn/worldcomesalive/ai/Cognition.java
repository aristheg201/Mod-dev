package vn.worldcomesalive.ai;
import vn.worldcomesalive.model.LivingWorld.*;
import vn.worldcomesalive.data.WorldContent;
import java.util.*;

/** Perception facts -> weighted motives -> GOAP. Schedules contribute utility, never veto danger. */
public final class Cognition {
    public record Decision(String goal,List<String> plan,Map<String,Double> scores) {}
    public static Decision decide(Npc n,long clock,WorldContent data,boolean shopOpen){
        long elapsed=Math.max(0,clock-n.lastCognition);n.lastCognition=clock;
        n.needs.merge("hunger",elapsed/36000.0,Double::sum);n.needs.merge("fatigue",elapsed/48000.0,Double::sum);n.needs.merge("loneliness",elapsed/50000.0,Double::sum);
        int time=(int)(clock%24000);var p=data.professions.getOrDefault(n.profession,data.professions.get("resident"));
        Map<String,Double> utility=new LinkedHashMap<>();
        utility.put("safe",n.interruptUntil>clock&&Set.of("fire","assault","family_danger","crime").contains(n.interrupt)?1000.0:0.0);
        utility.put("rested",n.need("fatigue")*65+(time>13000?50:0));
        utility.put("nourished",n.need("hunger")*100);
        utility.put("worked",!n.workplace.isBlank()&&time>=p.start()&&time<p.end()?55+n.trait("ambition")*15:0.0);
        utility.put("social",12+n.need("loneliness")*55+n.trait("sociability")*12+(time>=10000&&time<=13000?20:0));
        utility.put("trained",n.pokemon.isEmpty()?0.0:n.trait("curiosity")*16);
        String goal=utility.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow().getKey();
        Set<String> facts=new HashSet<>();facts.add("alive");if(n.inventory.getOrDefault("minecraft:bread",0)>0)facts.add("food");if(shopOpen&&n.money>=3)facts.add("shop_open");if(n.profession.equals("farmer")||n.profession.equals("hunter"))facts.add("can_gather");
        return new Decision(goal,GoalPlanner.plan(facts,goal,data.actions),Map.copyOf(utility));
    }
    private Cognition(){}
}
