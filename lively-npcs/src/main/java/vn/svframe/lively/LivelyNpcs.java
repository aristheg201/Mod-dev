package vn.svframe.lively;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.svframe.lively.admin.LivelyCommands;
import vn.svframe.lively.api.LivelyApi;
import vn.svframe.lively.dialogue.DialogueService;
import vn.svframe.lively.event.WorldEventEngine;
import vn.svframe.lively.persistence.NpcStateRegistry;
import vn.svframe.lively.persistence.NpcStateStore;
import vn.svframe.lively.persistence.WorldHistoryJournal;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class LivelyNpcs implements ModInitializer {
    public static final String MOD_ID = "livelynpcs";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private final AtomicLong ticks = new AtomicLong();
    private final AtomicLong historySequence = new AtomicLong();
    private volatile CompletableFuture<Void> pendingAutosave = CompletableFuture.completedFuture(null);
    private NpcStateRegistry stateRegistry;
    private WorldHistoryJournal historyJournal;

    @Override
    public void onInitialize() {
        Path config = FabricLoader.getInstance().getConfigDir().resolve("livelynpcs");
        Path statePath = config.resolve("state");
        stateRegistry = new NpcStateRegistry(new NpcStateStore(statePath));
        LivelyApi.installStateRegistry(stateRegistry);
        stateRegistry.preloadAll().whenComplete((count, error) -> {
            if (error != null) LOGGER.error("Lively NPC state preload failed", error);
            else LOGGER.info("Lively NPC state preload completed: {} NPC states", count);
        });

        historyJournal = new WorldHistoryJournal(config.resolve("history").resolve("world-history.lwh"), 128L * 1024L * 1024L);
        try {
            var history = historyJournal.readAll();
            history.stream().mapToLong(WorldHistoryJournal.Entry::sequence).max().ifPresent(historySequence::set);
            LOGGER.info("Lively world history loaded: {} records", history.size());
        } catch (IOException error) {
            LOGGER.error("Lively world history validation failed; new events will still be journaled if storage is writable", error);
        }
        LivelyApi.events().addListener(new WorldEventEngine.Listener() {
            @Override public void onStarted(WorldEventEngine.WorldEvent event) { journal("event_started", event); }
            @Override public void onFinished(WorldEventEngine.WorldEvent event) { journal("event_finished", event); }
            @Override public void onCancelled(WorldEventEngine.WorldEvent event) { journal("event_cancelled", event); }
        });

        DialogueService dialogueService = new DialogueService();
        dialogueService.install();
        LivelyCommands.install();

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            long tick = ticks.incrementAndGet();
            if (tick % 20L == 0L) {
                Instant now = Instant.now();
                LivelyApi.profiler().measure("world-events", () -> LivelyApi.events().advance(now));
                LivelyApi.profiler().measure("quest-expiry", () -> LivelyApi.quests().expire(now));
                LivelyApi.profiler().measure("rumor-expiry", () -> LivelyApi.social().expireRumors(now));
            }
            if (tick % 1200L == 0L) LivelyApi.profiler().measure("market-tick", () -> { LivelyApi.economy().marketTick(); return 0; });
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
        LOGGER.info("Lively NPCs initialized: living-world AI core and validated admin/runtime surfaces ready");
    }

    private void journal(String type, WorldEventEngine.WorldEvent event) {
        try {
            historyJournal.append(new WorldHistoryJournal.Entry(
                    historySequence.incrementAndGet(), Instant.now(), type, event.id().toString(),
                    Map.of("category", event.category().name(), "seed", event.seed(), "phase", event.phase().name(),
                            "structure", event.structureId() == null ? "" : event.structureId())));
        } catch (IOException error) {
            LOGGER.error("Failed to append Lively world history record {}", type, error);
        }
    }
}
