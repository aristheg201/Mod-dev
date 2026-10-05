package vn.worldcomesalive.agriculture;
import vn.worldcomesalive.model.LivingWorld.*;
import vn.worldcomesalive.model.LivingWorld;
import vn.worldcomesalive.world.SettlementBootstrap;
import java.util.*;
/** Economy-first agricultural capacity and persistent crop lifecycle, shared across simulation LOD. */
public final class Agriculture {
    public static final class Plot {
        public UUID id;public String settlement,owner,building,crop,state="GROWING",storage;public Pos origin;public int width,depth,capacity;public double fertility=.8;public long due;public List<UUID> workers=new ArrayList<>();public List<Pos> cells=new ArrayList<>();
    }
    public static final class Pasture {public UUID id;public String settlement,owner,storage;public Pos origin;public int width=16,depth=14;public List<UUID> animals=new ArrayList<>();}
    public static final class Livestock {public UUID id;public String species="sheep",pasture;public Pos location;public long lastFed;public boolean alive=true;}
    public static void plan(Settlement s){
        if(!s.fields.isEmpty())return;int population=s.buildings.values().stream().mapToInt(b->b.beds).sum();s.economicIdentity=s.service("farm")!=null?"AGRICULTURE / CRAFT / TRADE":"CRAFT / REGIONAL TRADE";
        for(Building b:s.buildings.values())if(b.beds>0&&!b.type.equals("tavern")){Plot p=plot(s,b,6,4,new Pos(b.origin.x()+2,b.origin.y(),b.origin.z()-7),Math.floorMod(b.id.hashCode(),2)==0?"minecraft:carrot":"minecraft:potato",s.seed^b.id.hashCode());s.fields.put(p.id,p);}
        Building farm=s.service("farm");if(farm==null)return;
        for(int i=0;i<2;i++){int width=Math.max(15,(int)Math.ceil(Math.sqrt(population*9)));Plot p=plot(s,farm,width,Math.max(12,width-4),new Pos(s.center.x()-33+i*(width+4),s.center.y(),s.center.z()+32+i*3),i==0?"minecraft:wheat":"minecraft:potato",s.seed+i);s.fields.put(p.id,p);}
        var pasture=new Pasture();pasture.id=SettlementBootstrap.uuid(s.id+"pasture");pasture.settlement=s.id;pasture.owner=farm.id;pasture.storage=farm.id;pasture.origin=new Pos(s.center.x()-53,s.center.y(),s.center.z()+32);s.pastures.put(pasture.id,pasture);
        for(int i=0;i<4;i++){Livestock a=new Livestock();a.id=SettlementBootstrap.uuid(pasture.id+"animal"+i);a.species=i<2?"sheep":i==2?"cow":"chicken";a.pasture=pasture.id.toString();a.location=new Pos(pasture.origin.x()+4+i*2,pasture.origin.y()+1,pasture.origin.z()+5);s.livestock.put(a.id,a);pasture.animals.add(a.id);}
        Building barn=new Building();barn.id=s.id+"_barn";barn.type="barn";barn.profession="carpenter";barn.width=13;barn.depth=9;barn.height=5;barn.region=s.region;barn.wealth=farm.wealth;barn.owner=farm.id;barn.origin=new Pos(s.center.x()-52,s.center.y(),s.center.z()+18);barn.mark(Marker.ENTRANCE,new Pos(barn.origin.x()+6.5,barn.origin.y()+1,barn.origin.z()+7.5));barn.mark(Marker.ROAD_CONNECTION,new Pos(barn.origin.x()+6.5,barn.origin.y()+1,s.center.z()));s.buildings.put(barn.id,barn);
    }
    private static Plot plot(Settlement s,Building b,int width,int depth,Pos origin,String crop,long seed){var p=new Plot();p.id=SettlementBootstrap.uuid(b.id+origin.x()+":"+origin.z());p.settlement=s.id;p.owner=b.id;p.building=b.id;p.storage=b.id;p.origin=origin;p.width=width;p.depth=depth;p.crop=crop;p.due=1200+Math.floorMod(seed,1200);p.state=Math.floorMod(seed,3)==0?"MATURE":"GROWING";for(int x=0;x<width;x++)for(int z=0;z<depth;z++)if(!(x==0&&z<2||x==width-1&&z>=depth-2))p.cells.add(new Pos(origin.x()+x,origin.y(),origin.z()+z));p.capacity=p.cells.size();return p;}
    public static void assign(LivingWorld world,Settlement s){for(Plot p:s.fields.values()){Building owner=s.buildings.get(p.building);p.owner=owner.owner;Npc farmer=s.residents.stream().map(world.npcs::get).filter(n->n.profession.equals("farmer")&&n.workplace.equals(p.building)).findFirst().orElseGet(()->s.residents.stream().map(world.npcs::get).filter(n->n.profession.equals("farmer")).min(Comparator.comparingDouble(n->n.location.distance(p.origin))).orElse(null));if(farmer!=null){p.workers.add(farmer.id);farmer.ownership.add(p.id.toString());}p.due+=world.clock;}}
    public static Plot workPlot(Settlement s,Npc farmer){return s.fields.values().stream().filter(p->p.workers.contains(farmer.id)&&p.width>=12).min(Comparator.comparingInt(p->p.state.equals("MATURE")?0:p.state.equals("HARVESTED")?1:2)).orElse(null);}
    public static int harvest(Plot p,Building storage){if(!p.state.equals("MATURE"))return 0;int yield=Math.max(1,(int)(p.capacity*p.fertility*.25));storage.stock.merge(p.crop,yield,Integer::sum);p.state="HARVESTED";return yield;}
    private Agriculture(){}
}
