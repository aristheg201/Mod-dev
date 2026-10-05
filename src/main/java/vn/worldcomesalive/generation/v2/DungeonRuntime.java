package vn.worldcomesalive.generation.v2;
import vn.worldcomesalive.server.WorldSimulation;
import net.minecraft.entity.*;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.*;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.item.ItemStack;
import net.minecraft.block.entity.ChestBlockEntity;
/** Actual native occupants/loot, player discovery and persistent death consequences; no timer-based respawns. */
public final class DungeonRuntime {
    private final WorldSimulation sim;
    public DungeonRuntime(WorldSimulation sim){this.sim=sim;}
    public void tick(){if(sim.state.clock%40!=0)return;for(var town:sim.state.settlements.values()){var dungeon=town.generationPlan==null?null:town.generationPlan.dungeon;if(dungeon==null||!town.ready)continue;boolean near=sim.world.getPlayers().stream().anyMatch(p->p.squaredDistanceTo(dungeon.anchor.x(),dungeon.surface,dungeon.anchor.z())<14400);if(!near)continue;if(dungeon.state.equals("UNEXPLORED")&&sim.world.getPlayers().stream().anyMatch(p->p.squaredDistanceTo(dungeon.anchor.x(),dungeon.surface,dungeon.anchor.z())<2500)){dungeon.state="DISCOVERED";dungeon.discoveredAt=sim.state.clock;sim.state.revision++;}
        for(var loot:dungeon.loot)if(!loot.created&&sim.world.isChunkLoaded(loot.x>>4,loot.z>>4)&&sim.world.getBlockEntity(new BlockPos(loot.x,loot.y,loot.z)) instanceof ChestBlockEntity chest){int slot=0;for(var item:loot.items.entrySet()){var stack=new ItemStack(Registries.ITEM.get(Identifier.of(item.getKey())),item.getValue());chest.setStack(slot++,stack);}chest.markDirty();loot.created=true;}
        for(var occupant:dungeon.occupants.values())if(occupant.alive&&!occupant.spawned){var room=dungeon.chambers.stream().filter(r->r.id().equals(occupant.room)).findFirst().orElseThrow();var p=room.bounds().center();int y=dungeon.surface-room.level()*8+1;if(!sim.world.isChunkLoaded((int)p.x()>>4,(int)p.z()>>4))continue;Entity existing=sim.world.getEntity(occupant.id);if(existing!=null){occupant.spawned=true;continue;}EntityType<?> type=Registries.ENTITY_TYPE.get(Identifier.of(occupant.species));Entity e=type.create(sim.world);if(e instanceof MobEntity mob){mob.setUuid(occupant.id);mob.setPersistent();mob.refreshPositionAndAngles(p.x(),y,p.z(),0,0);if(occupant.danger>=3){mob.setCustomName(net.minecraft.text.Text.literal("Guardian of the Forgotten Crypt"));mob.setHealth(mob.getMaxHealth());}if(sim.world.spawnEntity(mob))occupant.spawned=true;}}
    }}
    public void death(java.util.UUID actor){for(var town:sim.state.settlements.values()){var dungeon=town.generationPlan==null?null:town.generationPlan.dungeon;if(dungeon!=null&&dungeon.death(actor,sim.state.clock)){sim.state.revision++;if(dungeon.state.equals("CLEARED")){var victim=sim.world.getEntity(actor);for(var id:town.residents){var npc=sim.npc(id);if(victim!=null&&npc.location.distance(new vn.worldcomesalive.model.LivingWorld.Pos(victim.getX(),victim.getY(),victim.getZ()))<32&&npc.profession.equals("guard"))sim.state.remember(npc,new vn.worldcomesalive.model.LivingWorld.Memory("local_dungeon_cleared",actor,dungeon.id,sim.state.clock,.9,.4,.9,"nearby patrol"));}}}}
    }
}
