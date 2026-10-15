import dev.svextract.match.BattleRoyaleMatch;
import dev.svextract.match.VersusMatch;
import dev.svextract.mission.ExtractorBotBrain;
import dev.svextract.mission.RaidPatrols;
import dev.svextract.mission.ObjectiveGraph;
import dev.svextract.config.WeightedLoot;
import java.util.*;

/** Runnable without external dependencies when Gradle repositories cannot be accessed. */
public final class CoreEngineSmoke {
    static void check(boolean value,String msg){if(!value)throw new AssertionError(msg);}
    public static void main(String[] args){
        int checked=0;
        var rules=BattleRoyaleMatch.Rules.defaults();
        for(int iteration=0;iteration<128;iteration++){
            List<BattleRoyaleMatch.Participant> roster=new ArrayList<>();
            for(int i=0;i<16;i++)roster.add(new BattleRoyaleMatch.Participant(UUID.nameUUIDFromBytes((iteration+":"+i).getBytes()),i/4,i!=0));
            var match=new BattleRoyaleMatch(rules,roster,0);match.tick(301);
            check(match.phase()==BattleRoyaleMatch.Phase.ACTIVE,"BR start");
            check(match.shouldApplyZoneDamage(320),"BR first damage interval");
            check(!match.shouldApplyZoneDamage(320),"BR double damage guard");
            check(match.zoneDamage(20000,20000,320)>0,"BR far zone damage");
            check(match.zoneDamage(0,0,320)==0,"BR center safe");
            for(int i=4;i<16;i++)check(match.eliminate(roster.get(i).id(),roster.get(0).id(),321+i),"BR elimination");
            check(match.phase()==BattleRoyaleMatch.Phase.FINISHED&&match.winner()==0,"BR winner");
            checked++;
        }
        for(int iteration=0;iteration<128;iteration++){
            List<VersusMatch.Seat> roster=new ArrayList<>();
            for(int i=0;i<10;i++)roster.add(new VersusMatch.Seat(UUID.nameUUIDFromBytes((iteration+"-"+i).getBytes()),i/5,i!=0));
            var rulesTdm=new VersusMatch.Rules(VersusMatch.Mode.TEAM_DEATHMATCH,5,4,300,1,1,20);
            var match=new VersusMatch(rulesTdm,roster,0);match.tick(20);
            for(int i=0;i<4;i++){
                long now=21+i*21;
                check(match.elimination(roster.get(5).player(),roster.get(0).player(),now),"TDM hit");
                check(!match.elimination(roster.get(5).player(),roster.get(0).player(),now),"TDM double kill rejected");
            }
            check(match.winner()==0&&match.score(0)==4,"TDM winner");
            var dom=new VersusMatch(VersusMatch.Rules.domination(),roster,0);dom.tick(200);
            check(dom.capture(0,0)&&dom.capture(1,0)&&dom.capture(2,1),"Dom capture");
            dom.tick(1200);
            check(dom.score(0)>=dom.score(1),"Dom scoring");
            checked++;
        }
        for(int iteration=0;iteration<128;iteration++){
            var brain=new ExtractorBotBrain(10,100);
            var encounter=new ExtractorBotBrain.Context(.9,10,30,true,false,true,true,0,800,true,false,false);
            check(brain.decide(encounter,0).goal()==ExtractorBotBrain.Goal.ENGAGE,"Bot engage");
            var loot=new ExtractorBotBrain.Context(.9,10,30,false,false,true,true,150,400,true,false,false);
            check(brain.decide(loot,10).goal()==ExtractorBotBrain.Goal.EXTRACT,"Bot extract decision");
            var escape=new ExtractorBotBrain.Context(.9,10,30,false,false,true,false,150,300,true,true,false);
            check(brain.decide(escape,20).goal()==ExtractorBotBrain.Goal.EVACUATED,"Bot evacuation");
            checked++;
        }
        for(int iteration=0;iteration<128;iteration++){
            UUID raid=UUID.nameUUIDFromBytes(("loot-"+iteration).getBytes());
            var entries=List.of(new WeightedLoot.Entry("minecraft:iron_ingot",4,9),
                new WeightedLoot.Entry("minecraft:diamond",1,1));
            var first=WeightedLoot.choose(raid,"foundry_safe",entries);
            check(first.equals(WeightedLoot.choose(raid,"foundry_safe",entries)),"Loot roll must be deterministic for the raid/cache");
            check(entries.contains(first),"Loot roll must come from an actual configured entry");
            checked++;
        }
        for(String map:List.of("blackglass_industrial","redline_station")){
            var spawn=RaidPatrols.spawnPoints(map);
            check(spawn.size()>=12,"Patrol map has too few distributed spawn locations");
            for(var route:RaidPatrols.routes(map))for(var waypoint:route.points()){
                var next=RaidPatrols.next(map,waypoint.x(),waypoint.z());
                check(next!=null,"No next patrol waypoint");
                check(Math.abs(next.x())<=50&&Math.abs(next.z())<=50,"Patrol waypoint escaped the map");
                checked++;
            }
        }
        var contract=new ObjectiveGraph(ObjectiveGraph.blackglassOperations());
        check(contract.nodes().size()==8,"Operations must have several independent contracts");
        check(contract.available("evacuation"),"Early extraction must be allowed in Operations");
        check(!contract.available("data_core"),"Research evidence still requires investigation");
        for(String independent:List.of("power","security_manifest","relay_intelligence","clinic_records","substation_logs")){
            check(contract.complete(independent),"Optional independent contract should be completable: "+independent);
            checked++;
        }
        check(contract.complete("research_train")&&contract.complete("data_core"),"Research dependency must work");
        System.out.println("PASS: "+checked+" pure-Java rules, weighted loot, patrol, Operations contracts scenarios");
    }
}
