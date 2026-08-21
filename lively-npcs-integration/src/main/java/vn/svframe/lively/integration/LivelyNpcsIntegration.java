package vn.svframe.lively.integration;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.svframe.lively.api.LivelyApi;

/**
 * External-mod bridge. Core AI remains independent from Cobblemon and other ecosystem APIs.
 */
public final class LivelyNpcsIntegration implements ModInitializer {
    public static final String MOD_ID = "livelynpcs_integration";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        boolean cobblemon = FabricLoader.getInstance().isModLoaded("cobblemon");
        LOGGER.info("Lively NPCs Integration initialized; Cobblemon present={}", cobblemon);

        if (cobblemon) installCobblemonNpcInteraction();
    }

    private void installCobblemonNpcInteraction() {
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient || !(player instanceof ServerPlayerEntity serverPlayer)) return ActionResult.PASS;
            if (!entity.getCommandTags().contains("lively")) return ActionResult.PASS;
            if (!looksLikeCobblemonNpc(entity.getClass())) return ActionResult.PASS;
            if (LivelyApi.dialogues() == null) return ActionResult.PASS;

            LivelyApi.dialogues().start(serverPlayer, entity.getUuid(), entity.getName().getString());
            return ActionResult.SUCCESS;
        });
    }

    private static boolean looksLikeCobblemonNpc(Class<?> type) {
        String name = type.getName().toLowerCase(java.util.Locale.ROOT);
        return name.contains("cobblemon") && name.contains("npc");
    }
}
