package io.github.aristheg201.cobblemonworld.narrative;

import io.github.aristheg201.cobblemonworld.progression.*;
import io.github.aristheg201.cobblemonworld.story.*;
import io.github.aristheg201.cobblemonworld.npc.NpcPlacementStore;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import java.util.*;

public final class NarrativeEngine {
    private NarrativeEngine(){}
    public static PlayerProgression state(ServerPlayer p){return ProgressionStore.INSTANCE.getOrCreate(p.getUUID());}
    public static void register(){
        com.cobblemon.mod.common.api.events.CobblemonEvents.POKEMON_CAPTURED.subscribe(com.cobblemon.mod.common.api.Priority.NORMAL,event->{
            var player=event.getPlayer();for(var stage:active(player))if(stage.type().equals("capture") && stage.item().equalsIgnoreCase(event.getPokemon().getSpecies().getName()) && near(player,stage.target(),64))complete(player,stage);return kotlin.Unit.INSTANCE;
        });
        ServerPlayConnectionEvents.JOIN.register((handler,sender,server)->server.execute(()->{migrate(handler.player);ObjectiveService.sync(handler.player,true);}));
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->ConversationService.close(handler.player));
        ServerTickEvents.END_SERVER_TICK.register(server->{if(server.getTickCount()%10!=0)return;
            for(var p:server.getPlayerList().getPlayers()){
                migrate(p);
                if (server.getTickCount()%100 == 0) NarrativeRewards.retry(p);
                for(var s:active(p)) {
                    if(s.type().equals("travel") && near(p,s.target(),5))event(p,"travel",s.target());
                    else if(s.type().equals("collect") && inventoryCount(p,s.item())>=s.amount())complete(p,s);
                }
            }
        });
    }
    public static void migrate(ServerPlayer player){
        var p=state(player);var n=p.narrative;var r=NarrativeRegistry.INSTANCE;
        if(n.schema>=1)return;
        int skip=0;
        for(int i=0;i<r.data.campaign().length;i++){
            var s=r.data.campaign()[i];
            if(s.legacyFlag()!=null && !s.legacyFlag().isBlank() && p.storyFlags.contains(s.legacyFlag()))skip=i+1;
        }
        if(p.storyFlags.contains("main_story_complete"))skip=r.data.campaign().length;
        for(int i=0;i<skip;i++){var stage=r.data.campaign()[i];n.finished.add(stage.id());if(stage.type().equals("battle"))n.finished.add("battle:"+stage.target());if(stage.flags()!=null)p.storyFlags.addAll(java.util.List.of(stage.flags()));}
        n.main=skip<r.data.campaign().length?r.data.campaign()[skip].id():"";n.schema=1;
        ProgressionStore.INSTANCE.save();
        io.github.aristheg201.cobblemonworld.CobblemonWorldMod.LOGGER.info("Narrative migration player={} inferred={} next={}",player.getUUID(),skip,n.main);
    }
    public static NarrativeRegistry.Stage main(PlayerProgression p){return NarrativeRegistry.INSTANCE.stages.get(p.narrative.main);}
    public static List<NarrativeRegistry.Stage> active(ServerPlayer p){var n=state(p).narrative;var out=new ArrayList<NarrativeRegistry.Stage>();var main=main(state(p));if(main!=null)out.add(main);
        for(String id:n.chains.values()){var s=NarrativeRegistry.INSTANCE.stages.get(id);if(s!=null)out.add(s);}return out;}
    public static boolean available(ServerPlayer p,NarrativeRegistry.Chain c){var n=state(p).narrative;return !n.completedChains.contains(c.id()) && !n.chains.containsKey(c.id()) && (c.prerequisite()==null || c.prerequisite().isBlank() || state(p).storyFlags.contains(c.prerequisite()) || n.finished.contains(c.prerequisite()));}
    public static boolean activate(ServerPlayer p,String id){var c=NarrativeRegistry.INSTANCE.chains.get(id);if(c==null || !available(p,c))return false;state(p).narrative.chains.put(id,c.stages()[0].id());ProgressionStore.INSTANCE.save();ObjectiveService.sync(p,true);return true;}
    public static boolean near(ServerPlayer p,String target,double radius){var point=PoiStore.PLACES.get(target);if(point!=null)return point.near(p,radius);var pos=NpcPlacementStore.INSTANCE.get(target);
        return pos!=null && pos.dimension().equals(p.level().dimension().location().toString()) && p.distanceToSqr(pos.x(),pos.y(),pos.z())<=radius*radius;}
    public static int inventoryCount(ServerPlayer p,String id){if(id==null)return 0;var item=BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));int count=0;for(var stack:p.getInventory().items)if(stack.is(item))count+=stack.getCount();return count;}
    public static boolean finishConversation(ServerPlayer p,NarrativeRegistry.Stage s){
        if(!active(p).contains(s))return false;
        if(s.type().equals("talk") || s.type().equals("inspect")){complete(p,s);return true;}
        if(s.type().equals("deliver")){
            if(inventoryCount(p,s.item())<s.amount())return false;
            var item=BuiltInRegistries.ITEM.get(ResourceLocation.parse(s.item()));int remaining=s.amount();
            for(var stack:p.getInventory().items)if(stack.is(item)){int take=Math.min(remaining,stack.getCount());stack.shrink(take);remaining-=take;if(remaining==0)break;}
            p.getInventory().setChanged();complete(p,s);return true;
        }return false;
    }
    public static void event(ServerPlayer p,String type,String target){for(var s:active(p))if(s.type().equals(type) && s.target().equals(target)){
            if(type.equals("heal") && s.item()!=null && !s.item().isBlank()){boolean present=false;for(var member:com.cobblemon.mod.common.Cobblemon.INSTANCE.getStorage().getParty(p))if(member.getSpecies().getName().equalsIgnoreCase(s.item()) && member.isFullHealth())present=true;if(!present)continue;}
            complete(p,s);
        }}
    public static void complete(ServerPlayer player,NarrativeRegistry.Stage s){
        var p=state(player);var n=p.narrative;if(!active(player).contains(s) || !n.finished.add(s.id()))return;
        if(s.flags()!=null)for(String f:s.flags())CampaignService.setFlag(player,f);
        if(s.id().equals(n.main)){
            var campaign=NarrativeRegistry.INSTANCE.data.campaign();for(int i=0;i<campaign.length;i++)if(campaign[i].id().equals(s.id())){n.main=i+1<campaign.length?campaign[i+1].id():"";break;}
        }else for(var c:NarrativeRegistry.INSTANCE.chains.values())if(n.chains.containsKey(c.id()) && n.chains.get(c.id()).equals(s.id())){
            for(int i=0;i<c.stages().length;i++)if(c.stages()[i].id().equals(s.id())){
                if(i+1<c.stages().length)n.chains.put(c.id(),c.stages()[i+1].id());
                else {n.chains.remove(c.id());n.completedChains.add(c.id());p.completedSideQuests.add(c.id());NarrativeRewards.offer(player,c.id(),c.reward());
                    if(c.id().equals("shady_business") && n.scamPaid)NarrativeRewards.offer(player,"scam_refund",10);}
                break;
            }
            break;
        }
        if (io.github.aristheg201.cobblemonworld.story.ContentRegistry.INSTANCE.contact(s.target()) != null) CampaignService.unlockContact(player,s.target());
        CampaignService.setFlag(player,"narrative_stage_"+s.id());
        ProgressionStore.INSTANCE.save();ObjectiveService.sync(player,true);
    }
    public static PinnedObjective objective(PlayerProgression p,String chain){NarrativeRegistry.Stage s=chain.equals("main")?main(p):NarrativeRegistry.INSTANCE.stages.get(p.narrative.chains.get(chain));
        if(s==null)return null;boolean point=NarrativeRegistry.INSTANCE.pois.containsKey(s.target());return new PinnedObjective(chain,s.id(),point?"poi":"npc",s.target(),"",s.objective());}
    public static boolean battleAllowed(ServerPlayer p,String npc){
        var n=state(p).narrative;
        return n.finished.contains("battle:"+npc) || active(p).stream().anyMatch(s->s.type().equals("battle") && s.target().equals(npc));
    }
    public static void battleResult(ServerPlayer p,String npc,boolean won){
        var n=state(p).narrative;
        if(won){n.finished.add("battle:"+npc);event(p,"battle",npc);}
        else n.losses.merge(npc,1,Integer::sum);
        ProgressionStore.INSTANCE.save();
    }
}
