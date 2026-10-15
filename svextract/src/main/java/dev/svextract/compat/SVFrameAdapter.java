package dev.svextract.compat;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;
import java.lang.reflect.*;
import java.util.*;

/** Public APIs verified against Cobblemon-MMO's SVFrameMMO branch, commit 6baffdb.
 * Does not attach/detach class skills, reset cooldowns, replace progression, or calculate a second hit.
 */
public final class SVFrameAdapter {
    private Class<?> mmo;
    private Method classes,playerData,restoreEquipment;
    public void initialize() throws ReflectiveOperationException{
        if(!present())return;
        mmo=Class.forName("vn.svframe.svframemmo.SVFrameMMO");
        classes=mmo.getMethod("classes");playerData=mmo.getMethod("playerData");
        restoreEquipment=Class.forName("vn.svframe.svframelib.fabric.runtime.PlayerStatInitialization").getMethod("restoreEquipment",ServerPlayerEntity.class);
    }
    public boolean present(){return FabricLoader.getInstance().isModLoaded("svframemmo");}
    public boolean ready(){return mmo!=null&&restoreEquipment!=null;}
    public Map<String,Object> profile(ServerPlayerEntity player)throws ReflectiveOperationException{
        if(!present())return Map.of("class","ADVENTURER","level",player.experienceLevel);
        Object data=data(player);return Map.of("class",data.getClass().getMethod("getClassId").invoke(data),"level",data.getClass().getMethod("getLevel").invoke(data));
    }
    public List<String> classIds() throws ReflectiveOperationException{
        if(!ready())return List.of();Object manager=classes.invoke(null);
        Collection<?> definitions=(Collection<?>)manager.getClass().getMethod("getAll").invoke(manager);
        List<String> ids=new ArrayList<>();for(Object definition:definitions)ids.add((String)definition.getClass().getMethod("getId").invoke(definition));
        return ids.stream().sorted().toList();
    }
    private Object data(ServerPlayerEntity p) throws ReflectiveOperationException{
        if(!ready())throw new IllegalStateException("Installed SVFrameMMO API is incompatible; mission transition blocked");
        Object manager=playerData.invoke(null);return manager.getClass().getMethod("get",UUID.class).invoke(manager,p.getUuid());
    }
    public void restoreStats(ServerPlayerEntity p,boolean medicalRecovery) throws ReflectiveOperationException{
        if(!present())return;
        Object data=data(p);
        data.getClass().getMethod("refreshClassStats").invoke(data);
        restoreEquipment.invoke(null,p);
        // Use MMO's authoritative health resource setter. ResourceUpdateReason.MECHANIC
        // fires normal healing events; no hurt/tilt packet is manufactured by SVEXTRACT.
        if(medicalRecovery){
            Class<?> resource=Class.forName("vn.svframe.svframemmo.api.player.profess.resource.PlayerResource");
            Class<?> reason=Class.forName("vn.svframe.svframelib.player.resource.ResourceUpdateReason");
            Object health=resource.getField("HEALTH").get(null),healing=reason.getField("MECHANIC").get(null);
            double maximum=((Number)data.getClass().getMethod("getMaxResource",resource).invoke(data,health)).doubleValue();
            data.getClass().getMethod("setResource",resource,double.class,reason).invoke(data,health,maximum,healing);
        }
    }
}
