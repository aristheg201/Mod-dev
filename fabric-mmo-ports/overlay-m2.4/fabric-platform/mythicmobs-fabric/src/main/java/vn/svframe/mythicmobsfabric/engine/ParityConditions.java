package vn.svframe.mythicmobsfabric.engine;

import java.util.Collection;
import java.util.UUID;

final class ParityConditions {
    private ParityConditions() {}
    static void register(SkillRuntime r, SkillPlatform p) {
        r.registerCondition("crouching",(c,l,e,v)->p.sneaking(ParityUtil.entity(c,e)),"iscrouching","sneak");
        r.registerCondition("isgliding",(c,l,e,v)->p.gliding(ParityUtil.entity(c,e)));
        r.registerCondition("isburning",(c,l,e,v)->p.burning(ParityUtil.entity(c,e)));
        r.registerCondition("isfrozen",(c,l,e,v)->p.frozen(ParityUtil.entity(c,e)));
        r.registerCondition("isonground",(c,l,e,v)->p.onGround(ParityUtil.entity(c,e)));
        r.registerCondition("issprinting",(c,l,e,v)->p.sprinting(ParityUtil.entity(c,e)));
        r.registerCondition("isswimming",(c,l,e,v)->p.swimming(ParityUtil.entity(c,e)));
        r.registerCondition("sunny",(c,l,e,v)->!p.raining(ParityUtil.entity(c,e)));
        r.registerCondition("iscaster",(c,l,e,v)->ParityUtil.entity(c,e).equals(c.caster()));
        r.registerCondition("height",(c,l,e,v)->{Vec3 at=v!=null?v:p.position(ParityUtil.entity(c,e));return at!=null&&ParityUtil.compare(at.y(),range(l,"height","h"));});
        r.registerCondition("pitch",(c,l,e,v)->p instanceof RotationAwareSkillPlatform rotation&&ParityUtil.compare(rotation.pitch(ParityUtil.entity(c,e)),range(l,"pitch","p")));
        r.registerCondition("stanceequals",(c,l,e,v)->ParityUtil.eq(p.getCustomData(ParityUtil.entity(c,e),"stance"),ParityUtil.text(c,l,"stance","s","value","v")));
        r.registerCondition("worldtime",(c,l,e,v)->ParityUtil.compare(p.dayTime(ParityUtil.entity(c,e)),ParityUtil.text(c,l,"time","t","value","v")),"time");
        r.registerCondition("dawn",(c,l,e,v)->ParityUtil.timeBetween(p.dayTime(ParityUtil.entity(c,e)),22000,24000));
        r.registerCondition("dusk",(c,l,e,v)->ParityUtil.timeBetween(p.dayTime(ParityUtil.entity(c,e)),12000,14000));
        r.registerCondition("daytime",(c,l,e,v)->ParityUtil.timeBetween(p.dayTime(ParityUtil.entity(c,e)),0,12000));
        r.registerCondition("nighttime",(c,l,e,v)->ParityUtil.timeBetween(p.dayTime(ParityUtil.entity(c,e)),13000,23000));
        r.registerCondition("dimension",(c,l,e,v)->ParityUtil.contains(ParityUtil.text(c,l,"dimension","d","world","w"),p.world(ParityUtil.entity(c,e))));
        r.registerCondition("moving",(c,l,e,v)->p.velocity(ParityUtil.entity(c,e)).lengthSquared()>Math.pow(l.decimal("threshold",0.01,"t"),2));
        r.registerCondition("motionx",(c,l,e,v)->ParityUtil.compare(p.velocity(ParityUtil.entity(c,e)).x(),ParityUtil.text(c,l,"value","v","x")));
        r.registerCondition("motiony",(c,l,e,v)->ParityUtil.compare(p.velocity(ParityUtil.entity(c,e)).y(),ParityUtil.text(c,l,"value","v","y")));
        r.registerCondition("motionz",(c,l,e,v)->ParityUtil.compare(p.velocity(ParityUtil.entity(c,e)).z(),ParityUtil.text(c,l,"value","v","z")));
        r.registerCondition("speed",(c,l,e,v)->ParityUtil.compare(p.velocity(ParityUtil.entity(c,e)).length(),ParityUtil.text(c,l,"value","v","speed","s")));
        r.registerCondition("playersinradius",(c,l,e,v)->countNearby(p,ParityUtil.entity(c,e),l,true));
        r.registerCondition("livinginradius",(c,l,e,v)->countNearby(p,ParityUtil.entity(c,e),l,false),"mobsinradius","entitiesinradius");
        r.registerCondition("playersinworld",(c,l,e,v)->countWorld(p,ParityUtil.entity(c,e),l,true));
        r.registerCondition("livinginworld",(c,l,e,v)->countWorld(p,ParityUtil.entity(c,e),l,false),"mobsinworld");
        r.registerCondition("children",(c,l,e,v)->ParityUtil.compare(p.children(ParityUtil.entity(c,e)).size(),ParityUtil.text(c,l,"amount","a","value","v")));
        r.registerCondition("isparent",(c,l,e,v)->p.children(ParityUtil.entity(c,e)).contains(ParityUtil.target(c,e)));
        r.registerCondition("isowner",(c,l,e,v)->ParityUtil.target(c,e).equals(p.owner(ParityUtil.entity(c,e))));
        r.registerCondition("targetlineofsight",(c,l,e,v)->p.lineOfSight(ParityUtil.entity(c,e),ParityUtil.target(c,e)),"targetlos");
        r.registerCondition("targetdistance",(c,l,e,v)->ParityUtil.compare(ParityUtil.distance(p,ParityUtil.entity(c,e),ParityUtil.target(c,e)),ParityUtil.text(c,l,"distance","d","value","v")));
        r.registerCondition("verticaldistance",(c,l,e,v)->ParityUtil.compare(Math.abs(p.position(ParityUtil.entity(c,e)).y()-p.position(ParityUtil.target(c,e)).y()),ParityUtil.text(c,l,"distance","d","value","v")));
        r.registerCondition("startswith",(c,l,e,v)->stringValue(c,l).startsWith(ParityUtil.text(c,l,"with","w","match","m")));
        r.registerCondition("endswith",(c,l,e,v)->stringValue(c,l).endsWith(ParityUtil.text(c,l,"with","w","match","m")));
        r.registerCondition("stringequals",(c,l,e,v)->ParityUtil.eq(stringValue(c,l),ParityUtil.text(c,l,"equals","e","match","m","value2")));
        r.registerCondition("stringcontains",(c,l,e,v)->stringValue(c,l).contains(ParityUtil.text(c,l,"contains","c","match","m")));
        r.registerCondition("varisset",(c,l,e,v)->c.variables().containsKey(ParityUtil.text(c,l,"variable","var","v")),"variableisset");
        r.registerCondition("varisnotset",(c,l,e,v)->!c.variables().containsKey(ParityUtil.text(c,l,"variable","var","v")),"variableisnotset");
        r.registerCondition("variableequals",(c,l,e,v)->ParityUtil.eq(String.valueOf(c.variables().get(ParityUtil.text(c,l,"variable","var"))),ParityUtil.text(c,l,"value","v")));
    }
    private static String range(SkillLine l,String key,String alias){String value=l.string(key,"",alias);if(value.isBlank())value=l.string("value","","v");return value;}
    private static boolean countNearby(SkillPlatform p,UUID c,SkillLine l,boolean players){int n=p.nearby(c,l.decimal("radius",l.decimal("r",5)),l.decimal("yradius",l.decimal("yr",-1)),players).size();String expr=ParityUtil.text(l,"amount","a","value","v");return ParityUtil.compare(n,expr.isBlank()?"1":expr);}
    private static boolean countWorld(SkillPlatform p,UUID c,SkillLine l,boolean players){String w=p.world(c);int n=0;Collection<UUID> all=players?p.allPlayers():p.allLiving(c);for(UUID u:all)if(w.equals(p.world(u)))n++;String expr=ParityUtil.text(l,"amount","a","value","v");return ParityUtil.compare(n,expr.isBlank()?"1":expr);}
    private static String stringValue(SkillContext c,SkillLine l){String var=ParityUtil.text(l,"variable","var");if(!var.isBlank()&&c.variables().containsKey(var))return String.valueOf(c.variables().get(var));return ParityUtil.text(c,l,"value","v","string");}
}
