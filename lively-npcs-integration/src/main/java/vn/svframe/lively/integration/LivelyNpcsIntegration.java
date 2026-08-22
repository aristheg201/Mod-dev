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
import vn.svframe.lively.integration.cobblemon.CobblemonSocialResearchAwarenessService;
import vn.svframe.lively.integration.cobblemon.CobblemonWorldAwarenessService;
import vn.svframe.lively.quest.QuestRuntime;

import java.util.Map;

/** Cobblemon extension for Lively NPCs. Cobblemon itself is a mandatory runtime dependency. */
public final class LivelyNpcsIntegration implements ModInitializer {
    public static final String MOD_ID = "livelynpcs_integration";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private final CobblemonWorldAwarenessService worldAwareness = new CobblemonWorldAwarenessService();
    private final CobblemonSocialResearchAwarenessService socialResearchAwareness = new CobblemonSocialResearchAwarenessService();
    private final HologramProjectionService hologramProjection = new HologramProjectionService();
    private final QuestWaypointProjectionService questWaypoints = new QuestWaypointProjectionService();

    @Override
    public void onInitialize() {
        CobblemonIntegrationBootstrap.install();
        worldAwareness.install();
        socialResearchAwareness.install();
        ServerEcosystemBootstrap.install();
        hologramProjection.install();
        questWaypoints.install();

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            boolean installed = CobblemonIntegrationBootstrap.installNpcBodyProvider(server);
            LOGGER.info("Lively Cobblemon body provider bound and spawned bodies restored for server session={}", installed);
        });
        installCobblemonNpcInteraction();

        LOGGER.info("Lively NPCs: Cobblemon Integration initialized; Cobblemon=true, BEconomy={}, HoloDisplays={}, LuckPerms={}, Flan={}, SVFWaypoints={}",
                LivelyApi.externalEconomy().available(), LivelyApi.holograms().available(),
                FabricLoader.getInstance().isModLoaded("luckperms"), FabricLoader.getInstance().isModLoaded("flan"),
                LivelyApi.waypoints().available());
    }

    private void installCobblemonNpcInteraction() {
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient || hand != Hand.MAIN_HAND || !(player instanceof ServerPlayerEntity serverPlayer)) return ActionResult.PASS;
            if (!entity.getCommandTags().contains("lively") || !looksLikeCobblemonNpc(entity.getClass())) return ActionResult.PASS;

            ActorId owner = new ActorId(serverPlayer.getUuid(), ActorId.Kind.PLAYER);
            String npc = entity.getUuid().toString();
            LivelyApi.quests().signal(owner, QuestRuntime.ObjectiveType.SOCIAL, npc, 1L,
                    Map.of("actor", npc, "npc", npc));

            // Native trainer bodies retain Cobblemon's NPCClass interaction/battle configuration. Returning PASS here
            // lets Cobblemon continue its own interaction pipeline after Lively has recorded the semantic contact.
            if (entity.getCommandTags().contains("lively_native_interaction")) return ActionResult.PASS;

            if (LivelyApi.dialogues() == null) return ActionResult.PASS;
            LivelyApi.dialogues().start(serverPlayer, entity.getUuid(), entity.getName().getString(), "cobblemon_npc");
            return ActionResult.SUCCESS;
        });
    }

    private static boolean looksLikeCobblemonNpc(Class<?> type) {
        String name = type.getName().toLowerCase(java.util.Locale.ROOT);
        return name.contains("cobblemon") && name.contains("npc");
    }
}
