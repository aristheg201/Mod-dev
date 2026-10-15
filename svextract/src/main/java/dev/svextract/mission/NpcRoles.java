package dev.svextract.mission;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;

/** Explicit service roles; combat allies and opponents never inherit hub protection. */
public final class NpcRoles {
    public enum Role { MISSION_CONTROL, MERCHANT, QUEST_OFFICER }
    private static final String PROTECTED="svextract_protected_service";
    private NpcRoles(){}
    public static boolean protectedService(Entity entity){
        return entity.getCommandTags().contains(PROTECTED)
            ||entity.getCommandTags().contains("svextract_mission_control");
    }
    public static void protect(LivingEntity entity,Role role){
        entity.addCommandTag(PROTECTED);
        entity.addCommandTag("svextract_role_"+role.name().toLowerCase(java.util.Locale.ROOT));
        entity.setInvulnerable(true);
        entity.setHealth(entity.getMaxHealth());
        if(entity instanceof MobEntity mob){mob.setAiDisabled(true);mob.setPersistent();}
    }
    public static void restoreProtection(LivingEntity entity){
        Role role=Role.MISSION_CONTROL;
        for(Role candidate:Role.values())if(entity.getCommandTags().contains("svextract_role_"+candidate.name().toLowerCase(java.util.Locale.ROOT))){role=candidate;break;}
        protect(entity,role);
    }
}
