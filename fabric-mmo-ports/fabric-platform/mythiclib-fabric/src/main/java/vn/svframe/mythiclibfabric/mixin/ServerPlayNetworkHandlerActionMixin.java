package vn.svframe.mythiclibfabric.mixin;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vn.svframe.mythiclibfabric.MythicLibPassiveMod;

import java.util.LinkedHashMap;
import java.util.Map;

/** Packet-level passive triggers which Fabric's high-level callbacks do not expose. */
@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerActionMixin {
    @Shadow public ServerPlayerEntity player;

    @Inject(method = "onPlayerAction", at = @At("HEAD"))
    private void mythiclib$onPlayerAction(PlayerActionC2SPacket packet, CallbackInfo ci) {
        switch (packet.getAction()) {
            case DROP_ITEM, DROP_ALL_ITEMS -> fireHeld(player.isSneaking() ? "SHIFT_DROP_ITEM" : "DROP_ITEM");
            case SWAP_ITEM_WITH_OFFHAND -> fireHeld(player.isSneaking() ? "SHIFT_SWAP_ITEMS" : "SWAP_ITEMS");
            case RELEASE_USE_ITEM -> fireRelease();
            default -> {
            }
        }
    }

    @Inject(method = "onClientCommand", at = @At("HEAD"))
    private void mythiclib$onClientCommand(ClientCommandC2SPacket packet, CallbackInfo ci) {
        if (packet.getMode() == ClientCommandC2SPacket.Mode.PRESS_SHIFT_KEY) {
            MythicLibPassiveMod.fire(player.getUuid(), "SNEAK", player.getUuid(), Map.of("sneaking", true));
        }
    }

    private void fireHeld(String trigger) {
        ItemStack stack = player.getMainHandStack();
        MythicLibPassiveMod.fire(player.getUuid(), trigger, player.getUuid(), itemContext(stack));
    }

    private void fireRelease() {
        ItemStack stack = player.getActiveItem();
        if (stack.isEmpty()) return;

        String trigger;
        if (stack.isOf(Items.BOW)) {
            trigger = "SHOOT_BOW";
        } else if (stack.isOf(Items.TRIDENT)) {
            trigger = "SHOOT_TRIDENT";
        } else {
            return;
        }

        Map<String, Object> context = itemContext(stack);
        context.put("hand", player.getActiveHand().name());
        MythicLibPassiveMod.fire(player.getUuid(), trigger, player.getUuid(), context);
    }

    private static Map<String, Object> itemContext(ItemStack stack) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (stack == null || stack.isEmpty()) return out;
        out.put("item", Registries.ITEM.getId(stack.getItem()).toString());
        out.put("item-count", stack.getCount());
        out.put("item-damage", stack.getDamage());
        out.put("item-max-damage", stack.getMaxDamage());
        out.put("custom-data", stack.contains(DataComponentTypes.CUSTOM_DATA));
        return out;
    }
}
