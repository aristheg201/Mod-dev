package vn.worldcomesalive.server;
import vn.worldcomesalive.model.LivingWorld;
import vn.worldcomesalive.model.LivingWorld.*;
import vn.worldcomesalive.data.WorldContent;
import java.util.*;

/** Same directed relationship model serves NPCs, players, household formation and gossip. */
public final class SocialRules {
    public static String dialogue(Npc n,UUID player,String playerName,String topic,Settlement s,LivingWorld w,WorldContent data,boolean rain){
        var relation=n.relationship(player);String season=List.of("spring","summer","autumn","winter").get((int)(w.clock/24000/24)%4);
        var candidates=data.candidates(topic,n.profession).stream().filter(d->(d.activity().equals("*")||d.activity().equals(n.activity))&&relation.trust>=d.minTrust()&&(d.weather().equals("*")||d.weather().equals(rain?"rain":"clear"))&&(d.season().equals("*")||d.season().equals(season))&&(d.memory().isBlank()||n.knowledge.values().stream().anyMatch(k->k.fact().equals(d.memory())&&player.equals(k.actor())))).toList();
        String text=candidates.isEmpty()?"There is much to do today.":candidates.get((int)Math.floorMod(w.clock/600+n.id.hashCode(),candidates.size())).text();
        return text.replace("{name}",n.name).replace("{player}",playerName).replace("{settlement}",s.name).replace("{faction}",s.faction.replace('_',' ')).replace("{profession}",n.profession).replace("{activity}",n.activity).replace("{home}","the "+s.buildings.get(n.home).type);
    }
    public static double gift(Npc n,UUID actor,String item,Set<String> tags,LivingWorld w,WorldContent data){
        int repeats=n.repeatedGifts.getOrDefault(item,0);double value=-1;
        for(var rule:data.gifts)if(tags.contains(rule.tag())&&(rule.profession().equals("*")||rule.profession().equals(n.profession)))value=Math.max(value,rule.value()*(.5+n.trait("kindness"))+(rule.need().isBlank()?0:n.need(rule.need())*3));
        var domestic=vn.worldcomesalive.domestic.DomesticContent.active.good(item);if(domestic!=null){if(n.preferences.getOrDefault(item,.5)<0)return -1;value*=.6+n.preferences.getOrDefault(item,.5);}
        value+=n.trait("curiosity")-n.trait("greed");if((w.clock/24000)%96==n.birthday)value+=3;
        if(n.lastGiftDay==w.clock/24000)value-=5;value-=Math.min(8,repeats*1.5);
        var r=n.relationship(actor);if(r.fear>30||r.trust< -20)value=-5;
        if(value>0){r.friendship+=value;r.trust+=Math.min(1,value/5);r.familiarity+=1;n.inventory.merge(item,1,Integer::sum);n.repeatedGifts.merge(item,1,Integer::sum);n.lastGiftDay=w.clock/24000;}
        w.remember(n,new Memory("gift",actor,n.home,w.clock,Math.min(.9,Math.abs(value)/10),value,1,"witnessed"));r.clamp();return value;
    }
    public static boolean acceptsCards(Npc n,UUID player,long time,long stakes){var r=n.relationship(player);return !n.decks.isEmpty()&&!n.cardArchetype.equals("none")&&n.age>=18&&n.interruptUntil<=time&&n.need("hunger")<.9&&n.need("fatigue")<.9&&r.fear<35&&r.trust> -25&&stakes<=n.money&&(!n.activity.equals("working")||n.profession.equals("innkeeper"))&&(stakes==0||n.trait("risk_tolerance")>.4);}
    public static void socialize(LivingWorld w,Npc a,Npc b){
        Relationship ar=a.relationship(b.id),br=b.relationship(a.id);if(w.clock-ar.lastInteraction<1200)return;
        double harmony=1-Math.abs(a.trait("honesty")-b.trait("honesty"));double change=harmony*2-(a.trait("aggression")+b.trait("aggression"))*.7;
        ar.friendship+=change;ar.trust+=change*.25;ar.familiarity++;ar.lastInteraction=w.clock;br.friendship+=change*.8;br.familiarity++;br.lastInteraction=w.clock;ar.clamp();br.clamp();
        if(a.age>=18&&b.age>=18&&!a.household.equals(b.household)&&ar.friendship>45&&br.friendship>40&&ar.trust>15){ar.attraction+=a.trait("sociability");br.attraction+=b.trait("sociability");ar.stage=ar.attraction>30?"dating":"friend";br.stage=br.attraction>30?"dating":"friend";}
        gossip(a,b,w.clock);gossip(b,a,w.clock);a.version++;b.version++;
    }
    private static void gossip(Npc source,Npc receiver,long clock){source.knowledge.values().stream().filter(k->k.confidence()>.6).sorted(Comparator.comparingDouble(Belief::confidence).reversed()).limit(1).forEach(k->{String key=k.fact()+":"+k.actor();if(!receiver.knowledge.containsKey(key))receiver.knowledge.put(key,new Belief(k.fact(),k.actor(),k.confidence()*(.6+receiver.trait("loyalty")*.25),"heard from "+source.id,clock,1));});}
    private SocialRules(){}
}
