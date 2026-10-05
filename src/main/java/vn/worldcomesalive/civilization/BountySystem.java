package vn.worldcomesalive.civilization;

import vn.worldcomesalive.model.LivingWorld;
import vn.worldcomesalive.model.LivingWorld.*;
import vn.worldcomesalive.world.SettlementBootstrap;
import java.util.*;

/** Witness-driven crime and persistent bounty records. No omniscient tracking is stored here. */
public final class BountySystem {
    public static final class Crime {
        public UUID id,actor,victim;
        public String settlement,type,status="REPORTED";
        public long time;
        public double severity;
        public Set<UUID> witnesses=new LinkedHashSet<>();
    }
    public static final class Bounty {
        public UUID id,target;
        public String jurisdiction,status="WANTED",requirement="DEAD_OR_ALIVE";
        public List<UUID> crimes=new ArrayList<>();
        public long reward;
        public Pos lastKnown;
        public long lastSeen;
        public Set<UUID> knownAssociates=new LinkedHashSet<>();
    }

    public static Crime report(LivingWorld world,String settlement,UUID actor,UUID victim,String type,double severity,UUID witness,Pos where){
        if(actor==null||settlement==null)return null;
        Crime crime=new Crime();
        crime.id=SettlementBootstrap.uuid("crime:"+settlement+":"+actor+":"+type+":"+world.clock+":"+String.valueOf(victim));
        crime.actor=actor;crime.victim=victim;crime.settlement=settlement;crime.type=type;crime.severity=Math.max(0,Math.min(1,severity));crime.time=world.clock;
        if(witness!=null)crime.witnesses.add(witness);
        world.civilization.crimes.putIfAbsent(crime.id,crime);

        String key=settlement+":"+actor;
        Bounty bounty=world.civilization.bounties.values().stream().filter(b->b.jurisdiction.equals(settlement)&&b.target.equals(actor)&&Set.of("WANTED","FUGITIVE").contains(b.status)).findFirst().orElse(null);
        if(bounty==null){
            bounty=new Bounty();bounty.id=SettlementBootstrap.uuid("bounty:"+key);bounty.target=actor;bounty.jurisdiction=settlement;
            world.civilization.bounties.put(bounty.id,bounty);
        }
        if(!bounty.crimes.contains(crime.id))bounty.crimes.add(crime.id);
        bounty.reward+=Math.max(5,Math.round(60*crime.severity));
        bounty.lastKnown=where;bounty.lastSeen=world.clock;
        if(crime.severity>=.9)bounty.requirement="DEAD_OR_ALIVE";
        else bounty.requirement="ALIVE_ONLY";

        PlayerLife player=world.players.get(actor);
        if(player!=null)player.bounty.merge(settlement,Math.max(1,Math.round(60*crime.severity)),Long::sum);
        world.revision++;
        return crime;
    }

    public static void witnessAgain(LivingWorld world,UUID bountyId,UUID witness,Pos location){
        Bounty bounty=world.civilization.bounties.get(bountyId);
        if(bounty==null||!Set.of("WANTED","FUGITIVE").contains(bounty.status))return;
        bounty.lastKnown=location;bounty.lastSeen=world.clock;
        if(witness!=null){Npc npc=world.npcs.get(witness);if(npc!=null)world.remember(npc,new Memory("saw_wanted_target",bounty.target,bounty.jurisdiction,world.clock,.75,-.35,1,"witnessed"));}
    }

    public static long captured(LivingWorld world,UUID bountyId,UUID hunter){
        Bounty bounty=world.civilization.bounties.get(bountyId);
        if(bounty==null||!Set.of("WANTED","FUGITIVE").contains(bounty.status))return 0;
        bounty.status="CAPTURED";
        for(UUID crimeId:bounty.crimes){Crime c=world.civilization.crimes.get(crimeId);if(c!=null)c.status="RESOLVED";}
        PlayerLife life=world.players.computeIfAbsent(hunter,k->new PlayerLife());life.money+=bounty.reward;life.skills.merge("bounty_hunting",.02,Double::sum);
        world.transact(bounty.jurisdiction+":treasury",hunter.toString(),"bounty",1,bounty.reward,"wanted target captured");
        return bounty.reward;
    }

    public static long killed(LivingWorld world,UUID bountyId,UUID hunter){
        Bounty bounty=world.civilization.bounties.get(bountyId);
        if(bounty==null||!Set.of("WANTED","FUGITIVE").contains(bounty.status)||bounty.requirement.equals("ALIVE_ONLY"))return 0;
        bounty.status="DEAD";
        for(UUID crimeId:bounty.crimes){Crime c=world.civilization.crimes.get(crimeId);if(c!=null)c.status="RESOLVED";}
        PlayerLife life=world.players.computeIfAbsent(hunter,k->new PlayerLife());life.money+=bounty.reward;life.skills.merge("bounty_hunting",.012,Double::sum);
        world.transact(bounty.jurisdiction+":treasury",hunter.toString(),"bounty",1,bounty.reward,"wanted target killed");
        return bounty.reward;
    }

    private BountySystem(){}
}
