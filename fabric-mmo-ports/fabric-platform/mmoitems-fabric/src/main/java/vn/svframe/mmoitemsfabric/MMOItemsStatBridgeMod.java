package vn.svframe.mmoitemsfabric;

import net.fabricmc.api.ModInitializer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.mythiclibfabric.MythicLibFabricMod;
import vn.svframe.mythiclibfabric.runtime.StatProviderRegistry;

import java.util.Locale;

public final class MMOItemsStatBridgeMod implements ModInitializer {
    private static AutoCloseable registration;

    @Override
    public void onInitialize() {
        if (registration != null) return;
        registration = StatProviderRegistry.register((entityId, stat) -> {
            MinecraftServer server = MythicLibFabricMod.server();
            if (server == null) return 0.0d;
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entityId);
            if (player == null) return 0.0d;
            String itemStat = stat.toLowerCase(Locale.ROOT).replace('_', '-');
            return MMOItemsFabricMod.equipmentStats(player).sum(itemStat);
        });
    }
}
