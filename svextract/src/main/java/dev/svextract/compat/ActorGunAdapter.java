package dev.svextract.compat;

import dev.svextract.SvExtract;
import net.minecraft.block.Block;
import net.minecraft.entity.*;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.*;
import net.minecraft.util.math.Vec3d;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.Predicate;

/** Uses Point Blank's actual crosshair resolver, ammunition components and HurtingItem hit pipeline.
 * Initially supports nonexplosive, single-pellet hitscan modes only. No extra projectile entity.
 * NPCs receive a finite mission-issued magazine; there is no imitation player reload path.
 */
public final class ActorGunAdapter {
    public static final ThreadLocal<LivingEntity> HIT_ACTOR=new ThreadLocal<>();
    private Class<?> gunClass;
    private Method initialize,mode,ammo,setAmmo,decrement,capacity,hurt,ray,sound,volume,explosion,pellets;
    private static final Predicate<Block> NO_PENETRATION=block->false;
    public void initialize() throws ReflectiveOperationException {
        if(!Integrations.pointBlankLoaded())return;
        gunClass=Class.forName("com.vicmatskiv.pointblank.item.GunItem");Class<?> fireMode=Class.forName("com.vicmatskiv.pointblank.item.FireModeInstance");
        initialize=gunClass.getMethod("initStackForCrafting",ItemStack.class);mode=gunClass.getMethod("getFireModeInstance",ItemStack.class);
        ammo=gunClass.getMethod("getAmmo",ItemStack.class,fireMode);setAmmo=gunClass.getMethod("setAmmo",ItemStack.class,fireMode,int.class);
        decrement=gunClass.getMethod("decrementAmmo",ItemStack.class);capacity=gunClass.getMethod("getMaxAmmoCapacity",ItemStack.class,fireMode);
        explosion=gunClass.getMethod("getExplosion");pellets=fireMode.getMethod("getPelletCount");
        hurt=gunClass.getMethod("hurtEntity",LivingEntity.class,EntityHitResult.class,Entity.class,ItemStack.class);
        ray=Class.forName("com.vicmatskiv.pointblank.util.HitScan").getMethod("getNearestObjectInCrosshair",LivingEntity.class,float.class,double.class,Predicate.class,Predicate.class,List.class);
        sound=gunClass.getMethod("getFireSound");volume=gunClass.getMethod("getFireSoundVolume");
    }
    public boolean ready(){return gunClass!=null&&ray!=null;}
    public int remainingAmmo(ItemStack stack)throws ReflectiveOperationException{
        Object fireMode=mode.invoke(null,stack);return ((Number)ammo.invoke(null,stack,fireMode)).intValue();
    }
    /** A finite NPC reserve feeds the same Point Blank magazine component used by the firearm. */
    public int reload(ItemStack stack,int available)throws ReflectiveOperationException{
        Object fireMode=mode.invoke(null,stack);int max=((Number)capacity.invoke(stack.getItem(),stack,fireMode)).intValue();
        int old=remainingAmmo(stack),supplied=Math.max(0,Math.min(available,max-old));
        setAmmo.invoke(null,stack,fireMode,old+supplied);return supplied;
    }
    public void initializeLoot(ItemStack stack) throws ReflectiveOperationException {
        if(ready()&&gunClass.isInstance(stack.getItem()))initialize.invoke(null,stack);
    }
    public boolean supports(ItemStack stack) throws ReflectiveOperationException{
        if(!ready()||!gunClass.isInstance(stack.getItem())||explosion.invoke(stack.getItem())!=null)return false;
        // Point Blank 2.2 uses zero for an ordinary unspread hitscan; positive values
        // select the pellet-spread path. Both zero and one resolve one hit here.
        Object fireMode=mode.invoke(null,stack);
        int count=fireMode==null?-1:((Number)pellets.invoke(fireMode)).intValue();
        return count>=0&&count<=1;
    }
    public ItemStack missionLoan(String itemId) throws ReflectiveOperationException {
        Identifier id=Identifier.tryParse(itemId);
        if(id==null||!Registries.ITEM.containsId(id))throw new IllegalArgumentException("Unknown mission bot weapon: "+itemId);
        ItemStack weapon=new ItemStack(Registries.ITEM.get(id));initialize.invoke(null,weapon);
        if(!supports(weapon))throw new IllegalArgumentException("Mission bot weapon must be a nonexplosive single-pellet Point Blank gun");
        Object fireMode=mode.invoke(null,weapon);int max=((Number)capacity.invoke(weapon.getItem(),weapon,fireMode)).intValue();
        setAmmo.invoke(null,weapon,fireMode,max);return weapon;
    }
    public boolean fire(LivingEntity actor,LivingEntity target,ItemStack weapon,double range) throws ReflectiveOperationException {
        if(!supports(weapon)||!actor.isAlive()||!target.isAlive()||actor.getWorld()!=target.getWorld()||actor.squaredDistanceTo(target)>range*range)return false;
        Object fireMode=mode.invoke(null,weapon);
        if(((Number)ammo.invoke(null,weapon,fireMode)).intValue()<=0)return false;
        Vec3d aim=target.getBoundingBox().getCenter().subtract(actor.getEyePos());
        actor.setYaw((float)Math.toDegrees(Math.atan2(-aim.x,aim.z)));actor.setHeadYaw(actor.getYaw());
        actor.setPitch((float)-Math.toDegrees(Math.atan2(aim.y,Math.sqrt(aim.x*aim.x+aim.z*aim.z))));
        HitResult hit=(HitResult)ray.invoke(null,actor,1f,range,NO_PENETRATION,NO_PENETRATION,new ArrayList<>());
        // Preserve squad safety and line of sight. Never fire through an ally or a wall.
        if(!(hit instanceof EntityHitResult entityHit)||entityHit.getEntity()!=target)return false;
        decrement.invoke(null,weapon);
        HIT_ACTOR.set(actor);
        try{hurt.invoke(weapon.getItem(),actor,entityHit,null,weapon);}finally{HIT_ACTOR.remove();}
        SoundEvent fire=(SoundEvent)sound.invoke(weapon.getItem());float level=((Number)volume.invoke(weapon.getItem())).floatValue();
        if(fire!=null)((ServerWorld)actor.getWorld()).playSound(null,actor.getX(),actor.getY(),actor.getZ(),fire,SoundCategory.HOSTILE,level,1f);
        return true;
    }
}
