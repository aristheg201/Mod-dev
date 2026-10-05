package vn.worldcomesalive.civilization;

import vn.worldcomesalive.model.LivingWorld;
import vn.worldcomesalive.model.LivingWorld.*;
import vn.worldcomesalive.world.SettlementBootstrap;
import java.util.*;

/** Turns real simulation pressure into player-facing work; it never invents a disconnected objective. */
public final class ContractDirector {
    public static void tick(LivingWorld world){
        if(world.clock%1200!=0)return;
        TradeNetwork.refresh(world);
        for(var route:world.civilization.tradeRoutes.values())TradeNetwork.opportunity(world,route).ifPresent(t->supply(world,route,t));for(var target:world.civilization.huntingTargets.values())if(target.status.equals("ACTIVE"))HuntingSystem.ensureContract(world,target);for(var settlement:world.settlements.values())ensureDungeonSecurity(world,settlement);
    }

    private static CivilizationState.Contract supply(LivingWorld world,TradeNetwork.Route route,TradeNetwork.Transfer transfer){
        if(world.civilization.contracts.values().stream().anyMatch(c->c.route.equals(route.id)&&c.item.equals(transfer.item())&&!Set.of("COMPLETED","EXPIRED","FAILED").contains(c.status)))return null;
        Settlement destination=world.settlements.get(transfer.destination()),origin=world.settlements.get(transfer.origin());
        if(destination==null||origin==null)return null;
        Building depot=destination.service("trading_post");
        if(depot==null||depot.money<12)return null;
        Npc requester=destination.residents.stream().map(world.npcs::get).filter(Objects::nonNull).filter(n->!n.lifeStage.equals("deceased")).filter(n->n.workplace.equals(depot.id)||n.profession.equals("merchant")).findFirst().orElse(null);
        if(requester==null)return null;

        long reward=Math.min(depot.money,Math.max(12,transfer.quantity()*3L+(long)Math.ceil(route.distance/180.0)));
        CivilizationState.Contract c=new CivilizationState.Contract();
        c.id=SettlementBootstrap.uuid("route-supply:"+route.id+":"+transfer.item()+":"+(world.clock/24000));
        if(world.civilization.contracts.containsKey(c.id))return world.civilization.contracts.get(c.id);
        c.requester=requester.id;c.business=depot.id;c.settlement=destination.id;c.type="INTERSETTLEMENT_SUPPLY";
        c.route=route.id;c.origin=origin.id;c.destination=destination.id;c.item=transfer.item();c.required=transfer.quantity();c.reward=reward;
        c.title="Supply "+destination.name;
        c.description=destination.name+" is short of "+display(transfer.item())+". Bring "+transfer.quantity()+" from the regional trade network to "+depot.type.replace('_',' ')+".";
        c.knownInformation=origin.name+" currently has a recorded surplus. Route danger is "+Math.round(route.danger)+"/100; this information comes from Trading Post stock records, not omniscient tracking.";
        c.locationQuality="EXACT";c.target=depot.point(Marker.CUSTOMER_POINT);c.deadline=world.clock+72000;
        depot.money-=reward;world.transact(depot.id,c.id.toString(),"reward escrow",1,reward,"inter-settlement supply contract");
        world.civilization.contracts.put(c.id,c);
        world.remember(requester,new Memory("regional_supply_shortage",null,depot.id,world.clock,.72,-.15,1,"trading post ledger"));
        return c;
    }


    private static CivilizationState.Contract ensureDungeonSecurity(LivingWorld world,Settlement settlement){var dungeon=settlement.generationPlan==null?null:settlement.generationPlan.dungeon;if(dungeon==null||!dungeon.faction.equals("BANDITS")||Set.of("CLEARED","ABANDONED").contains(dungeon.state))return null;for(var c:world.civilization.contracts.values())if(c.type.equals("CLEAR_BANDIT_STRONGHOLD")&&dungeon.id.equals(c.worldTarget)&&!Set.of("COMPLETED","FAILED","EXPIRED").contains(c.status))return c;var gov=world.civilization.governments.get(settlement.id);Npc requester=null;if(gov!=null&&gov.leader!=null)requester=world.npcs.get(gov.leader);if(requester==null)requester=settlement.residents.stream().map(world.npcs::get).filter(Objects::nonNull).filter(n->n.profession.equals("guard")).findFirst().orElse(null);if(requester==null)return null;long reward=55;if(gov!=null){reward=Math.min(reward,Math.max(0,gov.treasury));gov.treasury-=reward;}if(reward<=0)return null;CivilizationState.Contract c=new CivilizationState.Contract();c.id=SettlementBootstrap.uuid("bandit-clear:"+dungeon.id);c.requester=requester.id;c.settlement=settlement.id;c.type="CLEAR_BANDIT_STRONGHOLD";c.title="Clear the bandit stronghold";c.description="Bandits have occupied an old fort and are threatening regional trade. Remove the hostile force and make the route safe again.";c.worldTarget=dungeon.id;c.required=dungeon.occupants.size();c.reward=reward;c.deadline=world.clock+96000;c.locationQuality=dungeon.state.equals("UNEXPLORED")?"APPROXIMATE":"EXACT";c.target=new Pos(dungeon.anchor.x(),dungeon.surface,dungeon.anchor.z());c.knownInformation=dungeon.state.equals("UNEXPLORED")?"Scouts place the fort somewhere near "+Math.round(dungeon.anchor.x())+", "+Math.round(dungeon.anchor.z())+". Exact internal strength is unknown.":"The stronghold has been discovered. Last confirmed at "+Math.round(dungeon.anchor.x())+", "+Math.round(dungeon.anchor.z())+".";world.civilization.contracts.put(c.id,c);world.transact(settlement.id+":treasury",c.id.toString(),"security contract escrow",1,reward,"bandit stronghold clearance");world.remember(requester,new Memory("bandit_stronghold_threat",null,dungeon.id,world.clock,.9,-.7,.9,"scout report"));return c;}
    private static String display(String item){int colon=item.indexOf(':');return (colon>=0?item.substring(colon+1):item).replace('_',' ');}
    private ContractDirector(){}
}
