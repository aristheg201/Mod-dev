package vn.svframe.lively.integration;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.svframe.lively.actor.ActorId;
import vn.svframe.lively.api.LivelyApi;
import vn.svframe.lively.integration.cobblemon.CobblemonIntegrationBootstrap;
import vn.svframe.lively.integration.cobblemon.CobblemonWorldAwarenessService;
import vn.svframe.lively.quest.QuestRuntime;

import java.util.Map;

/** External-mod bridge. Core AI remains independent from Cobblemon and the rest of the server ecosystem. */
public final class LivelyNpcsIntegration implements ModInitializer {
    public static final String MOD_ID = "livelynpcs_integration";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private final CobblemonWorldAwarenessService worldAwareness = new CobblemonWorldAwarenessService();
    private final HologramProjectionService hologramProjection = new HologramProjectionService();
    private final QuestWaypointProjectionService questWaypoints = new QuestWaypointProjectionService();

    @Override
    public void onInitialize() {
        ServerEcosystemBootstrap.install();
        hologramProjection.install();
        questWaypoints.install();
        boolean cobblemon = FabricLoader.getInstance().isModLoaded("cobblemon");
        if (cobblemon) {
            CobblemonIntegrationBootstrap.installIfPresent();
            worldAwareness.install();
            ServerLifecycleEvents.SERVER_STARTED.register(server -> {
                boolean installed = CobblemonIntegrationBootstrap.installNpcBodyProvider(server);
                LOGGER.info("Lively Cobblemon body provider bound and spawned bodies restored for server session={}", installed);
            });
            installCobblemonNpcInteraction();
        }
        LOGGER.info("Lively NPCs Integration initialized; Cobblemon={}, BEconomy={}, HoloDisplays={}, LuckPerms={}, Flan={}, SVFWaypoints={}",
                cobblemon, LivelyApi.externalEconomy().available(), LivelyApi.holograms().available(),
                FabricLoader.getInstance().isModLoaded("luckperms"), FabricLoader.getInstance().isModLoaded("flan"),
                LivelyApi.waypoints().available());
    }

    private void installCobblemonNpcInteraction() {
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient || hand != Hand.MAIN_HAND || !(player instanceof ServerPlayerEntity serverPlayer)) return ActionResult.PASS;
            if (!entity.getCommandTags().contains("lively") || !looksLikeCobblemonNpc(entity.getClass()) || LivelyApi.dialogues() == null) return ActionResult.PASS;
            ActorId owner = new ActorId(serverPlayer.getUuid(), ActorId.Kind.PLAYER);
            String npc = entity.getUuid().toString();
            LivelyApi.quests().signal(owner, QuestRuntime.ObjectiveType.SOCIAL, npc, 1L,
                    Map.of("actor", npc, "npc", npc));
            LivelyApi.dialogues().start(serverPlayer, entity.getUuid(), entity.getName().getString(), "cobblemon_npc");
            return ActionResult.SUCCESS;
        });
    }

    private static boolean looksLikeCobblemonNpc(Class<?> type) {
        String name = type.getName().toLowerCase(java.util.Locale.ROOT);
        return name.contains("cobblemon") && name.contains("npc");
    }
}
