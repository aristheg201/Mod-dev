package com.svframe.pyp;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import net.minecraft.class_3244;
import net.minecraft.server.MinecraftServer;
import xyz.nikitacartes.easyauth.utils.PlayerAuth;

public final class PypRuntimeScheduler {
    private static final Map<Object, class_3244> CONNECTIONS = new ConcurrentHashMap<>();
    private static final ScheduledThreadPoolExecutor TIMER = createTimer();
    private static volatile boolean retryScanNeeded;

    private PypRuntimeScheduler() {}

    private static ScheduledThreadPoolExecutor createTimer() {
        ThreadFactory tf = r -> {
            Thread t = new Thread(r, "PYP-delay");
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        };
        ScheduledThreadPoolExecutor ex = new ScheduledThreadPoolExecutor(1, tf);
        ex.setRemoveOnCancelPolicy(true);
        ex.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        ex.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        return ex;
    }

    public static void onJoin(class_3244 handler, MinecraftServer server) {
        if (handler == null || handler.field_14140 == null) return;
        CONNECTIONS.put(handler.field_14140, handler);
        PackDelayGate.SessionState state = PackDelayGate.SESSIONS.get(handler);
        if (state == null) return;
        PypConfig cfg = PackDelayGate.config;
        if (!cfg.delayEnabled()) {
            if (cfg.isR2Mode()) schedule(handler, state, server, ++state.authGeneration, 0L);
            return;
        }
        Object player = handler.field_14140;
        if (player instanceof PlayerAuth auth && auth.easyAuth$isAuthenticated()) {
            onAuthStateChanged(player, server, true);
        }
    }

    public static void onDisconnect(class_3244 handler) {
        if (handler == null || handler.field_14140 == null) return;
        CONNECTIONS.remove(handler.field_14140, handler);
    }

    public static void onAuthStateChanged(Object player, Object server, boolean authenticated) {
        class_3244 handler = CONNECTIONS.get(player);
        if (handler == null) return;
        PackDelayGate.SessionState state = PackDelayGate.SESSIONS.get(handler);
        if (state == null || state.initialPackSent) return;
        PypConfig cfg = PackDelayGate.config;
        if (!cfg.delayEnabled()) return;
        if (!authenticated) {
            state.authenticatedAtNanos = -1L;
            state.authGeneration++;
            return;
        }
        if (state.authenticatedAtNanos >= 0L) return;
        long now = System.nanoTime();
        state.authenticatedAtNanos = now;
        long generation = ++state.authGeneration;
        long deadline = Math.max(
                PackDelayGate.saturatingAdd(state.joinedAtNanos, PackDelayGate.secondsToNanos(cfg.joinDelaySeconds())),
                PackDelayGate.saturatingAdd(now, PackDelayGate.secondsToNanos(cfg.postAuthDelaySeconds())));
        schedule(handler, state, server, generation, Math.max(0L, deadline - now));
    }

    private static void schedule(class_3244 handler, PackDelayGate.SessionState state, Object server, long generation, long delayNanos) {
        if (!(server instanceof Executor executor) || TIMER.isShutdown()) return;
        TIMER.schedule(() -> executor.execute(() -> fireDeadline(handler, state, (MinecraftServer) server, generation)),
                Math.max(0L, delayNanos), TimeUnit.NANOSECONDS);
    }

    private static void fireDeadline(class_3244 handler, PackDelayGate.SessionState state, MinecraftServer server, long generation) {
        if (PackDelayGate.SESSIONS.get(handler) != state || state.authGeneration != generation || state.initialPackSent || state.sendPending) return;
        PypConfig cfg = PackDelayGate.config;
        long now = System.nanoTime();
        if (cfg.delayEnabled()) {
            if (state.authenticatedAtNanos < 0L) return;
            long deadline = Math.max(
                    PackDelayGate.saturatingAdd(state.joinedAtNanos, PackDelayGate.secondsToNanos(cfg.joinDelaySeconds())),
                    PackDelayGate.saturatingAdd(state.authenticatedAtNanos, PackDelayGate.secondsToNanos(cfg.postAuthDelaySeconds())));
            if (now < deadline) {
                schedule(handler, state, server, generation, deadline - now);
                return;
            }
        } else if (!cfg.isR2Mode()) {
            return;
        }
        state.retryAfterNanos = -1L;
        if (cfg.isR2Mode()) {
            PackDelayGate.queueR2Send(server, handler, state, cfg, true, false);
        } else {
            PackDelayGate.releaseBetterServerPacks(handler, state);
        }
        if (!state.initialPackSent && state.retryAfterNanos > System.nanoTime()) retryScanNeeded = true;
    }

    public static void markRetryNeeded() {
        retryScanNeeded = true;
    }

    public static void tickRetry(MinecraftServer server) {
        if (!retryScanNeeded) return;
        retryScanNeeded = false;
        long now = System.nanoTime();
        for (Map.Entry<class_3244, PackDelayGate.SessionState> entry : PackDelayGate.SESSIONS.entrySet()) {
            PackDelayGate.SessionState state = entry.getValue();
            if (state.initialPackSent || state.sendPending || state.retryAfterNanos <= 0L) continue;
            if (now < state.retryAfterNanos) {
                retryScanNeeded = true;
                continue;
            }
            long generation = state.authGeneration;
            state.retryAfterNanos = -1L;
            fireDeadline(entry.getKey(), state, server, generation);
            if (!state.initialPackSent && state.retryAfterNanos > System.nanoTime()) retryScanNeeded = true;
        }
    }

    public static void shutdown() {
        CONNECTIONS.clear();
        TIMER.shutdownNow();
    }
}
