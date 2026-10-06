package io.github.aristheg201.cobblemonworld.story;

import io.github.aristheg201.cobblemonworld.config.CWorldConfig;
import io.github.aristheg201.cobblemonworld.item.ModItems;
import io.github.aristheg201.cobblemonworld.network.CWorldNetworking;
import io.github.aristheg201.cobblemonworld.progression.PlayerProgression;
import io.github.aristheg201.cobblemonworld.progression.ProgressionStore;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.world.item.ItemStack;

public final class PhoneBootstrapService {
    private static long ticks;

    private PhoneBootstrapService() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!CWorldConfig.INSTANCE.grantTrainerPhoneOnFirstJoin || ++ticks % 20L != 0L) return;

            for (var player : server.getPlayerList().getPlayers()) {
                PlayerProgression p = ProgressionStore.INSTANCE.getOrCreate(player.getUUID());
                if (p.storyFlags.contains("phone_granted")) continue;

                ItemStack phone = new ItemStack(ModItems.TRAINER_PHONE);
                if (!player.addItem(phone)) player.drop(phone, false);

                p.storyFlags.add("phone_granted");
                ProgressionStore.INSTANCE.save();
                CampaignService.initializePhone(player);
                CWorldNetworking.toast(player, "story", "Trainer Phone", "A new message is waiting.");
            }
        });
    }
}
