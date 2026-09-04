package com.svframe.pyp;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import net.minecraft.class_3244;
import net.minecraft.server.MinecraftServer;
import xyz.nikitacartes.easyauth.utils.PlayerAuth;

public final class PypRuntimeScheduler {
    private static final Map<Object, class_3244> CONNECTIONS = new ConcurrentHashMap<>();
    private static final ScheduledThreadPoolExecutor TIMER = createTimer();
    private static final Object RETRY_LOCK = new Object();
    private static final long READINESS_RECHECK_NANOS = TimeUnit.MILLISECONDS.toNanos(250);

    private static volatile MinecraftServer SERVER;
    private static ScheduledFuture<?> retryFuture;
    private static long retryDeadlineNanos = Long.MAX_VALUE;

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
        if (handler == null || handler.field_14140 == null || server == null) return;
        SERVER = server;
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
        MinecraftServer actualServer = server instanceof MinecraftServer ms ? ms : SERVER;
        if (actualServer != null) schedule(handler, state, actualServer, generation, Math.max(0L, deadline - now));
    }

    public static void onConfigReload() {
        MinecraftServer server = SERVER;
        if (server == null) return;
        PypConfig cfg = PackDelayGate.config;
        long now = System.nanoTime();
        for (class_3244 handler : CONNECTIONS.values()) {
            PackDelayGate.SessionState state = PackDelayGate.SESSIONS.get(handler);
            if (state == null || state.initialPackSent) continue;
            long generation = ++state.authGeneration;
            if (!cfg.delayEnabled()) {
                if (cfg.isR2Mode()) schedule(handler, state, server, generation, 0L);
                continue;
            }
            if (state.authenticatedAtNanos < 0L) continue;
            long deadline = Math.max(
                    PackDelayGate.saturatingAdd(state.joinedAtNanos, PackDelayGate.secondsToNanos(cfg.joinDelaySeconds())),
                    PackDelayGate.saturatingAdd(state.authenticatedAtNanos, PackDelayGate.secondsToNanos(cfg.postAuthDelaySeconds())));
            schedule(handler, state, server, generation, Math.max(0L, deadline - now));
        }
        markRetryNeeded();
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
            boolean queued = PackDelayGate.queueR2Send(server, handler, state, cfg, true, false);
            if (!queued && !state.initialPackSent && !state.sendPending && state.retryAfterNanos <= 0L) {
                state.retryAfterNanos = PackDelayGate.saturatingAdd(System.nanoTime(), READINESS_RECHECK_NANOS);
            }
        } else {
            PackDelayGate.releaseBetterServerPacks(handler, state);
            if (!state.initialPackSent && !state.bspUnavailableLogged && state.retryAfterNanos <= 0L) {
                // BetterServerPacks is installed but its hash is not ready yet.
                // Old PYP retried this every server tick; recheck only this due session.
                state.retryAfterNanos = PackDelayGate.saturatingAdd(System.nanoTime(), READINESS_RECHECK_NANOS);
            }
        }

        if (!state.initialPackSent && state.retryAfterNanos > 0L) markRetryNeeded();
    }

    public static void markRetryNeeded() {
        MinecraftServer server = SERVER;
        if (server == null || TIMER.isShutdown()) return;
        long earliest = findEarliestRetry();
        if (earliest != Long.MAX_VALUE) scheduleRetryWake(server, earliest);
    }

    private static long findEarliestRetry() {
        long earliest = Long.MAX_VALUE;
        for (PackDelayGate.SessionState state : PackDelayGate.SESSIONS.values()) {
            if (state.initialPackSent || state.sendPending || state.retryAfterNanos <= 0L) continue;
            if (state.retryAfterNanos < earliest) earliest = state.retryAfterNanos;
        }
        return earliest;
    }

    private static void scheduleRetryWake(MinecraftServer server, long deadlineNanos) {
        synchronized (RETRY_LOCK) {
            if (TIMER.isShutdown()) return;
            if (retryFuture != null && !retryFuture.isDone() && retryDeadlineNanos <= deadlineNanos) return;
            if (retryFuture != null) retryFuture.cancel(false);
            retryDeadlineNanos = deadlineNanos;
            long delay = Math.max(0L, deadlineNanos - System.nanoTime());
            retryFuture = TIMER.schedule(() -> server.execute(() -> drainRetries(server)), delay, TimeUnit.NANOSECONDS);
        }
    }

    private static void drainRetries(MinecraftServer server) {
        synchronized (RETRY_LOCK) {
            retryFuture = null;
            retryDeadlineNanos = Long.MAX_VALUE;
        }

        long now = System.nanoTime();
        long next = Long.MAX_VALUE;
        for (Map.Entry<class_3244, PackDelayGate.SessionState> entry : PackDelayGate.SESSIONS.entrySet()) {
            PackDelayGate.SessionState state = entry.getValue();
            if (state.initialPackSent || state.sendPending || state.retryAfterNanos <= 0L) continue;
            if (now < state.retryAfterNanos) {
                if (state.retryAfterNanos < next) next = state.retryAfterNanos;
                continue;
            }
            long generation = state.authGeneration;
            state.retryAfterNanos = -1L;
            fireDeadline(entry.getKey(), state, server, generation);
            if (!state.initialPackSent && !state.sendPending && state.retryAfterNanos > 0L && state.retryAfterNanos < next) {
                next = state.retryAfterNanos;
            }
        }
        if (next != Long.MAX_VALUE) scheduleRetryWake(server, next);
    }

    public static void tickRetry(MinecraftServer server) {
        // Kept as a compatibility target for the patched PackDelayGate.tick method.
        // ProtectYourPack no longer registers END_SERVER_TICK; if another caller
        // invokes tick, only drain when the single retry deadline is actually due.
        long deadline;
        synchronized (RETRY_LOCK) {
            deadline = retryDeadlineNanos;
        }
        if (deadline != Long.MAX_VALUE && System.nanoTime() >= deadline) drainRetries(server);
    }

    public static void shutdown() {
        CONNECTIONS.clear();
        SERVER = null;
        synchronized (RETRY_LOCK) {
            if (retryFuture != null) retryFuture.cancel(false);
            retryFuture = null;
            retryDeadlineNanos = Long.MAX_VALUE;
        }
        TIMER.shutdownNow();
    }
}
