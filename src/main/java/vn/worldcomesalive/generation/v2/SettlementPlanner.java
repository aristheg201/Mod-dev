package vn.worldcomesalive.generation.v2;
import java.util.*;
import static vn.worldcomesalive.generation.v2.Spatial.*;
/** Economy -> macro graph -> terrain routes -> property lots -> massing -> room programs. No world access. */
public final class SettlementPlanner {
    private final GenerationCatalog data;private final TerrainSnapshot terrain;private final SettlementProgram program;private final Point center;private final Random random;
    private SettlementPlanner(SettlementProgram p,Point c,TerrainSnapshot t,GenerationCatalog d){program=p;center=c;terrain=t;data=d;random=new Random(p.seed());}
    public static SettlementPlan plan(SettlementProgram p,Point c,TerrainSnapshot t,GenerationCatalog d){PlanningRejectedException last=null;for(int attempt=0;attempt<5;attempt++)try{return new SettlementPlanner(p,c,t,d).compose(attempt);}catch(PlanningRejectedException e){last=e;}throw new PlanningRejectedException("V2 settlement rejected after bounded recomposition: "+(last==null?"unknown planning failure":last.getMessage()),last);}
    private SettlementPlan compose(int attempt){SettlementPlan p=new SettlementPlan();p.program=program;p.center=center;var archetype=data.archetypes.get(program.archetype());int turn=Math.floorMod((int)program.seed()+attempt,4);p.orientation=turn;
        for(var d:archetype.districts()){Point relative=rotate(new Point(d.x(),d.z()),turn),wanted=center.plus(relative.x(),relative.z());Point chosen=wanted;double score=-Double.MAX_VALUE;for(int x=-9;x<=9;x+=3)for(int z=-9;z<=9;z+=3){Point candidate=wanted.plus(x,z);double s=terrain.suitability(centered(candidate,9,9))-Math.hypot(x,z)*.25;if(s>score&&terrain.contains(candidate,35)){score=s;chosen=candidate;}}if(score<-100)throw new PlanningRejectedException("Unbuildable district "+d.id());p.districts.add(new SettlementPlan.District(d.id(),d.kind(),chosen,d.radius(),d.density()));}
        p.publicSpaces.add(new SettlementPlan.PublicSpace("village_green","CIVIC",centered(p.district("core").center(),19,19)));p.publicSpaces.add(new SettlementPlan.PublicSpace("market_square","MARKET",centered(p.district("market").center(),19,15)));
        for(var edge:archetype.links()){Point a=p.district(edge.from()).center(),b=p.district(edge.to()).center();var path=TerrainRouter.tryRoute(a,b,terrain,data.roads.get(edge.road())).orElse(null);if(path==null)throw new PlanningRejectedException("Unbuildable district connection "+edge.from()+"->"+edge.to());p.roads.add(new SettlementPlan.Road("route_"+p.roads.size(),edge.from(),edge.to(),edge.road(),path));}
        List<String> services=new ArrayList<>(program.services().keySet());Collections.sort(services);for(String id:services)for(int i=0;i<program.services().get(id);i++)lot(p,id,districtFor(id),attempt);
        for(int i=0;i<program.homes().size();i++)lot(p,program.homes().get(i),i%2==0?"west":"east",attempt);
        // Agricultural capacity is allocated by the economic program, before any blocks or population exist.
        List<SettlementPlan.Lot> farms=p.lots.stream().filter(l->l.kind().equals("farm")).toList();int remaining=program.foodCapacity(),index=0;int fieldSize=program.archetype().equals("farming_village")?26:23;
        while(remaining>0||index<archetype.minFields()){var owner=farms.get(index%farms.size());Rect r=findLand(p,p.district("farm").center(),fieldSize,21,index);int capacity=(fieldSize-2-(fieldSize-2)/7)*19;var farm=p.buildings.stream().filter(b->b.lot().equals(owner.id())).findFirst().orElseThrow();p.fields.add(new SettlementPlan.Field("field_"+index,"CROP",r,index%3==2?"minecraft:potato":"minecraft:wheat",capacity,owner.id(),farm.id()));remaining-=capacity;index++;if(index>12)throw new PlanningRejectedException("Insufficient productive land");}
        var owner=farms.getFirst();p.fields.add(new SettlementPlan.Field("pasture_0","PASTURE",findLand(p,p.district("pasture").center(),25,22,7),"",0,owner.id(),owner.building()));p.fields.add(new SettlementPlan.Field("orchard_0","ORCHARD",findLand(p,p.district("farm").center(),22,19,9),"minecraft:apple",16,owner.id(),owner.building()));
        for(var field:p.fields){var r=field.boundary();Point entrance=new Point(r.x(),r.z()+r.depth()/2);var access=TerrainRouter.nearest(entrance,p.roads.stream().filter(road->!road.id().startsWith("access_")).toList());var obstacles=p.buildings.stream().map(b->b.bounds().expand(2)).toList();p.roads.add(new SettlementPlan.Road("access_"+field.id(),field.id(),"farm","FARM_TRACK",TerrainRouter.route(access,entrance,terrain,data.roads.get("FARM_TRACK"),obstacles)));}
        p.dungeon=DungeonPlanner.plan(p,terrain,data);
        GenerationValidation.validate(p,data);if(!p.failures.isEmpty())throw new IllegalArgumentException(String.join("; ",p.failures));return p;
    }
    private String districtFor(String building){return switch(building){case "farmstead","barn"->"farm";case "smithy","bakery"->"craft";case "trading_hall"->"trade";case "market_hall"->"market";case "crowned_inn"->"market";default->"core";};}
    private void lot(SettlementPlan p,String definition,String district,int attempt){var spec=data.buildings.get(definition);Point anchor=p.district(district).center();double best=-Double.MAX_VALUE;Rect selected=null;int facing=0;Point frontage=null;
        List<SettlementPlan.Road> districtRoads=p.roads.stream().filter(r->!r.kind().equals("FOOTPATH")&&(r.from().equals(district)||r.to().equals(district))).toList();
        for(int candidate=0;candidate<960;candidate++){
            boolean streetCandidate=!districtRoads.isEmpty()&&candidate<720;
            Point center,access;
            if(streetCandidate){
                var road=districtRoads.get(Math.floorMod(candidate,districtRoads.size()));
                var pts=road.points();
                int usable=Math.max(1,pts.size()-2);
                int pi=1+Math.floorMod(candidate/Math.max(1,districtRoads.size()),usable);
                Point prev=pts.get(Math.max(0,pi-1)),sample=pts.get(Math.min(pi,pts.size()-1)),next=pts.get(Math.min(pts.size()-1,pi+1));
                double dx=next.x()-prev.x(),dz=next.z()-prev.z(),len=Math.max(.001,Math.hypot(dx,dz));
                double side=((candidate/districtRoads.size())%2==0?1:-1);
                double setbackBase=Math.max(spec.width(),spec.depth())*.5+spec.lotMargin()+data.roads.get(road.kind()).width()*.5+2;
                double setback=setbackBase+Math.floorMod(candidate/Math.max(1,districtRoads.size()*usable*2),4)*2.5;
                double longitudinal=(Math.floorMod(candidate/2,9)-4)*2.25;
                center=new Point(sample.x()+(-dz/len)*setback*side+(dx/len)*longitudinal,sample.z()+(dx/len)*setback*side+(dz/len)*longitudinal);
                access=sample;
            }else{
                int radial=candidate-720;
                double angle=radial*2.399963229728653+attempt*.3;
                double radius=14+Math.sqrt(radial)*3.0;
                center=anchor.plus(Math.cos(angle)*radius,Math.sin(angle)*radius);
                access=TerrainRouter.nearest(center,p.roads.stream().filter(r->!r.kind().equals("FOOTPATH")).toList());
            }
            if(!terrain.contains(center,25))continue;
            int rotation=facing(center,access);
            int width=rotation%2==0?spec.width():spec.depth(),depth=rotation%2==0?spec.depth():spec.width();
            Rect building=centered(center,width,depth),lot=building.expand(spec.lotMargin());
            if(p.lots.stream().anyMatch(l->l.boundary().expand(2).overlaps(lot))||p.publicSpaces.stream().anyMatch(space->space.boundary().overlaps(building.expand(2))))continue;
            if(p.roads.stream().anyMatch(r->TerrainRouter.crosses(building,r,data.roads.get(r.kind()).width()/2+2)))continue;
            var probe=new SettlementPlan.Building("probe",definition,building,0,rotation,1,"","probe",List.of());
            Point door=probe.local(spec.width()/2.0,spec.depth()-1);
            var approach=new SettlementPlan.Road("probe","probe",district,"FOOTPATH",List.of(access,door));
            if(p.buildings.stream().anyMatch(b->TerrainRouter.crosses(b.bounds(),approach,2)))continue;
            double suit=terrain.suitability(building);if(suit<-100)continue;
            double frontagePenalty=access.distance(center);
            double score=suit-frontagePenalty*.30-center.distance(anchor)*.14+(streetCandidate?7.5:0)+random.nextDouble()*.3;
            if(score>best){best=score;selected=building;facing=rotation;frontage=access;}
        }
        if(selected==null)throw new PlanningRejectedException("Cannot allocate accessible lot "+definition);String id="building_"+p.buildings.size(),lotId="lot_"+p.lots.size();List<SettlementPlan.Room> rooms=new ArrayList<>();for(var r:spec.rooms())rooms.add(new SettlementPlan.Room(id+"_room_"+rooms.size(),r.type(),new Rect(r.x(),r.z(),r.width(),r.depth()),r.floor(),r.composition(),r.beds()));var building=new SettlementPlan.Building(id,definition,selected,terrain.foundation(selected),facing,spec.floors(),spec.roof(),lotId,List.copyOf(rooms));Point entrance=building.local(spec.width()/2.0,spec.depth()-1),gate=building.local(spec.width()/2.0,spec.depth()+data.facades.get(spec.facade()).porchDepth());double wealth=definition.contains("noble")||definition.equals("civic_hall")?.9:definition.contains("crofter")?.22:.48+Math.floorMod(id.hashCode()+(int)program.seed(),20)*.01;
        List<Rect> obstacles=new ArrayList<>(p.buildings.stream().map(b->b.bounds().expand(1)).toList());obstacles.add(selected.expand(1));var routedApproach=TerrainRouter.tryRoute(frontage,gate,terrain,data.roads.get("FOOTPATH"),obstacles).orElse(null);if(routedApproach==null)throw new PlanningRejectedException("Unbuildable lot approach "+definition);List<Point> approach=new ArrayList<>(routedApproach);approach.add(entrance);var accessRoad=new SettlementPlan.Road("access_"+id,lotId,district,"FOOTPATH",List.copyOf(approach));for(var existing:p.buildings)if(TerrainRouter.crosses(existing.bounds(),accessRoad,1))throw new PlanningRejectedException("Lot access intersects occupied building");p.buildings.add(building);p.lots.add(new SettlementPlan.Lot(lotId,district,spec.type(),selected.expand(spec.lotMargin()),id,frontage,gate,wealth));p.roads.add(accessRoad);
    }
    private Rect findLand(SettlementPlan p,Point anchor,int width,int depth,int phase){Rect best=null;double score=-Double.MAX_VALUE;for(int i=0;i<1400;i++){double angle=i*2.399963229728653+phase*.7,radius=16+Math.sqrt(i)*3;Point c=anchor.plus(Math.cos(angle)*radius,Math.sin(angle)*radius);Rect r=centered(c,width,depth);if(!terrain.contains(c,Math.max(width,depth)))continue;if(p.publicSpaces.stream().anyMatch(space->space.boundary().expand(2).overlaps(r))||p.lots.stream().anyMatch(l->l.boundary().expand(2).overlaps(r))||p.fields.stream().anyMatch(f->f.boundary().expand(3).overlaps(r))||p.roads.stream().anyMatch(road->TerrainRouter.crosses(r,road,data.roads.get(road.kind()).width()/2+1)))continue;double suit=terrain.suitability(r);if(suit<-100)continue;double value=suit-c.distance(anchor)*.12;if(value>score){score=value;best=r;}}if(best==null)throw new PlanningRejectedException("Cannot allocate agricultural land");return best;}
    private static int facing(Point from,Point target){double dx=target.x()-from.x(),dz=target.z()-from.z();return Math.abs(dx)>Math.abs(dz)?dx<0?1:3:dz<0?2:0;}
}
