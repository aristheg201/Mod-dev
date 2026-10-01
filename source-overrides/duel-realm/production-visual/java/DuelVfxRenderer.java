package vn.svarcade.tcg.client.render;

import com.cobblemon.mod.common.client.net.effect.SpawnSnowstormParticleHandler;
import com.cobblemon.mod.common.client.particle.BedrockParticleOptionsRepository;
import com.cobblemon.mod.common.net.messages.client.effect.SpawnSnowstormParticlePacket;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.particle.*;
import net.minecraft.registry.Registries;
import net.minecraft.sound.*;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;
import vn.svarcade.tcg.client.component.CardWorldsLanguage;
import java.util.*;

/** Small battlefield meshes plus the existing Minecraft/Cobblemon particle engines. */
final class DuelVfxRenderer {
    private static final Identifier WHITE=Identifier.of("svarcade_tcg","textures/duel/vfx.png");
    private int maxSegments=48;private long particleTick;private int budget;
    void tick(List<DuelVfxTimeline.Instance> active,long now) {
        MinecraftClient client=MinecraftClient.getInstance();if(client.world==null)return;
        if(now-particleTick<50)return;particleTick=now;budget=64;
        for(var v:active) {
            double distance=client.gameRenderer.getCamera().getPos().distanceTo(v.source);
            if(distance>v.profile.distanceCull())continue;
            double t=v.progress(now);
            if(!v.sound){v.sound=true;playSound(v);}
            int count=Math.min(budget,Math.min(Math.max(1,v.profile.spawnRate()/20),v.profile.maxParticles()-v.particles));
            if(distance>32)count=Math.max(1,count/2);
            String shape=v.cue.presentation()==null?v.profile.shape():vn.svarcade.tcg.data.EffectSpec.value(v.cue.presentation().shape(),v.profile.shape());
            boolean summon=v.cue.semantic().startsWith("SUMMON")||v.cue.semantic().equals("REVIVE");
            if(summon)shape=t<.35?"SPIRAL":t<.7?"COLUMN":"SHOCKWAVE";
            if(v.cue.semantic().equals("ATTACK_PHYSICAL"))shape="TRAIL";
            if(v.cue.semantic().equals("ATTACK_SPECIAL"))shape=v.mode().equals("BEAM")?"BEAM":"TRAIL";
            if(v.cue.semantic().equals("CHAIN_NEGATE"))shape="IMPACT_CONE";
            Vec3d center=summon?v.source:v.cue.semantic().equals("ATTACK_SPECIAL")?projectile(v,t):v.cue.semantic().equals("ATTACK_PHYSICAL")?v.source.add(v.target.subtract(v.source).multiply(Math.min(.75,Math.clamp((t-v.fraction("windup"))/(v.fraction("impact")-v.fraction("windup")),0,1)))).add(0,1,0):v.target;
            for(int i=0;i<count;i++) {
                Vec3d point=shape(shape,center,v.source,v.target,(v.particles+i)%32,32,t);
                Vec3d velocity=point.subtract(center).multiply(.02).add(0,summon?.025:.005,0);
                client.world.addParticle(particle(v),true,point.x,point.y,point.z,velocity.x,velocity.y,velocity.z);
            }
            budget-=count;v.particles+=count;
            if(!v.impacted&&t>=v.fraction("impact")&&Set.of("ATTACK_PHYSICAL","ATTACK_SPECIAL","IMPACT","DESTROY","DAMAGE").contains(v.cue.semantic())) {
                v.impacted=true;if(v.particles+15<=v.profile.maxParticles()&&budget>=15){nativeImpact(v,center);v.particles+=15;budget-=15;}
                if(budget>=8){for(int i=0;i<8;i++){Vec3d p=shape("BURST",center,v.source,v.target,i,8,.7);client.world.addParticle(ParticleTypes.CRIT,true,p.x,p.y,p.z,0,.035,0);}budget-=8;}
            }
            if(budget<=0)break;
        }
    }
    private ParticleEffect particle(DuelVfxTimeline.Instance v) {
        String id=v.cue.presentation()==null?v.profile.particle():vn.svarcade.tcg.data.EffectSpec.value(v.cue.presentation().particle(),v.profile.particle());
        Identifier key=Identifier.tryParse(id);
        if(key!=null&&Registries.PARTICLE_TYPE.containsId(key)&&Registries.PARTICLE_TYPE.get(key) instanceof SimpleParticleType simple)return simple;
        String fallback=v.cue.presentation()==null?v.profile.fallback():vn.svarcade.tcg.data.EffectSpec.value(v.cue.presentation().fallback(),v.profile.fallback());
        key=Identifier.tryParse(fallback);
        if(key!=null&&Registries.PARTICLE_TYPE.containsId(key)&&Registries.PARTICLE_TYPE.get(key) instanceof SimpleParticleType simple)return simple;
        int color=v.profile.color();return new DustParticleEffect(new Vector3f((color>>16&255)/255f,(color>>8&255)/255f,(color&255)/255f),1.2f);
    }
    private void nativeImpact(DuelVfxTimeline.Instance v,Vec3d point) {
        if(v.profile.emitter()==null||!Registries.PARTICLE_TYPE.containsId(Identifier.of("cobblemon","snowstorm")))return;
        Identifier emitter=Identifier.tryParse(v.profile.emitter());
        if(emitter==null||BedrockParticleOptionsRepository.INSTANCE.getEffect(emitter)==null)return;
        // Real one-shot provider emitter (10–15 particles / <=1s), not an invented particle ID.
        try {
            SpawnSnowstormParticleHandler.INSTANCE.handle(new SpawnSnowstormParticlePacket(emitter,point),MinecraftClient.getInstance());
            org.slf4j.LoggerFactory.getLogger("cardworlds-vfx").info("CARDWORLDS_NATIVE_PARTICLE registered=cobblemon:snowstorm emitter={}",emitter);
        }catch(RuntimeException ignored){/* Vanilla particles already supply the bounded fallback. */}
    }
    private void playSound(DuelVfxTimeline.Instance v) {
        var client=MinecraftClient.getInstance();String name=v.cue.presentation()==null?v.profile.sound():vn.svarcade.tcg.data.EffectSpec.value(v.cue.presentation().sound(),v.profile.sound());
        Identifier key=Identifier.tryParse(name);SoundEvent sound=key!=null&&Registries.SOUND_EVENT.containsId(key)?Registries.SOUND_EVENT.get(key):SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME;
        client.world.playSound(v.source.x,v.source.y,v.source.z,sound,SoundCategory.PLAYERS,.35f,1.1f,false);
    }
    private Vec3d projectile(DuelVfxTimeline.Instance v,double t) {
        double travel=Math.clamp((t-v.fraction("release"))/(v.fraction("impact")-v.fraction("release")),0,1);
        return v.source.add(0,1.2,0).lerp(v.target.add(0,1.1,0),travel);
    }
    static Vec3d shape(String kind,Vec3d center,Vec3d source,Vec3d target,int i,int count,double t) {
        double a=Math.PI*2*i/Math.max(1,count),r=.4+t*1.8;double f=i/(double)Math.max(1,count-1);
        return switch(kind.toUpperCase(Locale.ROOT)) {
            case "RING" -> center.add(Math.cos(a)*r,.06,Math.sin(a)*r);
            case "SHOCKWAVE" -> center.add(Math.cos(a)*r*(.3+t*2),.06,Math.sin(a)*r*(.3+t*2));
            case "GLYPH" -> {double vertex=Math.round(a/(Math.PI/3))*(Math.PI/3);yield center.add(Math.cos(vertex)*r,.12,Math.sin(vertex)*r);}
            case "AURA" -> center.add(Math.cos(a)*r,.3+f*2,Math.sin(a)*r);
            case "ARC" -> center.add(Math.cos(a*.5)*r,Math.sin(a*.5)*r,0);
            case "SPIRAL" -> center.add(Math.cos(a+t*7)*r*(1-f),f*3,Math.sin(a+t*7)*r*(1-f));
            case "HELIX" -> center.add(Math.cos(a*2+t*7)*r*.6,f*3,Math.sin(a*2+t*7)*r*.6);
            case "VORTEX" -> center.add(Math.cos(a-t*9)*r*(1-t),f*2,Math.sin(a-t*9)*r*(1-t));
            case "BEAM" -> source.add(0,1,0).lerp(target.add(0,1,0),f);
            case "CHAIN_LINE" -> source.add(0,1,0).lerp(target.add(0,1,0),f).add(0,Math.sin(f*Math.PI*6)*.25,0);
            case "TRAIL" -> center.lerp(source.add(0,1,0),f*.5).add(Math.cos(a)*.15,Math.sin(a)*.15,0);
            case "COLUMN" -> center.add(Math.cos(a)*.8,f*4,Math.sin(a)*.8);
            case "ORB" -> center.add(Math.cos(a)*Math.sin(f*Math.PI)*r,Math.cos(f*Math.PI)*r,Math.sin(a)*Math.sin(f*Math.PI)*r);
            case "IMPACT_CONE" -> center.add(Math.cos(a)*r,Math.abs(Math.sin(a))*r,f*2);
            default -> center.add(Math.cos(a)*r,Math.sin(a)*r,Math.sin(a*3)*r*.5);
        };
    }
    void render(WorldRenderContext context,List<DuelVfxTimeline.Instance> active,long now) {
        if(context.consumers()==null||context.matrixStack()==null)return;
        for(var v:active) {
            if(context.camera().getPos().distanceTo(v.source)>v.profile.distanceCull())continue;
            maxSegments=Math.clamp(v.profile.maxTrailSegments(),8,64);double t=v.progress(now);int color=(int)((1-t)*180+50)<<24|v.profile.color()&0xFFFFFF;
            String semantic=v.cue.semantic();
            if(semantic.startsWith("SUMMON")||semantic.equals("REVIVE")) {
                ring(context,v.source.add(0,.045,0),1.1+t*1.8,color,48);
                ring(context,v.source.add(0,.05,0),.8+t*1.2,color,32);
                for(int i=0;i<6;i++){double a=i*Math.PI/3+t;Vec3d p=v.source.add(Math.cos(a)*2,.05,Math.sin(a)*2);line(context,p,p.add(Math.cos(a)*.6,0,Math.sin(a)*.6),.04f,color);}
                if(t>.25&&t<.72){for(int i=0;i<5;i++)ring(context,v.source.add(0,(i+t*4)%5*.6,0),1.05,color,24);}
            } else if(semantic.equals("ATTACK_PHYSICAL")) {
                Vec3d contact=v.source.add(0,1,0).add(v.target.subtract(v.source).multiply(Math.min(1,t/v.fraction("impact"))));
                line(context,v.source.add(0,1,0),contact,.07f,color);
                if(t>v.fraction("impact")-.1)ring(context,v.target.add(0,.06,0),.4+(t-v.fraction("impact")+.1)*6,color,32);
            } else if(semantic.equals("ATTACK_SPECIAL")||Set.of("PROJECTILE","BEAM","AOE").contains(semantic)) {
                if(t<v.fraction("release"))orb(context,v.source.add(0,1.4,0),.25+t,color);
                else if(v.mode().equals("BEAM")||semantic.equals("BEAM")){line(context,v.source.add(0,1.5,0),v.target.add(0,1.2,0),.18f,color);line(context,v.source.add(0,1.5,0),v.target.add(0,1.2,0),.04f,0xEEFFFFFF);}
                else if(v.mode().equals("AOE")||semantic.equals("AOE"))ring(context,v.target.add(0,.06,0),t*7,color,64);
                else {Vec3d p=projectile(v,t);orb(context,p,.32,color);line(context,v.source.add(0,1.2,0),p,.035f,color);}
            } else if(semantic.equals("CHAIN_NEGATE")) {
                Vec3d p=v.target.add(0,1.4,0);ring(context,p,1.1,0xDDB775ED,40);
                line(context,p.add(-.8,-.8,0),p.add(.8,.8,0),.12f,0xEED786AA);line(context,p.add(-.8,.8,0),p.add(.8,-.8,0),.12f,0xEED786AA);
                label(context,p.add(0,1,0),Text.translatable("cardworlds.ui.negated").getString(),0xFFFF9FBB);
            } else if(Set.of("SPELL_ACTIVATE","TRAP_REVEAL","CHAIN_LINK","CHAIN_RESOLVE").contains(semantic)) {
                int frame=semantic.equals("TRAP_REVEAL")?0xCCD873D9:0xCC73DDE6;
                ring(context,v.source.add(0,.07,0),1.5+t,frame,36);
                line(context,v.source.add(0,.5,0),v.target.add(0,.5,0),.035f,frame);
                if(v.cue.link()>0)label(context,v.source.add(0,2.2,0),Text.translatable("cardworlds.ui.chain_link_number",v.cue.link()).getString(),0xFFEFD2FF);
            } else {
                Vec3d p=Set.of("DESTROY","BANISH","RETURN_HAND","RETURN_DECK","SEND_GRAVE").contains(semantic)?v.source:v.target;
                ring(context,p.add(0,.04,0),.7+t*2,color,32);
                if(semantic.equals("SHIELD"))orb(context,p.add(0,1.2,0),1.1,color);
                else if(semantic.equals("BUFF")||semantic.equals("HEAL"))line(context,p.add(0,.2,0),p.add(0,2.5,0),.05f,color);
                else if(Set.of("RETURN_HAND","RETURN_DECK","DRAW","SEARCH","DISCARD","MILL","SEND_GRAVE").contains(semantic))line(context,v.source.add(0,1+t,0),v.target.add(0,.3,0),.07f,color);
                else if(semantic.equals("BANISH"))orb(context,p.add(0,1.2,0),1.5*(1-t),0xB85C398F);
            }
        }
    }
    void persistent(WorldRenderContext context,Vec3d base,int color,long now) {
        ring(context,base.add(0,.035,0),1.6+Math.sin(now*.001)*.06,0x55FFFFFF&color|0x55000000,24);
    }
    private void ring(WorldRenderContext context,Vec3d center,double radius,int color,int segments) {
        segments=Math.min(segments,maxSegments);for(int i=0;i<segments;i++){double a=i*Math.PI*2/segments,b=(i+1)*Math.PI*2/segments;line(context,center.add(Math.cos(a)*radius,0,Math.sin(a)*radius),center.add(Math.cos(b)*radius,0,Math.sin(b)*radius),.025f,color);}
    }
    private void orb(WorldRenderContext context,Vec3d center,double radius,int color) {
        var stack=context.matrixStack();stack.push();Vec3d camera=context.camera().getPos();stack.translate(center.x-camera.x,center.y-camera.y,center.z-camera.z);stack.multiply(context.camera().getRotation());
        var buffer=context.consumers().getBuffer(RenderLayer.getEntityTranslucent(WHITE));var entry=stack.peek();
        float r=(float)radius;
        vertex(buffer,entry,-r,-r,0,0,0,color);vertex(buffer,entry,-r,r,0,0,1,color);vertex(buffer,entry,r,r,0,1,1,color);vertex(buffer,entry,r,-r,0,1,0,color);
        stack.pop();
    }
    private void line(WorldRenderContext context,Vec3d a,Vec3d b,float width,int color) {
        var stack=context.matrixStack();stack.push();Vec3d camera=context.camera().getPos();stack.translate(-camera.x,-camera.y,-camera.z);
        Vec3d normal=b.subtract(a).crossProduct(camera.subtract(a)).normalize().multiply(width);var buffer=context.consumers().getBuffer(RenderLayer.getEntityTranslucent(WHITE));var entry=stack.peek();
        Vec3d[] p={a.subtract(normal),a.add(normal),b.add(normal),b.subtract(normal)};
        for(int i=0;i<4;i++)vertex(buffer,entry,(float)p[i].x,(float)p[i].y,(float)p[i].z,i<2?0:1,i==0||i==3?0:1,color);
        stack.pop();
    }
    private void vertex(VertexConsumer buffer,MatrixStack.Entry entry,float x,float y,float z,float u,float v,int color) {
        buffer.vertex(entry,x,y,z).color(color).texture(u,v).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(entry,0,1,0);
    }
    private void label(WorldRenderContext context,Vec3d p,String text,int color) {
        var client=MinecraftClient.getInstance();var stack=context.matrixStack();stack.push();Vec3d camera=context.camera().getPos();stack.translate(p.x-camera.x,p.y-camera.y,p.z-camera.z);stack.multiply(context.camera().getRotation());stack.scale(-.022f,-.022f,.022f);
        client.textRenderer.draw(text,-client.textRenderer.getWidth(text)/2f,0,color,false,stack.peek().getPositionMatrix(),context.consumers(),net.minecraft.client.font.TextRenderer.TextLayerType.SEE_THROUGH,0,0xF000F0);stack.pop();
    }
}
