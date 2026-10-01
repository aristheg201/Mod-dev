package vn.svarcade.tcg.client.render;

import net.minecraft.util.math.Vec3d;
import vn.svarcade.tcg.duel.Duel;
import java.util.*;
import java.util.function.Function;

/** Deduplicated, bounded client presentation. It never changes authoritative card state. */
final class DuelVfxTimeline {
    static final Set<String> SEMANTICS=Set.of("SUMMON_NORMAL","SUMMON_TRIBUTE","SUMMON_EXTRA","SUMMON_EVOLUTION","SUMMON_TRANSFORM",
        "ATTACK_PHYSICAL","ATTACK_SPECIAL","CAST_STATUS","PROJECTILE","BEAM","AOE","IMPACT","DIRECT_ATTACK",
        "SPELL_ACTIVATE","TRAP_REVEAL","CHAIN_LINK","CHAIN_RESOLVE","CHAIN_NEGATE","DAMAGE","HEAL","BUFF","DEBUFF","SHIELD",
        "DESTROY","BANISH","RETURN_HAND","RETURN_DECK","SEND_GRAVE","DRAW","SEARCH","DISCARD","MILL","REVIVE",
        "CONTROL_CHANGE","POSITION_CHANGE","CHARGE");
    static final class Instance {
        final Duel.Cue cue;final DuelVfxProfile profile;final Vec3d source,target;final long started;int duration;
        int particles;long emitted;boolean released,impacted,hit,sound;boolean preview,visualOnly;
        Instance(Duel.Cue cue,Vec3d source,Vec3d target,long started,boolean preview) {
            this.cue=cue;this.source=source;this.target=target;this.started=started;this.preview=preview;
            profile=DuelVfxProfile.resolve(cue.presentation()==null?cue.element():vn.svarcade.tcg.data.EffectSpec.value(cue.presentation().profile(),cue.element()));
            duration=Math.clamp(cue.presentation()==null?1400:cue.presentation().duration(),400,Math.min(3000,profile.maxLifetime()));
        }
        double progress(long now){return Math.clamp((now-started)/(double)duration,0,1);}
        double fraction(String stage){
            var a=cue.presentation()==null?null:cue.presentation().animation();
            if(a==null)return switch(stage){case "release"->.46;case "impact"->.72;case "recovery"->.90;default->.25;};
            return Math.clamp(switch(stage){case "release"->a.release();case "impact"->a.impact();case "recovery"->a.recovery();default->a.windup();},.05,.98);
        }
        String mode(){return cue.presentation()==null?"PROJECTILE":vn.svarcade.tcg.data.EffectSpec.value(cue.presentation().mode(),"PROJECTILE");}
    }
    private long observed,lastRevision;
    private final List<Instance> instances=new ArrayList<>();
    private final ArrayDeque<Instance> pending=new ArrayDeque<>();
    List<Instance> active(){return instances;}
    void observe(Duel.View view,Function<String,Vec3d> position,Vec3d origin,java.util.function.BiFunction<Integer,Boolean,Vec3d> fallback,long now) {
        if(view.revision()<lastRevision){clear();observed=0;}lastRevision=view.revision();
        int resolution=0;long delay=0;long battleDelay=0;
        for(Duel.Cue cue:view.cues()) {
            if(cue.sequence()<=observed)continue;observed=cue.sequence();
            if(!SEMANTICS.contains(cue.semantic()))continue;
            Vec3d source=position.apply(cue.source()),target=position.apply(cue.target());
            if(source==null)source=fallback.apply(cue.controller(),false);
            if(target==null)target=fallback.apply(cue.controller(),true);
            if(cue.semantic().equals("CHAIN_RESOLVE"))delay=resolution++*450L;
            long starts=now+delay;
            if(cue.semantic().equals("ATTACK_PHYSICAL")||cue.semantic().equals("ATTACK_SPECIAL"))battleDelay=1000;
            if(cue.semantic().equals("IMPACT"))starts+=battleDelay;
            if(cue.semantic().equals("DESTROY"))starts+=Math.max(450,battleDelay+200);
            if(pending.size()>=24)pending.removeFirst();pending.add(new Instance(cue,source,target,starts,false));
            if(Set.of("CAST_STATUS","SPELL_ACTIVATE","TRAP_REVEAL","ATTACK_PHYSICAL","ATTACK_SPECIAL").contains(cue.semantic())&&cue.presentation()!=null) {
                for(var stage:vn.svarcade.tcg.data.EffectSpec.list(cue.presentation().stages())){
                    if(pending.size()>=24)break;var base=cue.presentation();
                    var presentation=new vn.svarcade.tcg.data.EffectSpec.Presentation(base.mode(),stage.profile(),650,base.animation(),base.particle(),base.fallback(),stage.shape(),null);
                    var child=new Duel.Cue(cue.sequence(),stage.semantic(),cue.source(),cue.target(),cue.controller(),cue.element(),presentation,cue.link());
                    var instance=new Instance(child,source,target,starts+(long)(stage.at()*base.duration()),false);instance.visualOnly=true;pending.add(instance);
                }
            }
        }
    }
    void tick(long now,java.util.function.Consumer<Instance> start) {
        instances.removeIf(v->now-v.started>v.duration);
        for(var it=pending.iterator();it.hasNext();) {
            Instance v=it.next();if(v.started>now)continue;it.remove();
            if(instances.size()>=Math.min(24,v.profile.maxConcurrentInstances()))instances.removeFirst();
            instances.add(v);if(!v.visualOnly)start.accept(v);
        }
    }
    void preview(Duel.Cue cue,Vec3d source,Vec3d target,long now,java.util.function.Consumer<Instance> start) {
        clear();Instance v=new Instance(cue,source,target,now,true);instances.add(v);if(!v.visualOnly)start.accept(v);
    }
    void appendPreview(Duel.Cue cue,Vec3d source,Vec3d target,long now,java.util.function.Consumer<Instance> start) {
        Instance v=new Instance(cue,source,target,now,true);instances.add(v);if(!v.visualOnly)start.accept(v);
    }
    Vec3d motion(String token,long now) {
        for(var v:instances)if(v.cue.source().equals(token)&&v.cue.semantic().equals("ATTACK_PHYSICAL")){
            double t=v.progress(now),contact=v.fraction("impact");
            double dash=t<contact?Math.sin(Math.PI*.5*Math.clamp((t-v.fraction("windup"))/(contact-v.fraction("windup")),0,1)):
                Math.cos(Math.PI*.5*Math.clamp((t-contact)/(1-contact),0,1));
            Vec3d toward=v.target.subtract(v.source);double length=Math.max(.01,toward.horizontalLength());
            return new Vec3d(toward.x/length,0,toward.z/length).multiply(Math.min(6,Math.max(0,length-2))*dash);
        }
        return Vec3d.ZERO;
    }
    Instance card(String token){return instances.stream().filter(v->v.cue.source().equals(token)&&Set.of("SPELL_ACTIVATE","TRAP_REVEAL","CHAIN_LINK","CHAIN_RESOLVE","CHAIN_NEGATE").contains(v.cue.semantic())).findFirst().orElse(null);}
    boolean departing(String token){return java.util.stream.Stream.concat(instances.stream(),pending.stream()).anyMatch(v->v.cue.source().equals(token)&&Set.of("DESTROY","BANISH","SEND_GRAVE","RETURN_HAND","RETURN_DECK").contains(v.cue.semantic()));}
    boolean settled(){return instances.isEmpty()&&pending.isEmpty();}
    double cameraPunch(long now) {
        double value=0;for(var v:instances){double t=v.progress(now);if(v.cue.semantic().startsWith("SUMMON")||v.cue.semantic().equals("IMPACT")||v.cue.semantic().equals("CHAIN_NEGATE"))value+=Math.sin(Math.PI*t)*.15;}
        return Math.min(.35,value);
    }
    void clear(){instances.clear();pending.clear();}
}
