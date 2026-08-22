package vn.svframe.lively.memory;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import vn.svframe.lively.api.LivelyApi;
import vn.svframe.lively.model.NpcState;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Periodic off-thread memory consolidation. It touches only NpcState locks, never Minecraft world objects. */
public final class MemoryMaintenanceBootstrap implements ModInitializer {
    private static final long INTERVAL_TICKS = 6000L;
    private static final double MIN_RECALL_SCORE = .06D;
    private static final int MIN_RECENT = 64;

    private final AtomicBoolean running = new AtomicBoolean();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Lively-Memory-Consolidation");
        thread.setDaemon(true);
        return thread;
    });

    @Override
    public void onInitialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % INTERVAL_TICKS != 0L || LivelyApi.states() == null || !running.compareAndSet(false, true)) return;
            List<NpcState> states = List.copyOf(LivelyApi.states().all());
            MemoryPolicy policy = LivelyApi.memoryPolicy();
            Instant now = Instant.now();
            worker.execute(() -> {
                try {
                    for (NpcState state : states) state.consolidateMemories(policy, now, MIN_RECALL_SCORE, MIN_RECENT);
                } finally {
                    running.set(false);
                }
            });
        });
    }
}
