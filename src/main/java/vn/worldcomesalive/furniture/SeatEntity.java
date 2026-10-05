package vn.worldcomesalive.furniture;

import net.minecraft.entity.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Transient mounting representation; furniture ownership and NPC identity remain in the world store. */
public final class SeatEntity extends Entity {
    public BlockPos chair=BlockPos.ORIGIN;
    public SeatEntity(EntityType<?> type,World world){super(type,world);noClip=true;setNoGravity(true);}
    @Override protected void initDataTracker(net.minecraft.entity.data.DataTracker.Builder builder){}
    @Override protected void readCustomDataFromNbt(NbtCompound nbt){chair=BlockPos.fromLong(nbt.getLong("Chair"));}
    @Override protected void writeCustomDataToNbt(NbtCompound nbt){nbt.putLong("Chair",chair.asLong());}
    @Override public void tick(){super.tick();if(!getWorld().isClient&&(getPassengerList().isEmpty()||!getWorld().getBlockState(chair).isIn(Semantics.SITTABLE)))discard();}
    @Override protected boolean canAddPassenger(Entity passenger){return getPassengerList().isEmpty();}
    @Override public boolean isAttackable(){return false;}
}
