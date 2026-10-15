package dev.svextract.mixin;

import dev.svextract.SvExtract;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class NetworkGuardMixin {
    @Shadow public ServerPlayerEntity player;
    @Inject(method="onPlayerMove",at=@At("HEAD"),cancellable=true)
    private void svextract$move(PlayerMoveC2SPacket packet,CallbackInfo ci){if(SvExtract.locked(player)||SvExtract.runtime()!=null&&SvExtract.runtime().frozen(player.getUuid()))ci.cancel();}
    @Inject(method="onPlayerAction",at=@At("HEAD"),cancellable=true)
    private void svextract$action(net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket packet,CallbackInfo ci){if(SvExtract.locked(player))ci.cancel();}
    @Inject(method="onPickFromInventory",at=@At("HEAD"),cancellable=true)
    private void svextract$pick(net.minecraft.network.packet.c2s.play.PickFromInventoryC2SPacket packet,CallbackInfo ci){if(SvExtract.locked(player))ci.cancel();}
    @Inject(method="onUpdateSelectedSlot",at=@At("HEAD"),cancellable=true)
    private void svextract$select(net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket packet,CallbackInfo ci){if(SvExtract.locked(player))ci.cancel();}
    @Inject(method="onCreativeInventoryAction",at=@At("HEAD"),cancellable=true)
    private void svextract$creative(net.minecraft.network.packet.c2s.play.CreativeInventoryActionC2SPacket packet,CallbackInfo ci){if(SvExtract.restricted(player))ci.cancel();}
}
