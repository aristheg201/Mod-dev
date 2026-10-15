package dev.svextract.compat;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import java.lang.reflect.Method;
import java.nio.file.*;

public final class SVFrameMobsAdapter {
    private Method spawn;
    public static void installMissionDefinition() throws java.io.IOException{
        if(!FabricLoader.getInstance().isModLoaded("svframemobs"))return;
        Path file=FabricLoader.getInstance().getConfigDir().resolve("SVFrameMobs/mobs/svextract-redline.yml");
        if(Files.exists(file))return;Files.createDirectories(file.getParent());
        Files.writeString(file,"""
                SVEXTRACT_RAILWORKER:
                  Type: ZOMBIE
                  Display: '&cFractured Railworker'
                  Health: 30
                  Damage: 4
                  Armor: 2
                  Equipment:
                    - minecraft:leather_helmet HEAD
                  Options:
                    NoAI: false
                    Silent: false
                """);
    }
    public void initialize() throws ReflectiveOperationException{
        if(!present())return;
        spawn=Class.forName("vn.svframe.svframemobs.SVFrameMobs").getMethod("spawnMissionMob",String.class,ServerWorld.class,Vec3d.class);
    }
    public boolean present(){return FabricLoader.getInstance().isModLoaded("svframemobs");}
    public MobEntity spawn(ServerWorld world,Vec3d position) throws ReflectiveOperationException{
        if(spawn==null)throw new IllegalStateException("SVFrameMobs needs the mission spawn API patch included in this private bundle");
        return (MobEntity)spawn.invoke(null,"SVEXTRACT_RAILWORKER",world,position);
    }
}
