package vn.svframe.mmoitemsfabric.mixin;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vn.svframe.mmoitemsfabric.MMOItemsFabricMod;

/** Routes packet-only ability triggers into the real MMOItems ability runtime. */
@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerAbilityMixin {
    @Shadow public ServerPlayerEntity player;

    @Inject(method = "onPlayerAction", at = @At("HEAD"))
    private void mmoitems$onPlayerAction(PlayerActionC2SPacket packet, CallbackInfo ci) {
        switch (packet.getAction()) {
            case DROP_ITEM, DROP_ALL_ITEMS -> MMOItemsFabricMod.fireItemPacketTrigger(
                    player, player.isSneaking() ? "SHIFT_DROP_ITEM" : "DROP_ITEM");
            case SWAP_ITEM_WITH_OFFHAND -> MMOItemsFabricMod.fireItemPacketTrigger(
                    player, player.isSneaking() ? "SHIFT_SWAP_ITEMS" : "SWAP_ITEMS");
            case RELEASE_USE_ITEM -> fireReleaseAbility();
            default -> {
            }
        }
    }

    private void fireReleaseAbility() {
        ItemStack active = player.getActiveItem();
        if (active.isOf(Items.BOW)) {
            MMOItemsFabricMod.fireItemPacketTrigger(player, "SHOOT_BOW");
        } else if (active.isOf(Items.TRIDENT)) {
            MMOItemsFabricMod.fireItemPacketTrigger(player, "SHOOT_TRIDENT");
        }
    }
}
