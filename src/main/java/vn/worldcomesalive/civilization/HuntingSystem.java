package vn.worldcomesalive.civilization;

import vn.worldcomesalive.model.LivingWorld;
import vn.worldcomesalive.model.LivingWorld.*;
import vn.worldcomesalive.world.SettlementBootstrap;
import java.util.*;

/** Systemic hunting pressure created by actual livestock predation or dangerous wildlife incidents. */
public final class HuntingSystem {
    public static final class Target {
        public UUID id,entity;
        public String settlement,species,type="PREDATOR_CONTROL",status="ACTIVE";
        public Pos lastKnown;
        public long reported,lastSeen,reward;
        public int livestockLosses=1;
    }

    public static Target reportPredation(LivingWorld world,String settlement,UUID entity,String species,Pos where){
        if(settlement==null||entity==null)return null;
        Target existing=world.civilization.huntingTargets.values().stream().filter(t->t.entity.equals(entity)&&t.status.equals("ACTIVE")).findFirst().orElse(null);
        if(existing!=null){existing.livestockLosses++;existing.lastKnown=where;existing.lastSeen=world.clock;existing.reward+=8;return existing;}
        Target target=new Target();
        target.id=SettlementBootstrap.uuid("hunt:"+settlement+":"+entity);
        target.entity=entity;target.settlement=settlement;target.species=species;target.lastKnown=where;target.reported=world.clock;target.lastSeen=world.clock;
        target.reward=18;
        world.civilization.huntingTargets.put(target.id,target);
        world.revision++;
        return target;
    }

    public static Optional<Target> activeForEntity(LivingWorld world,UUID entity){return world.civilization.huntingTargets.values().stream().filter(t->t.entity.equals(entity)&&t.status.equals("ACTIVE")).findFirst();}

    public static CivilizationState.Contract ensureContract(LivingWorld world,Target target){
        for(var c:world.civilization.contracts.values())if(c.type.equals("PREDATOR_CONTROL")&&target.id.equals(c.targetEntity)&&!Set.of("COMPLETED","FAILED","EXPIRED").contains(c.status))return c;
        Settlement settlement=world.settlements.get(target.settlement);if(settlement==null)return null;
        Npc requester=settlement.residents.stream().map(world.npcs::get).filter(Objects::nonNull).filter(n->!n.lifeStage.equals("deceased")).filter(n->Set.of("farmer","hunter","leader").contains(n.profession)||n.roles.contains("Leader")).findFirst().orElse(null);
        if(requester==null)return null;
        var gov=world.civilization.governments.get(settlement.id);long reward=target.reward;if(gov!=null){reward=Math.min(Math.max(0,gov.treasury),reward);gov.treasury-=reward;}if(reward<=0)return null;
        CivilizationState.Contract c=new CivilizationState.Contract();
        c.id=SettlementBootstrap.uuid("hunt-contract:"+target.id);
        c.requester=requester.id;c.settlement=settlement.id;c.type="PREDATOR_CONTROL";c.title="Hunt dangerous "+display(target.species);c.description="A "+display(target.species)+" has attacked settlement livestock. Track it and eliminate the threat.";c.knownInformation="Last confirmed by local livestock loss. Position is only last-known, not live tracking.";c.locationQuality="LAST_KNOWN";c.target=target.lastKnown;c.targetEntity=target.id;c.required=1;c.reward=reward;c.deadline=world.clock+72000;
        world.civilization.contracts.put(c.id,c);world.transact(settlement.id+":treasury",c.id.toString(),"hunt reward escrow",1,reward,"predator control contract");
        world.remember(requester,new Memory("livestock_predation",target.entity,settlement.id,world.clock,.82,-.55,1,"witnessed aftermath"));
        return c;
    }

    public static long killed(LivingWorld world,UUID entity,UUID hunter){
        Target target=activeForEntity(world,entity).orElse(null);if(target==null)return 0;target.status="KILLED";long paid=0;
        for(var c:world.civilization.contracts.values())if(c.type.equals("PREDATOR_CONTROL")&&target.id.equals(c.targetEntity)&&c.status.equals("ACTIVE")&&Objects.equals(c.assignee,hunter)){c.status="COMPLETED";c.progress=1;PlayerLife life=world.players.computeIfAbsent(hunter,k->new PlayerLife());life.money+=c.reward;life.skills.merge("hunting",.02,Double::sum);world.transact(c.id.toString(),hunter.toString(),target.species,1,c.reward,"predator control completed");paid+=c.reward;}
        return paid;
    }

    private static String display(String id){int colon=id.indexOf(':');return (colon>=0?id.substring(colon+1):id).replace('_',' ');}
    private HuntingSystem(){}
}
