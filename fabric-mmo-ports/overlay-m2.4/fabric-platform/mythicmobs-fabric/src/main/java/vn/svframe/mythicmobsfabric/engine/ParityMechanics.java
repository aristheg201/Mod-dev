package vn.svframe.mythicmobsfabric.engine;

import java.util.UUID;

final class ParityMechanics {
    private ParityMechanics() {}
    static void register(SkillRuntime r, SkillPlatform p) {
        r.registerMechanic("cancelskill", (c,l,e,v)->{c.terminate();return true;}, "cancel", "cancelevent");
        r.registerMechanic("disengage", (c,l,e,v)->radial(p,c,e,l,true));
        r.registerMechanic("forcepull", (c,l,e,v)->radial(p,c,e,l,false));
        r.registerMechanic("jump", (c,l,e,v)->jump(p,ParityUtil.entity(c,e),l), "spring");
        r.registerMechanic("lunge", (c,l,e,v)->lunge(p,ParityUtil.entity(c,e),l), "propel");
        r.registerMechanic("recoil", (c,l,e,v)->recoil(p,ParityUtil.entity(c,e),l));
        r.registerMechanic("look", (c,l,e,v)->look(p,c,e,v));
        r.registerMechanic("setspeed", (c,l,e,v)->p.setAttribute(ParityUtil.entity(c,e),"generic.movement_speed",l.decimal("speed",l.decimal("s",l.decimal("amount",0.2)))));
        r.registerMechanic("setusegravity", (c,l,e,v)->p.setGravity(ParityUtil.entity(c,e),l.bool("gravity",l.bool("g",true))), "gravity");
        r.registerMechanic("teleportin", (c,l,e,v)->teleportRelative(p,ParityUtil.entity(c,e),l));
        r.registerMechanic("teleporty", (c,l,e,v)->teleportY(p,ParityUtil.entity(c,e),l));
        r.registerMechanic("swap", (c,l,e,v)->swap(p,c,e));
        r.registerMechanic("taunt", (c,l,e,v)->p.setTarget(ParityUtil.entity(c,e),c.caster()), "rally");
        r.registerMechanic("stun", (c,l,e,v)->stun(p,ParityUtil.entity(c,e),l));
        r.registerMechanic("messagejson", (c,l,e,v)->p.message(ParityUtil.entity(c,e),ParityUtil.text(c,l,"message","m","msg","text"),false));
        r.registerMechanic("sendtitle", (c,l,e,v)->p.title(ParityUtil.entity(c,e),ParityUtil.text(c,l,"title","t"),ParityUtil.text(c,l,"subtitle","s"),l.integer("fadein",10,"fi"),l.integer("stay",70,"st"),l.integer("fadeout",20,"fo")));
        r.registerMechanic("takeexperience", (c,l,e,v)->p.addExperience(ParityUtil.entity(c,e),-Math.abs(l.integer("amount",1,"a"))), "takeexp");
        r.registerMechanic("clearxp", (c,l,e,v)->p.clearExperience(ParityUtil.entity(c,e)));
        r.registerMechanic("settime", (c,l,e,v)->p.setTime(ParityUtil.entity(c,e),l.longValue("time",0L,"t")));
        r.registerMechanic("setweather", (c,l,e,v)->p.setWeather(ParityUtil.entity(c,e),l.bool("rain",false,"raining"),l.bool("thunder",false,"thundering"),l.integer("duration",6000,"d")));
        r.registerMechanic("blockdestabilize", (c,l,e,v)->p.breakBlock(ParityUtil.location(p,c,e,v),l.bool("drops",true,"dropitems")), "breakblockandgiveitem");
        r.registerMechanic("playblockbreaksound", (c,l,e,v)->blockSound(p,c,e,l,"block.stone.break"));
        r.registerMechanic("playblockfallsound", (c,l,e,v)->blockSound(p,c,e,l,"block.stone.fall"));
        r.registerMechanic("playblockhitsound", (c,l,e,v)->blockSound(p,c,e,l,"block.stone.hit"));
        r.registerMechanic("playblockplacesound", (c,l,e,v)->blockSound(p,c,e,l,"block.stone.place"));
        r.registerMechanic("playblockstepsound", (c,l,e,v)->blockSound(p,c,e,l,"block.stone.step"));
        r.registerMechanic("speak", (c,l,e,v)->p.message(ParityUtil.entity(c,e),ParityUtil.text(c,l,"message","m","text"),false));
        r.registerMechanic("setstance", (c,l,e,v)->p.setCustomData(ParityUtil.entity(c,e),"stance",ParityUtil.text(c,l,"stance","s","value","v")));
        r.registerMechanic("settrackedlocation", (c,l,e,v)->{
            Vec3 at=ParityUtil.location(p,c,e,v); return p.setCustomData(c.caster(),"tracked_location",at.world()+","+at.x()+","+at.y()+","+at.z());
        });
        r.registerMechanic("setprojectiledirection", (c,l,e,v)->p.velocity(ParityUtil.entity(c,e),direction(p,ParityUtil.entity(c,e),l),true), "projectilevelocity");
        r.registerMechanic("terminateprojectile", (c,l,e,v)->p.remove(ParityUtil.entity(c,e)));
        r.registerMechanic("remount", (c,l,e,v)->p.mount(c.caster(),ParityUtil.entity(c,e)));
    }
    private static boolean radial(SkillPlatform p,SkillContext c,UUID e,SkillLine l,boolean away){UUID t=ParityUtil.entity(c,e);Vec3 a=p.position(c.caster()),b=p.position(t),d=(away?b.subtract(a):a.subtract(b)).normalize();double v=l.decimal("velocity",l.decimal("v",l.decimal("strength",1,"s"))),y=l.decimal("velocityy",l.decimal("vy",0.1));return p.velocity(t,new Vec3(d.x()*v,y,d.z()*v,p.world(t)),true);}
    private static boolean jump(SkillPlatform p,UUID e,SkillLine l){Vec3 v=p.velocity(e);return p.velocity(e,new Vec3(v.x(),l.decimal("velocity",l.decimal("v",0.5)),v.z(),p.world(e)),true);}
    private static boolean lunge(SkillPlatform p,UUID e,SkillLine l){return p.velocity(e,ParityUtil.forwardVector(p,e).multiply(l.decimal("velocity",l.decimal("v",1))).withWorld(p.world(e)),true);}
    private static boolean recoil(SkillPlatform p,UUID e,SkillLine l){return p.velocity(e,ParityUtil.forwardVector(p,e).multiply(-l.decimal("velocity",l.decimal("v",1))).withWorld(p.world(e)),true);}
    private static boolean look(SkillPlatform p,SkillContext c,UUID e,Vec3 loc){UUID t=ParityUtil.entity(c,e);Vec3 from=p.eyePosition(t),to=loc!=null?loc:p.position(ParityUtil.target(c,e)),d=to.subtract(from);double h=Math.sqrt(d.x()*d.x()+d.z()*d.z());return p.setRotation(t,(float)Math.toDegrees(Math.atan2(-d.x(),d.z())),(float)-Math.toDegrees(Math.atan2(d.y(),h)));}
    private static boolean teleportRelative(SkillPlatform p,UUID e,SkillLine l){Vec3 at=p.position(e);return p.teleport(e,new Vec3(at.x()+l.decimal("x",0),at.y()+l.decimal("y",0),at.z()+l.decimal("z",0),at.world()));}
    private static boolean teleportY(SkillPlatform p,UUID e,SkillLine l){Vec3 at=p.position(e);return p.teleport(e,new Vec3(at.x(),l.decimal("y",at.y()),at.z(),at.world()));}
    private static boolean swap(SkillPlatform p,SkillContext c,UUID e){UUID a=c.caster(),b=ParityUtil.target(c,e);if(a==null||b==null)return false;Vec3 pa=p.position(a),pb=p.position(b);return p.teleport(a,pb)&p.teleport(b,pa);}
    private static boolean stun(SkillPlatform p,UUID e,SkillLine l){int ticks=l.integer("duration",l.integer("d",20));if(!p.setAi(e,false))return false;p.schedule(Math.max(1,ticks),()->p.setAi(e,true));return true;}
    private static boolean blockSound(SkillPlatform p,SkillContext c,UUID e,SkillLine l,String fallback){String s=ParityUtil.text(c,l,"sound","s");if(s.isBlank())s=fallback;return p.sound(ParityUtil.entity(c,e),s,(float)l.decimal("volume",1,"v"),(float)l.decimal("pitch",1,"p"));}
    private static Vec3 direction(SkillPlatform p,UUID e,SkillLine l){Vec3 d=new Vec3(l.decimal("x",0),l.decimal("y",0),l.decimal("z",0),p.world(e));if(d.lengthSquared()<1e-9)d=ParityUtil.forwardVector(p,e);return d.normalize().multiply(l.decimal("velocity",l.decimal("v",1))).withWorld(p.world(e));}
}
