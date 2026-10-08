package io.github.aristheg201.cobblemonworld.narrative;

import com.cobblemon.mod.common.entity.npc.NPCEntity;
import io.github.aristheg201.cobblemonworld.npc.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;

/** Composition with Cobblemon's final NPCEntity; no reflection or class patching. */
public final class PersonalActors {
    private record Actor(UUID owner,NPCEntity npc){}
    private static final Map<String,Actor> ACTIVE=new HashMap<>();
    private static final Map<UUID,UUID> OWNERS=new HashMap<>();
    public static boolean personal(String id){return Arrays.stream(NarrativeRegistry.INSTANCE.data.actors()).anyMatch(a->a.id().equals(id) && "personal".equals(a.spawnPolicy()));}
    public static NPCEntity find(ServerPlayer p,String id){var actor=ACTIVE.get(p.getUUID()+":"+id);return actor==null?null:actor.npc();}
    public static UUID owner(NPCEntity npc){return OWNERS.get(npc.getUUID());}
    public static void register(){
        ServerLifecycleEvents.SERVER_STOPPING.register(s->{ACTIVE.values().forEach(a->a.npc().discard());ACTIVE.clear();OWNERS.clear();});
        ServerTickEvents.END_SERVER_TICK.register(server->{
            var iterator=ACTIVE.entrySet().iterator();while(iterator.hasNext()){
                var actor=iterator.next().getValue();var p=server.getPlayerList().getPlayer(actor.owner());
                if(p==null || actor.npc().isRemoved()){actor.npc().discard();OWNERS.remove(actor.npc().getUUID());iterator.remove();}
            }
            for(var p:server.getPlayerList().getPlayers())for(var actor:NarrativeRegistry.INSTANCE.data.actors()){
                if(!"personal".equals(actor.spawnPolicy()))continue;
                var placement=NpcPlacementStore.INSTANCE.get(actor.id());if(placement==null)continue;
                var n=NarrativeEngine.state(p).narrative;
                boolean visible=!n.finished.contains(actor.hideAfterStage()) || n.chains.values().contains(actor.reappearStage());
                String key=p.getUUID()+":"+actor.id();var instance=ACTIVE.get(key);
                if(!visible || !NarrativeEngine.near(p,actor.id(),48)){
                    if(instance!=null){instance.npc().discard();OWNERS.remove(instance.npc().getUUID());ACTIVE.remove(key);}continue;
                }
                if(instance==null){
                    var created=TrainerBattleService.createNpc(p,NpcDefinitionRegistry.INSTANCE.get(actor.id()));
                    created.moveTo(placement.x(),placement.y(),placement.z(),placement.yaw(),0);AnchoredNpcService.authoredPose(created,placement.yaw());
                    OWNERS.put(created.getUUID(),p.getUUID());
                    if(p.serverLevel().addFreshEntity(created)){instance=new Actor(p.getUUID(),created);ACTIVE.put(key,instance);}else OWNERS.remove(created.getUUID());
                }
                if(instance!=null)AnchoredNpcService.tickPose(instance.npc(),placement);
            }
        });
    }
}
