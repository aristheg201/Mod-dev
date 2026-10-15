package dev.svextract.mixin;

import dev.svextract.SvExtract;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.screen.NamedScreenHandlerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.OptionalInt;
import net.minecraft.entity.Entity;
import net.minecraft.world.TeleportTarget;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import java.util.Set;

@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerGuardMixin {
    @Inject(method="openHandledScreen",at=@At("HEAD"),cancellable=true)
    private void svextract$screen(NamedScreenHandlerFactory factory,CallbackInfoReturnable<OptionalInt> ci){
        if(SvExtract.restricted((ServerPlayerEntity)(Object)this))ci.setReturnValue(OptionalInt.empty());
    }
    @Inject(method="teleport(Lnet/minecraft/server/world/ServerWorld;DDDFF)V",at=@At("HEAD"),cancellable=true)
    private void svextract$teleport(ServerWorld world,double x,double y,double z,float yaw,float pitch,CallbackInfo ci){
        if(SvExtract.runtime()!=null&&!SvExtract.runtime().mayTeleport((ServerPlayerEntity)(Object)this,world,x,y,z))ci.cancel();
    }
    @Inject(method="teleport(Lnet/minecraft/server/world/ServerWorld;DDDLjava/util/Set;FF)Z",at=@At("HEAD"),cancellable=true)
    private void svextract$relativeTeleport(ServerWorld world,double x,double y,double z,Set<PositionFlag> flags,float yaw,float pitch,CallbackInfoReturnable<Boolean> ci){
        // Coordinates passed to this API are absolute; flags affect only the outgoing packet.
        if(SvExtract.runtime()!=null&&!SvExtract.runtime().mayTeleport((ServerPlayerEntity)(Object)this,world,x,y,z))ci.setReturnValue(false);
    }
    @Inject(method="teleportTo",at=@At("HEAD"),cancellable=true)
    private void svextract$portal(TeleportTarget target,CallbackInfoReturnable<Entity> ci){
        if(SvExtract.runtime()!=null&&!SvExtract.runtime().mayTeleport((ServerPlayerEntity)(Object)this,target.world(),target.pos().x,target.pos().y,target.pos().z))ci.setReturnValue(null);
    }
    @Inject(method="dropSelectedItem",at=@At("HEAD"),cancellable=true)
    private void svextract$dropSelected(boolean entireStack,CallbackInfoReturnable<Boolean> ci){
        if(SvExtract.restricted((ServerPlayerEntity)(Object)this))ci.setReturnValue(false);
    }
}
