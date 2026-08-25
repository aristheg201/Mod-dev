package vn.svframe.mythiclibfabric.mixin;

import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vn.svframe.mythiclibfabric.MythicLibPassiveMod;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Fires TELEPORT from the actual successful ServerPlayerEntity teleport operation. */
@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerTeleportTriggerMixin {
    @Unique private String mythiclib$fromWorld;
    @Unique private double mythiclib$fromX;
    @Unique private double mythiclib$fromY;
    @Unique private double mythiclib$fromZ;

    @Inject(method = "teleport(Lnet/minecraft/server/world/ServerWorld;DDDLjava/util/Set;FF)Z", at = @At("HEAD"))
    private void mythiclib$captureTeleportOrigin(ServerWorld world,
                                                  double x,
                                                  double y,
                                                  double z,
                                                  Set<PositionFlag> flags,
                                                  float yaw,
                                                  float pitch,
                                                  CallbackInfoReturnable<Boolean> cir) {
        ServerPlayerEntity player = self();
        mythiclib$fromWorld = player.getServerWorld().getRegistryKey().getValue().toString();
        mythiclib$fromX = player.getX();
        mythiclib$fromY = player.getY();
        mythiclib$fromZ = player.getZ();
    }

    @Inject(method = "teleport(Lnet/minecraft/server/world/ServerWorld;DDDLjava/util/Set;FF)Z", at = @At("RETURN"))
    private void mythiclib$fireTeleport(ServerWorld world,
                                         double x,
                                         double y,
                                         double z,
                                         Set<PositionFlag> flags,
                                         float yaw,
                                         float pitch,
                                         CallbackInfoReturnable<Boolean> cir) {
        if (!Boolean.TRUE.equals(cir.getReturnValue())) return;
        ServerPlayerEntity player = self();
        String toWorld = player.getServerWorld().getRegistryKey().getValue().toString();
        double toX = player.getX();
        double toY = player.getY();
        double toZ = player.getZ();
        if (mythiclib$fromWorld != null
                && mythiclib$fromWorld.equals(toWorld)
                && mythiclib$fromX == toX && mythiclib$fromY == toY && mythiclib$fromZ == toZ) return;

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("from-world", mythiclib$fromWorld == null ? toWorld : mythiclib$fromWorld);
        context.put("to-world", toWorld);
        context.put("from-x", mythiclib$fromX);
        context.put("from-y", mythiclib$fromY);
        context.put("from-z", mythiclib$fromZ);
        context.put("to-x", toX);
        context.put("to-y", toY);
        context.put("to-z", toZ);
        context.put("yaw", player.getYaw());
        context.put("pitch", player.getPitch());
        MythicLibPassiveMod.fire(player.getUuid(), "TELEPORT", player.getUuid(), context);
    }

    @Unique
    private ServerPlayerEntity self() {
        return (ServerPlayerEntity) (Object) this;
    }
}
