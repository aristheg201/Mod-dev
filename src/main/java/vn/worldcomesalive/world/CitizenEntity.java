package vn.worldcomesalive.world;
import net.minecraft.entity.*;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.world.World;

/** A citizen is only a visible representation. Identity, inventory and decisions live in WorldStore. */
public final class CitizenEntity extends VillagerEntity {
    public CitizenEntity(EntityType<? extends VillagerEntity> type,World world){super(type,world);setPersistent();}
    @Override protected void initGoals(){}
    @Override protected void mobTick(){/* The world simulation owns cognition and schedules. */}
    @Override public void tick(){super.tick();if(getWorld().isClient)return;if(vn.worldcomesalive.server.WorldSimulation.active()!=null)vn.worldcomesalive.server.WorldSimulation.active().perceive(this);}
}
