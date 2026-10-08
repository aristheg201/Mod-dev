package io.github.aristheg201.cobblemonworld.story;

import io.github.aristheg201.cobblemonworld.config.CWorldConfig;
import io.github.aristheg201.cobblemonworld.item.ModItems;
import io.github.aristheg201.cobblemonworld.network.CWorldNetworking;
import io.github.aristheg201.cobblemonworld.progression.PlayerProgression;
import io.github.aristheg201.cobblemonworld.progression.ProgressionStore;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.world.item.ItemStack;

public final class PhoneBootstrapService {
    private static long ticks;

    private PhoneBootstrapService() {}

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                server.execute(() -> ensurePhone(handler.player)));

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!CWorldConfig.INSTANCE.grantTrainerPhoneOnFirstJoin || ++ticks % 20L != 0L) return;
            for (var player : server.getPlayerList().getPlayers()) ensurePhone(player);
        });
    }

    private static void ensurePhone(net.minecraft.server.level.ServerPlayer player) {
        if (!CWorldConfig.INSTANCE.grantTrainerPhoneOnFirstJoin) return;

        PlayerProgression p = ProgressionStore.INSTANCE.getOrCreate(player.getUUID());
        boolean firstGrant = !p.storyFlags.contains("phone_granted");
        boolean hasPhysicalPhone = false;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).is(ModItems.TRAINER_PHONE)) {
                hasPhysicalPhone = true;
                break;
            }
        }

        if (!hasPhysicalPhone) {
            ItemStack phone = new ItemStack(ModItems.TRAINER_PHONE);
            if (!player.addItem(phone)) player.drop(phone, false);
            System.out.println("CWORLD_PHONE_ITEM_GRANTED player=" + player.getGameProfile().getName()
                    + " restored=" + (!firstGrant));
        }

        if (firstGrant) {
            p.storyFlags.add("phone_granted");
            ProgressionStore.INSTANCE.save();
        }

        CampaignService.initializePhone(player);

        if (firstGrant) {
            CWorldNetworking.toast(player, "story", "ui.cobblemonworld.trainer_phone", "toast.cobblemonworld.phone_waiting");
        } else if (!hasPhysicalPhone) {
            CWorldNetworking.toast(player, "story", "toast.cobblemonworld.phone_restored", "toast.cobblemonworld.phone_returned");
        }
    }
}
