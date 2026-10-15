package dev.svextract.mission;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.PillagerEntity;
import net.minecraft.world.World;

/** Movement ticks normally; goals/path requests and gun decisions come from the bounded director. */
public final class CombatBotEntity extends PillagerEntity {
    public CombatBotEntity(EntityType<? extends PillagerEntity> type,World world){super(type,world);}
    @Override protected void initGoals(){}
}
