package vn.worldcomesalive.world;
import net.minecraft.entity.*;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.world.World;

/** A citizen is only a visible representation. Identity, inventory and decisions live in WorldStore. */
public final class CitizenEntity extends VillagerEntity {
    private static final net.minecraft.entity.data.TrackedData<String> PRESENTATION=net.minecraft.entity.data.DataTracker.registerData(CitizenEntity.class,net.minecraft.entity.data.TrackedDataHandlerRegistry.STRING);
    @Override protected void initDataTracker(net.minecraft.entity.data.DataTracker.Builder builder){super.initDataTracker(builder);builder.add(PRESENTATION,"male:0");}
    public String presentation(){return dataTracker.get(PRESENTATION);}
    public void presentation(String gender,int skin){dataTracker.set(PRESENTATION,gender+":"+Math.floorMod(skin,4));}

    public CitizenEntity(EntityType<? extends VillagerEntity> type,World world){super(type,world);setPersistent();}
    @Override protected void initGoals(){}
    @Override protected void mobTick(){/* The world simulation owns cognition and schedules. */}
    @Override public void tick(){super.tick();if(getWorld().isClient)return;if(vn.worldcomesalive.server.WorldSimulation.active()!=null)vn.worldcomesalive.server.WorldSimulation.active().perceive(this);}
}
