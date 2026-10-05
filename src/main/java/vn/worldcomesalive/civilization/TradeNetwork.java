package vn.worldcomesalive.civilization;

import vn.worldcomesalive.model.LivingWorld;
import vn.worldcomesalive.model.LivingWorld.*;
import vn.worldcomesalive.world.SettlementBootstrap;
import java.util.*;

/**
 * Persistent inter-settlement trade simulation. Routes are logical world infrastructure;
 * nearby presentation can materialize caravans later without changing the economic truth.
 */
public final class TradeNetwork {
    public static final class Route {
        public String id,origin,destination;
        public List<String> stops=new ArrayList<>();
        public double distance,danger,security=50,traffic=1;
        public boolean blocked;
        public long lastShipment=-24000,lastUpdated;
        public Map<String,Integer> moved=new LinkedHashMap<>();
    }
    public static final class Shipment {
        public UUID id,contract;
        public String route,origin,destination,item,status="PREPARING";
        public int quantity;
        public long depart,eta;
        public boolean sealed=true,damaged,riskResolved;
        public Pos lastKnown;
    }

    public static String id(String a,String b){return a.compareTo(b)<0?a+"<->"+b:b+"<->"+a;}

    public static void initialize(LivingWorld world,Settlement settlement){
        for(String otherId:settlement.routes){
            Settlement other=world.settlements.get(otherId);
            if(other==null||!other.ready)continue;
            String id=id(settlement.id,other.id);
            Route route=world.civilization.tradeRoutes.computeIfAbsent(id,k->new Route());
            route.id=id;
            if(settlement.id.compareTo(other.id)<0){route.origin=settlement.id;route.destination=other.id;}
            else {route.origin=other.id;route.destination=settlement.id;}
            route.distance=settlement.center.distance(other.center);
            route.lastUpdated=world.clock;
        }
    }

    public static void refresh(LivingWorld world){
        for(Settlement settlement:world.settlements.values())if(settlement.ready)initialize(world,settlement);
        for(Route route:world.civilization.tradeRoutes.values()){
            Settlement a=world.settlements.get(route.origin),b=world.settlements.get(route.destination);
            if(a==null||b==null)continue;
            double security=(security(world,a)+security(world,b))*.5;
            boolean disrupted=world.events.stream().anyMatch(e->e.state.equals("active")&&(e.settlement.equals(a.id)||e.settlement.equals(b.id))&&Set.of("route_damage","bandit_attack","road_ambush").contains(e.type));
            route.security=security;
            route.blocked=disrupted;
            route.danger=Math.max(0,Math.min(100,100-security+(disrupted?45:0)));
            route.traffic=route.blocked?0:Math.max(.15,1-route.danger/120.0);
            route.lastUpdated=world.clock;
        }
    }

    private static double security(LivingWorld world,Settlement settlement){
        var g=world.civilization.governments.get(settlement.id);
        return g==null?50:g.metrics.getOrDefault("Security",50.0);
    }

    /** Find a real stock imbalance worth moving across an existing route. */
    public static Optional<Transfer> opportunity(LivingWorld world,Route route){
        if(route.blocked)return Optional.empty();
        Settlement a=world.settlements.get(route.origin),b=world.settlements.get(route.destination);
        if(a==null||b==null)return Optional.empty();
        Map<String,Integer> as=aggregate(a),bs=aggregate(b);
        Set<String> goods=new LinkedHashSet<>();goods.addAll(as.keySet());goods.addAll(bs.keySet());
        Transfer best=null;
        for(String item:goods){
            int av=as.getOrDefault(item,0),bv=bs.getOrDefault(item,0),delta=av-bv;
            if(Math.abs(delta)<12)continue;
            Settlement from=delta>0?a:b,to=delta>0?b:a;
            int quantity=Math.min(24,Math.max(6,Math.abs(delta)/3));
            long score=(long)Math.abs(delta)*quantity;
            if(best==null||score>best.score)best=new Transfer(route.id,from.id,to.id,item,quantity,score);
        }
        return Optional.ofNullable(best);
    }
    public record Transfer(String route,String origin,String destination,String item,int quantity,long score){}

    private static Map<String,Integer> aggregate(Settlement s){
        Map<String,Integer> result=new LinkedHashMap<>();
        for(Building b:s.buildings.values())for(var e:b.stock.entrySet())if(e.getValue()>0)result.merge(e.getKey(),e.getValue(),Integer::sum);
        return result;
    }

    public static Shipment createShipment(LivingWorld world,Transfer transfer,UUID contract){
        Route route=world.civilization.tradeRoutes.get(transfer.route());
        if(route==null||route.blocked)throw new IllegalArgumentException("Trade route unavailable");
        Shipment shipment=new Shipment();
        shipment.id=SettlementBootstrap.uuid("shipment:"+transfer.route()+":"+transfer.item()+":"+(world.clock/1200));
        shipment.contract=contract;shipment.route=transfer.route();shipment.origin=transfer.origin();shipment.destination=transfer.destination();shipment.item=transfer.item();shipment.quantity=transfer.quantity();shipment.depart=world.clock;shipment.eta=world.clock+Math.max(1200,(long)(route.distance*18));
        world.civilization.shipments.put(shipment.id,shipment);
        route.lastShipment=world.clock;
        return shipment;
    }


    public static Pos position(LivingWorld world,Shipment shipment){Settlement a=world.settlements.get(shipment.origin),b=world.settlements.get(shipment.destination);if(a==null||b==null)return shipment.lastKnown;double span=Math.max(1,shipment.eta-shipment.depart);double t=Math.max(0,Math.min(1,(world.clock-shipment.depart)/span));return a.center.between(b.center,t);}

    public static void tick(LivingWorld world){refresh(world);for(Route route:world.civilization.tradeRoutes.values()){boolean active=world.civilization.shipments.values().stream().anyMatch(s->s.route.equals(route.id)&&Set.of("PREPARING","IN_TRANSIT","DELAYED").contains(s.status));if(!active&&world.clock-route.lastShipment>=2400)opportunity(world,route).ifPresent(t->dispatch(world,t,null));}
        for(Shipment shipment:world.civilization.shipments.values()){if(!Set.of("PREPARING","IN_TRANSIT","DELAYED").contains(shipment.status))continue;Route route=world.civilization.tradeRoutes.get(shipment.route);if(route==null){shipment.status="LOST";continue;}if(shipment.status.equals("PREPARING"))shipment.status="IN_TRANSIT";shipment.lastKnown=position(world,shipment);if(route.blocked){shipment.status="DELAYED";shipment.eta+=100;continue;}if(shipment.status.equals("DELAYED"))shipment.status="IN_TRANSIT";if(!shipment.riskResolved&&world.clock>=shipment.depart+(shipment.eta-shipment.depart)/2){shipment.riskResolved=true;long roll=Math.floorMod(shipment.id.getMostSignificantBits()^shipment.id.getLeastSignificantBits(),100);if(roll<Math.round(route.danger*.12)){shipment.damaged=true;shipment.quantity=Math.max(1,shipment.quantity-(int)Math.max(1,shipment.quantity/4));}}
            if(world.clock>=shipment.eta&&shipment.status.equals("IN_TRANSIT"))deliver(world,shipment,route);}
    }

    public static Shipment dispatch(LivingWorld world,Transfer transfer,UUID contract){Route route=world.civilization.tradeRoutes.get(transfer.route());if(route==null||route.blocked)return null;Settlement origin=world.settlements.get(transfer.origin());if(origin==null)return null;int remaining=transfer.quantity();for(Building b:origin.buildings.values())if(remaining>0){int have=b.stock.getOrDefault(transfer.item(),0);if(have<=0)continue;int take=Math.min(have,remaining);b.stock.put(transfer.item(),have-take);remaining-=take;world.transact(b.id,"shipment:"+transfer.route(),transfer.item(),take,0,"caravan loading");}int loaded=transfer.quantity()-remaining;if(loaded<=0)return null;Transfer actual=new Transfer(transfer.route(),transfer.origin(),transfer.destination(),transfer.item(),loaded,transfer.score());return createShipment(world,actual,contract);}

    private static void deliver(LivingWorld world,Shipment shipment,Route route){Settlement destination=world.settlements.get(shipment.destination);if(destination==null){shipment.status="LOST";return;}Building depot=destination.service("trading_post");if(depot==null)depot=destination.service("bakery");if(depot==null&& !destination.buildings.isEmpty())depot=destination.buildings.values().iterator().next();if(depot==null){shipment.status="LOST";return;}depot.stock.merge(shipment.item,shipment.quantity,Integer::sum);route.moved.merge(shipment.item,shipment.quantity,Integer::sum);shipment.status="DELIVERED";shipment.lastKnown=destination.center;world.transact("shipment:"+shipment.id,depot.id,shipment.item,shipment.quantity,0,"caravan delivery");}

    private TradeNetwork(){}
}
