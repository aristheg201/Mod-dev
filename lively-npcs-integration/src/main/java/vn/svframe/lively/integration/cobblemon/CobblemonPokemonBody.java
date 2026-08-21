package vn.svframe.lively.integration.cobblemon;

import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import vn.svframe.lively.npc.NpcBody;
import vn.svframe.lively.npc.NpcDefinition;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Command-created Cobblemon body. It is not registered in Cobblemon's natural spawn system. */
public final class CobblemonPokemonBody implements NpcBody {
    private final UUID npcId;
    private PokemonEntity entity;

    public CobblemonPokemonBody(UUID npcId) { this.npcId = npcId; }
    @Override public UUID npcId(){return npcId;}
    @Override public NpcDefinition.BodyType type(){return NpcDefinition.BodyType.EXTERNAL;}
    @Override public boolean spawned(){return entity!=null&&!entity.isRemoved();}
    @Override public Optional<UUID> entityUuid(){return spawned()?Optional.of(entity.getUuid()):Optional.empty();}

    @Override
    public void spawn(MinecraftServer server,NpcDefinition definition){
        if(spawned())return;
        ServerWorld world=world(server,definition.world()); if(world==null)throw new IllegalArgumentException("unknown world");
        String properties=definition.bodyKey();
        if(properties.startsWith("cobblemon:"))properties=properties.substring("cobblemon:".length());
        PokemonProperties parsed=PokemonProperties.parse(properties);
        PokemonEntity created=parsed.createEntity(world);
        created.setUuid(npcId);
        created.refreshPositionAndAngles(definition.x(),definition.y(),definition.z(),definition.yaw(),definition.pitch());
        created.setCustomName(Text.literal(definition.name())); created.setCustomNameVisible(definition.nameVisible());
        created.setInvulnerable(definition.invulnerable()); created.setNoGravity(!definition.gravity()); created.setSilent(definition.silent());
        created.addCommandTag("lively"); created.addCommandTag("lively_body");
        created.setPersistent(); created.setAiDisabled(true);
        if(!world.spawnEntity(created))throw new IllegalStateException("Cobblemon world rejected Pokemon NPC body");
        entity=created;
    }

    @Override public void despawn(MinecraftServer server){if(entity!=null&&!entity.isRemoved())entity.discard();entity=null;}
    @Override public void teleport(MinecraftServer server,String worldKey,Vec3d position,float yaw,float pitch){
        if(!spawned())return; ServerWorld target=world(server,worldKey);if(target==null)return;
        if(entity.getServerWorld()!=target){despawn(server);return;}
        entity.refreshPositionAndAngles(position.x,position.y,position.z,yaw,pitch);
    }
    @Override public void lookAt(MinecraftServer server,Vec3d target){if(!spawned())return;Vec3d d=target.subtract(entity.getPos());double h=Math.sqrt(d.x*d.x+d.z*d.z);float yaw=(float)Math.toDegrees(Math.atan2(-d.x,d.z));float pitch=(float)-Math.toDegrees(Math.atan2(d.y,h));entity.setYaw(yaw);entity.setPitch(pitch);entity.setHeadYaw(yaw);}
    @Override public void tick(MinecraftServer server,NpcDefinition definition){if(!spawned())return;entity.setInvulnerable(definition.invulnerable());entity.setNoGravity(!definition.gravity());}
    private static ServerWorld world(MinecraftServer server,String key){Identifier id=Identifier.tryParse(key);return id==null?null:server.getWorld(RegistryKey.of(RegistryKeys.WORLD,id));}
}
