package vn.svframe.mythicmobsfabric.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class ParityTargeters {
    private ParityTargeters() {}
    static void register(SkillRuntime r, SkillPlatform p) {
        r.registerTargeter("selflocation",(c,l)->SkillRuntime.Targets.locations(List.of(p.position(c.caster()))),"casterlocation");
        r.registerTargeter("selfeye",(c,l)->SkillRuntime.Targets.locations(List.of(p.eyePosition(c.caster()))),"eye","castereye");
        r.registerTargeter("triggerlocation",(c,l)->locationOf(p,ParityUtil.entity(c,c.triggerEntity())));
        r.registerTargeter("triggereye",(c,l)->SkillRuntime.Targets.locations(List.of(p.eyePosition(ParityUtil.entity(c,c.triggerEntity())))));
        r.registerTargeter("ownerlocation",(c,l)->locationOf(p,p.owner(c.caster())));
        r.registerTargeter("parentlocation",(c,l)->locationOf(p,p.parent(c.caster())));
        r.registerTargeter("wolfowner",(c,l)->entityOf(p,p.owner(c.caster())));
        r.registerTargeter("siblings",(c,l)->siblings(p,c.caster()));
        r.registerTargeter("playersinworld",(c,l)->SkillRuntime.Targets.entities(ParityUtil.filterWorld(p,p.allPlayers(),p.world(c.caster()))),"worldplayers");
        r.registerTargeter("livinginworld",(c,l)->SkillRuntime.Targets.entities(ParityUtil.filterWorld(p,p.allLiving(c.caster()),p.world(c.caster()))),"worldlivingentities","worldentities");
        r.registerTargeter("playersinring",(c,l)->SkillRuntime.Targets.entities(ParityUtil.ring(p,c.caster(),p.allPlayers(),l)));
        r.registerTargeter("livingentitiesinring",(c,l)->SkillRuntime.Targets.entities(ParityUtil.ring(p,c.caster(),p.allLiving(c.caster()),l)),"entitiesinring","mobsinring");
        r.registerTargeter("livingincone",(c,l)->SkillRuntime.Targets.entities(ParityUtil.cone(p,c.caster(),p.allLiving(c.caster()),l)),"entitiesincone","mobsincone");
        r.registerTargeter("playersincone",(c,l)->SkillRuntime.Targets.entities(ParityUtil.cone(p,c.caster(),p.allPlayers(),l)));
        r.registerTargeter("livinginline",(c,l)->SkillRuntime.Targets.entities(ParityUtil.line(p,c.caster(),p.allLiving(c.caster()),l)),"entitiesinline","mobsinline");
        r.registerTargeter("playersinline",(c,l)->SkillRuntime.Targets.entities(ParityUtil.line(p,c.caster(),p.allPlayers(),l)));
        r.registerTargeter("forward",(c,l)->SkillRuntime.Targets.locations(List.of(ParityUtil.forward(p,c.caster(),l.decimal("forward",l.decimal("f",l.decimal("distance",1,"d")))))));
        r.registerTargeter("forwardwall",(c,l)->SkillRuntime.Targets.locations(List.of(ParityUtil.forward(p,c.caster(),l.decimal("distance",l.decimal("d",5))))));
        r.registerTargeter("randomlocationsnearsource",(c,l)->SkillRuntime.Targets.locations(ParityUtil.randomLocations(p.position(c.caster()),l,false)),"randomlocationsnearorigin");
        r.registerTargeter("randomlocationsneartarget",(c,l)->SkillRuntime.Targets.locations(ParityUtil.randomLocations(p.position(ParityUtil.target(c,null)),l,false)));
        r.registerTargeter("randomlocationsinsphere",(c,l)->SkillRuntime.Targets.locations(ParityUtil.randomLocations(p.position(c.caster()),l,true)));
        r.registerTargeter("randomlocationsinradius",(c,l)->SkillRuntime.Targets.locations(ParityUtil.randomLocations(p.position(c.caster()),l,false)));
        r.registerTargeter("randomlocationsinring",(c,l)->SkillRuntime.Targets.locations(ParityUtil.randomRing(p.position(c.caster()),l)));
        r.registerTargeter("randomlocationsinrectangle",(c,l)->SkillRuntime.Targets.locations(randomRectangle(p.position(c.caster()),l)));
        r.registerTargeter("flooroftargets",(c,l)->floorTargets(p,c));
        r.registerTargeter("blocksinradius",(c,l)->blocksInRadius(p.position(c.caster()),l),"blocksnearorigin");
        r.registerTargeter("blocksinchunk",(c,l)->blocksInChunk(p.position(c.caster()),l));
        r.registerTargeter("uuid",(c,l)->uuidTarget(l));
        r.registerTargeter("variablelocation",ParityTargeters::variableLocation);
        r.registerTargeter("trackedlocation",(c,l)->parsedLocation(p.getCustomData(c.caster(),"tracked_location"),c.origin()));
        r.registerTargeter("targeteye",(c,l)->SkillRuntime.Targets.locations(List.of(p.eyePosition(ParityUtil.target(c,null)))));
        r.registerTargeter("line",(c,l)->lineLocations(p,c.caster(),l));
        r.registerTargeter("ring",(c,l)->SkillRuntime.Targets.locations(ParityUtil.ringLocations(p.position(c.caster()),l)));
        r.registerTargeter("sphere",(c,l)->SkillRuntime.Targets.locations(ParityUtil.sphereLocations(p.position(c.caster()),l)));
    }
    private static SkillRuntime.Targets locationOf(SkillPlatform p,UUID u){return u==null?SkillRuntime.Targets.empty():SkillRuntime.Targets.locations(List.of(p.position(u)));}
    private static SkillRuntime.Targets entityOf(SkillPlatform p,UUID u){return u==null?SkillRuntime.Targets.empty():SkillRuntime.Targets.entities(List.of(u));}
    private static SkillRuntime.Targets siblings(SkillPlatform p,UUID u){UUID par=p.parent(u);if(par==null)return SkillRuntime.Targets.empty();List<UUID> xs=new ArrayList<>(p.children(par));xs.remove(u);return SkillRuntime.Targets.entities(xs);}
    private static List<Vec3> randomRectangle(Vec3 c,SkillLine l){int n=Math.max(1,l.integer("amount",l.integer("a",1)));double x=l.decimal("x",l.decimal("width",5)),z=l.decimal("z",l.decimal("length",5));List<Vec3>out=new ArrayList<>();for(int i=0;i<n;i++)out.add(new Vec3(c.x()+(ParityUtil.RANDOM.nextDouble()*2-1)*x,c.y(),c.z()+(ParityUtil.RANDOM.nextDouble()*2-1)*z,c.world()));return out;}
    private static SkillRuntime.Targets floorTargets(SkillPlatform p,SkillContext c){List<Vec3>out=new ArrayList<>();for(UUID u:c.entityTargets()){Vec3 v=p.position(u);out.add(new Vec3(v.x(),Math.floor(v.y()),v.z(),v.world()));}return SkillRuntime.Targets.locations(out);}
    private static SkillRuntime.Targets blocksInRadius(Vec3 c,SkillLine l){int r=Math.max(0,l.integer("radius",l.integer("r",1)));List<Vec3>out=new ArrayList<>();for(int x=-r;x<=r;x++)for(int y=-r;y<=r;y++)for(int z=-r;z<=r;z++)if(x*x+y*y+z*z<=r*r)out.add(new Vec3(Math.floor(c.x())+x,Math.floor(c.y())+y,Math.floor(c.z())+z,c.world()));return SkillRuntime.Targets.locations(out);}
    private static SkillRuntime.Targets blocksInChunk(Vec3 c,SkillLine l){int minY=l.integer("miny",(int)Math.floor(c.y())-1),maxY=l.integer("maxy",(int)Math.floor(c.y())+1),bx=((int)Math.floor(c.x())>>4)<<4,bz=((int)Math.floor(c.z())>>4)<<4;List<Vec3>out=new ArrayList<>();for(int x=0;x<16;x++)for(int z=0;z<16;z++)for(int y=minY;y<=maxY;y++)out.add(new Vec3(bx+x,y,bz+z,c.world()));return SkillRuntime.Targets.locations(out);}
    private static SkillRuntime.Targets uuidTarget(SkillLine l){try{return SkillRuntime.Targets.entities(List.of(UUID.fromString(ParityUtil.text(l,"uuid","u","id","i"))));}catch(Exception ex){return SkillRuntime.Targets.empty();}}
    private static SkillRuntime.Targets variableLocation(SkillContext c,SkillLine l){return parsedLocation(c.variables().get(ParityUtil.text(l,"variable","var","v")),c.origin());}
    private static SkillRuntime.Targets parsedLocation(Object raw,Vec3 fallback){if(raw instanceof Vec3 v)return SkillRuntime.Targets.locations(List.of(v));if(raw!=null){String[] s=String.valueOf(raw).split(",");try{if(s.length>=4)return SkillRuntime.Targets.locations(List.of(new Vec3(Double.parseDouble(s[1]),Double.parseDouble(s[2]),Double.parseDouble(s[3]),s[0])));}catch(Exception ignored){}}return fallback==null?SkillRuntime.Targets.empty():SkillRuntime.Targets.locations(List.of(fallback));}
    private static SkillRuntime.Targets lineLocations(SkillPlatform p,UUID u,SkillLine l){int points=Math.max(2,l.integer("points",l.integer("p",10)));double len=l.decimal("length",l.decimal("l",10));Vec3 start=p.eyePosition(u),dir=ParityUtil.forwardVector(p,u);List<Vec3>out=new ArrayList<>();for(int i=0;i<points;i++)out.add(start.add(dir.multiply(len*i/(points-1.0))));return SkillRuntime.Targets.locations(out);}
}
