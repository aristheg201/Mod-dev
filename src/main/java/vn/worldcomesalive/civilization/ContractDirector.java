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
        for(var route:world.civilization.tradeRoutes.values())TradeNetwork.opportunity(world,route).ifPresent(t->supply(world,route,t));for(var target:world.civilization.huntingTargets.values())if(target.status.equals("ACTIVE"))HuntingSystem.ensureContract(world,target);
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

    private static String display(String item){int colon=item.indexOf(':');return (colon>=0?item.substring(colon+1):item).replace('_',' ');}
    private ContractDirector(){}
}
