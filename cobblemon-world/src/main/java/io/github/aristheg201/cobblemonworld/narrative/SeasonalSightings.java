package io.github.aristheg201.cobblemonworld.narrative;

import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import java.util.*;
import kotlin.Unit;

/** Story sightings are visible, uncatchable and unbattleable. They never grant an early Legendary. */
public final class SeasonalSightings {
    private record Sighting(UUID owner,PokemonEntity entity,int expires){}
    private static final Map<String,Sighting> ACTIVE=new HashMap<>();
    public static void register(){
        ServerTickEvents.END_SERVER_TICK.register(server->{var iterator=ACTIVE.values().iterator();while(iterator.hasNext()){
            var s=iterator.next();if(server.getTickCount()>s.expires() || server.getPlayerList().getPlayer(s.owner())==null || s.entity().isRemoved()){s.entity().discard();iterator.remove();}
        }});
        ServerLifecycleEvents.SERVER_STOPPING.register(server->{ACTIVE.values().forEach(s->s.entity().discard());ACTIVE.clear();});
        ServerEntityEvents.ENTITY_LOAD.register((entity,world)->{if(entity.getTags().contains("cworld_season_sighting") && ACTIVE.values().stream().noneMatch(s->s.entity().getUUID().equals(entity.getUUID())))entity.discard();});
    }
    public static void show(ServerPlayer p,String point){
        var definition=NarrativeRegistry.INSTANCE.pois.get(point);if(definition==null || definition.sighting()==null || definition.sighting().isBlank())return;
        var place=PoiStore.PLACES.get(point);if(place==null)return;String key=p.getUUID()+":"+point;if(ACTIVE.containsKey(key))return;
        var pokemon=PokemonProperties.Companion.parse(definition.sighting()).create(p);
        if(!pokemon.isUncatchable())throw new IllegalStateException("Season sighting must remain uncatchable");
        pokemon.sendOut(p.serverLevel(),new Vec3(place.x()+2,place.y()+1,place.z()+2),null,entity->{
            entity.setNoAi(true);entity.setInvulnerable(true);entity.getEntityData().set(PokemonEntity.getUNBATTLEABLE(),true);
            entity.setYRot(135);entity.setXRot(0);ACTIVE.put(key,new Sighting(p.getUUID(),entity,p.getServer().getTickCount()+1200));entity.addTag("cworld_season_sighting");return Unit.INSTANCE;
        });
    }
}
