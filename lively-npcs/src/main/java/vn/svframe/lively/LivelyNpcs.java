package vn.svframe.lively;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.svframe.lively.api.LivelyApi;
import vn.svframe.lively.dialogue.DialogueService;
import vn.svframe.lively.persistence.NpcStateRegistry;
import vn.svframe.lively.persistence.NpcStateStore;

import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class LivelyNpcs implements ModInitializer {
    public static final String MOD_ID = "livelynpcs";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private final AtomicLong ticks = new AtomicLong();
    private volatile CompletableFuture<Void> pendingAutosave = CompletableFuture.completedFuture(null);
    private NpcStateRegistry stateRegistry;

    @Override
    public void onInitialize() {
        Path statePath = FabricLoader.getInstance().getConfigDir().resolve("livelynpcs").resolve("state");
        stateRegistry = new NpcStateRegistry(new NpcStateStore(statePath));
        LivelyApi.installStateRegistry(stateRegistry);
        stateRegistry.preloadAll().whenComplete((count, error) -> {
            if (error != null) LOGGER.error("Lively NPC state preload failed", error);
            else LOGGER.info("Lively NPC state preload completed: {} NPC states", count);
        });

        DialogueService dialogueService = new DialogueService();
        dialogueService.install();

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            long tick = ticks.incrementAndGet();
            if (tick % 20L == 0L) LivelyApi.events().advance(Instant.now());
            if (tick % 6000L != 0L || !pendingAutosave.isDone()) return;
            pendingAutosave = stateRegistry.saveAll().whenComplete((ignored, error) -> {
                if (error != null) LOGGER.error("Lively NPC state autosave failed", error);
            });
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            try {
                stateRegistry.saveAll().orTimeout(10L, TimeUnit.SECONDS).join();
            } catch (RuntimeException ex) {
                LOGGER.error("Lively NPC final state flush failed", ex);
            } finally {
                stateRegistry.close();
            }
        });
        LOGGER.info("Lively NPCs initialized: offline AI, actor/world state, event/story director, world-integrity boundary, persistence, dialogue, navigation and combat ready");
    }
}
