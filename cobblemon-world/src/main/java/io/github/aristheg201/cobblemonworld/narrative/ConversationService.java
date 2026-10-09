package io.github.aristheg201.cobblemonworld.narrative;

import com.google.gson.Gson;
import com.cobblemon.mod.common.entity.npc.NPCEntity;
import io.github.aristheg201.cobblemonworld.network.*;
import io.github.aristheg201.cobblemonworld.npc.*;
import io.github.aristheg201.cobblemonworld.progression.ProgressionStore;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;

/** A choice is resolved against a server-held, distance-bound, revisioned session. */
public final class ConversationService {
    public record Option(String id,String text,boolean optional){
        public Option(String id,String text){this(id,text,false);}
    }
    public record Line(String speaker,String text){}
    public record Snapshot(UUID session,int revision,String speaker,String text,String replySpeaker,String reply,List<Option> choices,String status,List<Line> history){}
    private static final Map<UUID,Session> SESSIONS=new HashMap<>();
    private record Pending(String id, boolean won, int due){}
    private static final Map<UUID, Pending> OUTCOMES=new HashMap<>();
    private static final Gson GSON=new Gson();
    private static final class Session {
        UUID id=UUID.randomUUID(); int revision; String target,scene,node,stage,reply="",replySpeaker="",status="";
        UUID npc; String offeredChain; Map<String,String> menu=new LinkedHashMap<>();
    }
    public static void register(){
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server->{
            var iterator=OUTCOMES.entrySet().iterator();while(iterator.hasNext()) {
                var entry=iterator.next();var player=server.getPlayerList().getPlayer(entry.getKey());
                if(player==null){iterator.remove();continue;}
                if(server.getTickCount()>=entry.getValue().due() && com.cobblemon.mod.common.Cobblemon.INSTANCE.getBattleRegistry().getBattleByParticipatingPlayer(player)==null){
                    showOutcome(player,entry.getValue().id(),entry.getValue().won());iterator.remove();
                }
            }
        });
        PayloadTypeRegistry.playS2C().register(ConversationPayload.TYPE,ConversationPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ConversationClosePayload.TYPE,ConversationClosePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ConversationChoicePayload.TYPE,ConversationChoicePayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ConversationChoicePayload.TYPE,(payload,context)->context.server().execute(()->choose(context.player(),payload)));
    }
    public static void close(ServerPlayer p){var s=SESSIONS.remove(p.getUUID());if(s!=null && ServerPlayNetworking.canSend(p,ConversationClosePayload.TYPE))ServerPlayNetworking.send(p,new ConversationClosePayload(s.id));}
    public static boolean openNpc(ServerPlayer p,NPCEntity npc,String id){
        NarrativeEngine.migrate(p);var s=new Session();s.target=id;s.npc=npc.getUUID();
        for(var stage:NarrativeEngine.active(p))if(stage.target().equals(id) && !stage.type().equals("travel") && !stage.type().equals("collect"))s.menu.put("stage:"+stage.id(),stage.title());
        for(var chain:NarrativeRegistry.INSTANCE.chains.values())if(chain.giver().equals(id) && NarrativeEngine.available(p,chain))s.menu.put("offer:"+chain.id(),chain.title());
        var def=NpcDefinitionRegistry.INSTANCE.get(id);
        if(def!=null && def.team()!=null && def.team().length>0 && NarrativeEngine.state(p).narrative.finished.contains("battle:"+id))s.menu.put("rematch","narrative.cobblemonworld.rematch");
        if (java.util.Set.of("pokemall_ren","fashion_elle","bicycle_tomo","daycare_mira").contains(id)) s.menu.put("service","narrative.cobblemonworld.service");
        s.menu.put("leave","narrative.cobblemonworld.leave");SESSIONS.put(p.getUUID(),s);send(p,s);return true;
    }
    public static boolean openPoint(ServerPlayer p,String id){
        if(!NarrativeEngine.near(p,id,8))return false;
        NarrativeEngine.migrate(p);
        var stage=NarrativeEngine.active(p).stream().filter(a->a.target().equals(id) && a.type().equals("inspect")).findFirst().orElse(null);
        if(stage==null)return false;SeasonalSightings.show(p,id);var s=new Session();s.target=id;setStage(p,s,stage);SESSIONS.put(p.getUUID(),s);send(p,s);return true;
    }
    public static void outcome(ServerPlayer p,String id,boolean won){OUTCOMES.put(p.getUUID(),new Pending(id,won,p.getServer().getTickCount()+80));}
    private static void showOutcome(ServerPlayer p,String id,boolean won){
        String scene="outcome."+id+"."+(won?"win":"loss");
        if(!NarrativeRegistry.INSTANCE.scenes.containsKey(scene))return;
        var s=new Session();s.target=id;s.scene=scene;s.node=NarrativeRegistry.INSTANCE.scenes.get(scene).start();
        // Battle outcomes are shown after the battle GUI closes; choices cannot start a remote battle.
        SESSIONS.put(p.getUUID(),s);send(p,s);
    }
    public static boolean openFinal(ServerPlayer p) {
        var stage=NarrativeEngine.active(p).stream().filter(a->a.target().equals("mysterious")).findFirst().orElse(null);
        if(stage==null)return false;var s=new Session();s.target="mysterious";setStage(p,s,stage);SESSIONS.put(p.getUUID(),s);send(p,s);return true;
    }
    private static boolean bound(ServerPlayer p,Session s){
        if("mysterious".equals(s.target)){var c=io.github.aristheg201.cobblemonworld.config.CWorldConfig.INSTANCE;return c.finalEncounterEnabled && c.finalEncounterDimension.equals(p.level().dimension().location().toString()) && p.distanceToSqr(c.finalEncounterX,c.finalEncounterY,c.finalEncounterZ)<=64;}
        if(s.npc!=null){var e=p.serverLevel().getEntity(s.npc);return e instanceof NPCEntity npc && npc.isAlive() && e.distanceToSqr(p)<=64
                && npc.getInteraction() instanceof CWorldNpcInteraction binding && s.target.equals(binding.definitionId())
                && (PersonalActors.owner(npc)==null || p.getUUID().equals(PersonalActors.owner(npc)));}
        return NarrativeEngine.near(p,s.target,8) || s.scene!=null && s.scene.startsWith("outcome.");
    }
    private static void setStage(ServerPlayer p,Session s,NarrativeRegistry.Stage stage){
        s.stage=stage.id();s.scene=stage.scene();var scene=NarrativeRegistry.INSTANCE.scenes.get(s.scene);
        var saved=NarrativeEngine.state(p).narrative.sceneNodes.get(s.scene);s.node=NarrativeRegistry.INSTANCE.node(scene,saved)==null?scene.start():saved;
        if(saved!=null) {
            var history=NarrativeEngine.state(p).narrative.transcript;
            for(int i=history.size()-1;i>=0;i--){var turn=history.get(i);
                if(turn.scene().equals(s.scene) && turn.speaker().equals(p.getGameProfile().getName())) {s.reply=turn.textKey();s.replySpeaker=turn.speaker();break;}
            }
        }
    }
    public static void choose(ServerPlayer p,ConversationChoicePayload request){
        var s=SESSIONS.get(p.getUUID());
        if(s==null){if(ServerPlayNetworking.canSend(p,ConversationClosePayload.TYPE))ServerPlayNetworking.send(p,new ConversationClosePayload(request.session()));p.sendSystemMessage(net.minecraft.network.chat.Component.translatable("narrative.cobblemonworld.stale"));return;}
        if(!s.id.equals(request.session()) || s.revision!=request.revision()){reject(p,s,"narrative.cobblemonworld.stale");return;}
        if(!bound(p,s)){close(p);p.sendSystemMessage(net.minecraft.network.chat.Component.translatable("narrative.cobblemonworld.out_of_range"));return;}
        if(s.scene==null){
            if(!s.menu.containsKey(request.choice())){reject(p,s,"narrative.cobblemonworld.stale");return;}
            s.revision++;
            if(request.choice().equals("leave")){close(p);return;}
            if(request.choice().equals("service")){close(p);var entity=p.serverLevel().getEntity(s.npc);if(entity instanceof NPCEntity npc)NpcService.dispatch(npc,p,s.target);return;}
            if(request.choice().equals("rematch")){var e=p.serverLevel().getEntity(s.npc);var result=e instanceof NPCEntity npc?TrainerBattleService.tryStartBattle(npc,p,s.target):new TrainerBattleService.BattleAttempt(false,"battle.cobblemonworld.unavailable");if(result.started())close(p);else reject(p,s,result.reason());return;}
            String id=request.choice().substring(request.choice().indexOf(':')+1);
            if(request.choice().startsWith("offer:")){
                var c=NarrativeRegistry.INSTANCE.chains.get(id);
                if(c==null || !NarrativeEngine.available(p,c)){reject(p,s,"narrative.cobblemonworld.stale");return;}
                s.offeredChain=id;setStage(p,s,c.stages()[0]);
            }else {var stage=NarrativeRegistry.INSTANCE.stages.get(id);if(stage==null || !NarrativeEngine.active(p).contains(stage)){reject(p,s,"narrative.cobblemonworld.stale");return;}setStage(p,s,stage);}
            send(p,s);return;
        }
        var scene=NarrativeRegistry.INSTANCE.scenes.get(s.scene);var node=NarrativeRegistry.INSTANCE.node(scene,s.node);
        var choice=Arrays.stream(node.choices()).filter(c->c.id().equals(request.choice())).findFirst().orElse(null);if(choice==null){reject(p,s,"narrative.cobblemonworld.stale");return;}
        if ("scam".equals(choice.action()) && !NarrativeRewards.scam(p)) { s.status="narrative.cobblemonworld.scam_unavailable";send(p,s);return; }
        s.revision++;var n=NarrativeEngine.state(p).narrative;
        n.transcript.add(new NarrativeState.Turn(s.scene,node.id(),node.speaker(),node.text()));
        n.transcript.add(new NarrativeState.Turn(s.scene,node.id(),p.getGameProfile().getName(),choice.text()));
        // Bound history prevents an unbounded save growth from rematches and repeat menus.
        while(n.transcript.size()>4096)n.transcript.removeFirst();
        if(choice.personality()!=null && !choice.personality().isBlank())n.personality.merge(choice.personality(),1,(a,b)->Math.min(1000,a+b));
        s.reply=choice.text();s.replySpeaker=p.getGameProfile().getName();s.status="";
        if(choice.next()!=null && !choice.next().isBlank()){
            s.node=choice.next();n.sceneNodes.put(s.scene,s.node);ProgressionStore.INSTANCE.save();send(p,s);return;
        }
        var stage=NarrativeRegistry.INSTANCE.stages.get(s.stage);
        if("close".equals(choice.action())){n.sceneNodes.remove(s.scene);ProgressionStore.INSTANCE.save();close(p);return;}
        if(s.offeredChain!=null){
            if(!NarrativeEngine.activate(p,s.offeredChain)){reject(p,s,"narrative.cobblemonworld.stale");return;}
            s.offeredChain=null;
        }
        if(stage!=null && NarrativeEngine.active(p).contains(stage)){
            if(stage.type().equals("battle")){
                if(!"battle".equals(choice.action())){reject(p,s,"narrative.cobblemonworld.stale");return;}
                if(!NarrativeEngine.battleAllowed(p,stage.target())){reject(p,s,"battle.cobblemonworld.story_locked");return;}
                if("mysterious".equals(stage.target())){
                    var result=io.github.aristheg201.cobblemonworld.boss.TobaEncounterService.beginFromConversation(p);
                    if(result.started()){n.sceneNodes.remove(s.scene);ProgressionStore.INSTANCE.save();close(p);}else reject(p,s,result.reason());
                }
                else {var entity=p.serverLevel().getEntity(s.npc);var result=entity instanceof NPCEntity npc?TrainerBattleService.tryStartBattle(npc,p,stage.target()):new TrainerBattleService.BattleAttempt(false,"battle.cobblemonworld.unavailable");
                    if(result.started()){n.sceneNodes.remove(s.scene);ProgressionStore.INSTANCE.save();close(p);}else reject(p,s,result.reason());
                }
                return;
            }
            if(stage.type().equals("buy") || stage.type().equals("heal")){
                ProgressionStore.INSTANCE.save();close(p);var entity=p.serverLevel().getEntity(s.npc);
                if(entity instanceof NPCEntity npc)NpcService.dispatch(npc,p,stage.target());
                return;
            }
            if(stage.type().equals("claim")){
                if(!SeasonalRewards.claim(p,stage.item())){s.status="narrative.cobblemonworld.claim_failed";send(p,s);return;}
                NarrativeEngine.complete(p,stage);
            }else if(!NarrativeEngine.finishConversation(p,stage)){
                s.status="narrative.cobblemonworld.need_items";ProgressionStore.INSTANCE.save();send(p,s);return;
            }
        }
        n.sceneNodes.remove(s.scene);ProgressionStore.INSTANCE.save();close(p);
    }
    private static void reject(ServerPlayer p,Session s,String reason){
        io.github.aristheg201.cobblemonworld.CobblemonWorldMod.LOGGER.info("Conversation action rejected player={} target={} scene={} revision={} reason={}",p.getUUID(),s.target,s.scene,s.revision,reason);
        s.status=reason;send(p,s);
    }
    private static void send(ServerPlayer p,Session s){
        String speaker,text;List<Option> options;
        if(s.scene==null){var def=NpcDefinitionRegistry.INSTANCE.get(s.target);speaker=def==null?"???":def.displayName();
            var state=NarrativeEngine.state(p);String variant=state.storyFlags.contains("main_story_complete")?"epilogue":state.storyFlags.contains("royal_league_complete")?"champion":state.storyFlags.contains("rocket_lab_resolved")?"rocket":"greeting";
            text="narrative.actor."+s.target+"."+variant;
            if(s.target.equals("rook") && state.narrative.personality.getOrDefault("greedy",0)>=3)text="narrative.actor.rook.greedy";
            if(s.target.equals("professor_hale") && state.narrative.personality.getOrDefault("sleepy",0)>=3)text="narrative.actor.professor_hale.sleepy";
            if(!NarrativeContentKeys.has(text))text="narrative.actor."+s.target+".greeting";
            options=s.menu.entrySet().stream().map(e->new Option(e.getKey(),e.getValue(),e.getKey().startsWith("offer:") ||
                    e.getKey().startsWith("stage:") && NarrativeRegistry.INSTANCE.chains.values().stream()
                            .anyMatch(c->Arrays.stream(c.stages()).anyMatch(stage->e.getKey().equals("stage:"+stage.id()))))).toList();
        }else{var node=NarrativeRegistry.INSTANCE.node(NarrativeRegistry.INSTANCE.scenes.get(s.scene),s.node);var def=NpcDefinitionRegistry.INSTANCE.get(node.speaker());
            speaker=def==null?("narrator".equals(node.speaker())?"narrative.cobblemonworld.observation":"mysterious".equals(node.speaker())?"???":node.speaker()):def.displayName();if(node.speaker().equals("mysterious") && NarrativeEngine.state(p).storyFlags.contains("toba_identity_revealed"))speaker="TOBA";text=node.text();
            options=Arrays.stream(node.choices()).map(c->new Option(c.id(),c.text())).toList();}
        var history=new ArrayList<Line>();
        for(var turn:NarrativeEngine.state(p).narrative.transcript){
            var stage=NarrativeRegistry.INSTANCE.stages.get(turn.scene());
            if(!(turn.scene().equals(s.scene) || stage!=null && stage.target().equals(s.target) || turn.scene().startsWith("outcome."+s.target+".")))continue;
            var actor=NpcDefinitionRegistry.INSTANCE.get(turn.speaker());String name=actor==null?turn.speaker():actor.displayName();
            if(turn.speaker().equals("narrator"))name="narrative.cobblemonworld.observation";
            if(turn.speaker().equals("mysterious") && NarrativeEngine.state(p).storyFlags.contains("toba_identity_revealed"))name="TOBA";
            history.add(new Line(name,turn.textKey()));if(history.size()>120)history.removeFirst();
        }
        ServerPlayNetworking.send(p,new ConversationPayload(GSON.toJson(new Snapshot(s.id,s.revision,speaker,text,s.replySpeaker,s.reply,options,s.status,history))));
    }
}
