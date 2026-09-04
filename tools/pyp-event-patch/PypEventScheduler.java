package com.svframe.pyp.runtime;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Event/deadline scheduler injected into the bundled ProtectYourPack jar.
 *
 * Minecraft work is always handed back to MinecraftServer.execute(...). The
 * single daemon timer thread only waits for deadlines. Reflection is limited to
 * PYP's own private session/config fields and happens on sparse lifecycle
 * transitions/deadlines instead of once per player per server tick.
 */
public final class PypEventScheduler {
    private static final Object LOCK = new Object();
    private static final IdentityHashMap<Object, State> BY_HANDLER = new IdentityHashMap<>();
    private static final IdentityHashMap<Object, State> BY_PLAYER = new IdentityHashMap<>();
    private static final WeakHashMap<Object, PendingAuth> PENDING_AUTH = new WeakHashMap<>();

    private static final long PENDING_CHECK_NANOS = TimeUnit.SECONDS.toNanos(1);
    private static final long READINESS_RECHECK_NANOS = TimeUnit.MILLISECONDS.toNanos(250);

    private static final ScheduledThreadPoolExecutor TIMER = createTimer();
    private static volatile Access access;

    private PypEventScheduler() {
    }

    public static void onJoin(Object server, Object handler, Object player) {
        if (server == null || handler == null || player == null) {
            return;
        }

        final long now = System.nanoTime();
        final Access a = access();
        final Object session = a.session(handler);
        final long joinedAt = session == null ? now : a.joinedAt(session);
        final State state;

        synchronized (LOCK) {
            State existingHandler = BY_HANDLER.get(handler);
            if (existingHandler != null) {
                return;
            }

            State existingPlayer = BY_PLAYER.get(player);
            if (existingPlayer != null) {
                detachLocked(existingPlayer);
            }

            state = new State(server, handler, player, joinedAt);
            PendingAuth pending = PENDING_AUTH.remove(player);
            if (pending != null) {
                state.authenticated = pending.authenticated;
                state.authenticatedAtNanos = pending.authenticated ? pending.changedAtNanos : -1L;
            }

            BY_HANDLER.put(handler, state);
            BY_PLAYER.put(player, state);
        }

        if (session != null) {
            a.setAuthenticatedAt(session, state.authenticated ? state.authenticatedAtNanos : -1L);
        }
        rescheduleInitialDeadlines(state);
    }

    public static void onDisconnect(Object handler) {
        if (handler == null) {
            return;
        }
        synchronized (LOCK) {
            State state = BY_HANDLER.get(handler);
            if (state != null) {
                detachLocked(state);
            }
        }
    }

    public static void onAuthChanged(Object player, boolean authenticated) {
        if (player == null) {
            return;
        }

        final long now = System.nanoTime();
        final State state;
        synchronized (LOCK) {
            state = BY_PLAYER.get(player);
            if (state == null) {
                PENDING_AUTH.put(player, new PendingAuth(authenticated, authenticated ? now : -1L));
                return;
            }

            if (state.disconnected) {
                return;
            }
            if (state.authenticated == authenticated) {
                return;
            }

            state.authenticated = authenticated;
            state.authenticatedAtNanos = authenticated ? now : -1L;
            if (!authenticated) {
                cancelAuthLocked(state);
            }
        }

        Object session = access().session(state.handler);
        if (session != null) {
            access().setAuthenticatedAt(session, authenticated ? now : -1L);
        }

        if (authenticated) {
            scheduleAuthDeadline(state);
        }
    }

    public static void onConfigReload() {
        List<State> states;
        synchronized (LOCK) {
            states = new ArrayList<>(BY_HANDLER.values());
        }
        for (State state : states) {
            rescheduleInitialDeadlines(state);
        }
    }

    public static void shutdown() {
        synchronized (LOCK) {
            for (State state : BY_HANDLER.values()) {
                state.disconnected = true;
                cancelAllLocked(state);
            }
            BY_HANDLER.clear();
            BY_PLAYER.clear();
            PENDING_AUTH.clear();
        }
        TIMER.shutdownNow();
    }

    private static void rescheduleInitialDeadlines(State state) {
        Config config = access().config();
        synchronized (LOCK) {
            if (!isActiveLocked(state)) {
                return;
            }
            cancelJoinLocked(state);
            cancelAuthLocked(state);
        }

        if (isInitialPackSent(state)) {
            return;
        }

        if (!config.delayEnabled) {
            if (config.r2Mode) {
                scheduleJoin(state, System.nanoTime());
            }
            return;
        }

        scheduleJoin(state, saturatingAdd(state.joinedAtNanos, secondsToNanos(config.joinDelaySeconds)));
        if (state.authenticated && state.authenticatedAtNanos >= 0L) {
            scheduleAuth(state, saturatingAdd(state.authenticatedAtNanos, secondsToNanos(config.postAuthDelaySeconds)));
        }
    }

    private static void scheduleAuthDeadline(State state) {
        Config config = access().config();
        if (!config.delayEnabled || isInitialPackSent(state)) {
            return;
        }
        long authAt = state.authenticatedAtNanos;
        if (authAt < 0L) {
            return;
        }
        scheduleAuth(state, saturatingAdd(authAt, secondsToNanos(config.postAuthDelaySeconds)));
    }

    private static void scheduleJoin(State state, long deadlineNanos) {
        final long generation;
        synchronized (LOCK) {
            if (!isActiveLocked(state)) {
                return;
            }
            cancelFuture(state.joinFuture);
            generation = ++state.joinGeneration;
            long delay = Math.max(0L, deadlineNanos - System.nanoTime());
            state.joinFuture = TIMER.schedule(() -> enqueueFire(state, TaskKind.JOIN, generation), delay, TimeUnit.NANOSECONDS);
        }
    }

    private static void scheduleAuth(State state, long deadlineNanos) {
        final long generation;
        synchronized (LOCK) {
            if (!isActiveLocked(state) || !state.authenticated) {
                return;
            }
            cancelFuture(state.authFuture);
            generation = ++state.authGeneration;
            long delay = Math.max(0L, deadlineNanos - System.nanoTime());
            state.authFuture = TIMER.schedule(() -> enqueueFire(state, TaskKind.AUTH, generation), delay, TimeUnit.NANOSECONDS);
        }
    }

    private static void scheduleRetry(State state, long deadlineNanos) {
        final long generation;
        synchronized (LOCK) {
            if (!isActiveLocked(state)) {
                return;
            }
            cancelFuture(state.retryFuture);
            generation = ++state.retryGeneration;
            long delay = Math.max(0L, deadlineNanos - System.nanoTime());
            state.retryFuture = TIMER.schedule(() -> enqueueFire(state, TaskKind.RETRY, generation), delay, TimeUnit.NANOSECONDS);
        }
    }

    private static void schedulePendingCheck(State state, long deadlineNanos) {
        final long generation;
        synchronized (LOCK) {
            if (!isActiveLocked(state)) {
                return;
            }
            if (state.pendingCheckFuture != null && !state.pendingCheckFuture.isDone()) {
                return;
            }
            generation = ++state.pendingGeneration;
            long delay = Math.max(0L, deadlineNanos - System.nanoTime());
            state.pendingCheckFuture = TIMER.schedule(() -> enqueuePendingCheck(state, generation), delay, TimeUnit.NANOSECONDS);
        }
    }

    private static void enqueueFire(State state, TaskKind kind, long generation) {
        enqueue(state, () -> {
            synchronized (LOCK) {
                if (!isGenerationCurrentLocked(state, kind, generation)) {
                    return;
                }
                clearFutureLocked(state, kind);
            }
            fireTick(state);
        });
    }

    private static void enqueuePendingCheck(State state, long generation) {
        enqueue(state, () -> {
            synchronized (LOCK) {
                if (!isActiveLocked(state) || state.pendingGeneration != generation) {
                    return;
                }
                state.pendingCheckFuture = null;
            }
            inspectAfterTick(state);
        });
    }

    private static void enqueue(State state, Runnable runnable) {
        if (state.disconnected) {
            return;
        }
        if (state.server instanceof Executor executor) {
            executor.execute(() -> {
                if (!state.disconnected) {
                    runnable.run();
                }
            });
            return;
        }
        System.err.println("[PYP] Event scheduler could not hand work back to MinecraftServer executor.");
    }

    private static void fireTick(State trigger) {
        if (trigger.disconnected || isInitialPackSent(trigger)) {
            return;
        }

        try {
            access().tick(trigger.server);
        } catch (Throwable throwable) {
            System.err.println("[PYP] Event-driven pack gate tick failed: " + safeMessage(throwable));
        }

        List<State> states;
        synchronized (LOCK) {
            states = new ArrayList<>(BY_HANDLER.values());
        }
        for (State state : states) {
            inspectAfterTick(state);
        }
    }

    private static void inspectAfterTick(State state) {
        if (state.disconnected) {
            return;
        }

        Access a = access();
        Object session = a.session(state.handler);
        if (session == null) {
            return;
        }

        if (a.initialPackSent(session)) {
            synchronized (LOCK) {
                cancelRetryLocked(state);
                cancelPendingLocked(state);
            }
            return;
        }

        long now = System.nanoTime();
        if (a.sendPending(session)) {
            schedulePendingCheck(state, saturatingAdd(now, PENDING_CHECK_NANOS));
            return;
        }

        long retryAt = a.retryAfter(session);
        if (retryAt > now) {
            scheduleRetry(state, retryAt);
            return;
        }
        if (retryAt > 0L) {
            scheduleRetry(state, now);
            return;
        }

        Config config = a.config();
        if (constraintsAreDue(state, config, now)
                && !a.bspUnavailableLogged(session)
                && !a.missingAuthLogged(session)
                && !a.authReadFailureLogged(session)) {
            // Exceptional readiness fallback: BetterServerPacks hash not ready yet,
            // or an R2 queue rejected without assigning retryAfterNanos. This only
            // runs for a session whose auth/delay deadlines are already satisfied.
            scheduleRetry(state, saturatingAdd(now, READINESS_RECHECK_NANOS));
        }
    }

    private static boolean constraintsAreDue(State state, Config config, long now) {
        if (!config.delayEnabled) {
            return config.r2Mode;
        }
        if (!state.authenticated || state.authenticatedAtNanos < 0L) {
            return false;
        }
        long joinDeadline = saturatingAdd(state.joinedAtNanos, secondsToNanos(config.joinDelaySeconds));
        long authDeadline = saturatingAdd(state.authenticatedAtNanos, secondsToNanos(config.postAuthDelaySeconds));
        return now >= joinDeadline && now >= authDeadline;
    }

    private static boolean isInitialPackSent(State state) {
        Object session = access().session(state.handler);
        return session == null || access().initialPackSent(session);
    }

    private static boolean isGenerationCurrentLocked(State state, TaskKind kind, long generation) {
        if (!isActiveLocked(state)) {
            return false;
        }
        return switch (kind) {
            case JOIN -> state.joinGeneration == generation;
            case AUTH -> state.authGeneration == generation && state.authenticated;
            case RETRY -> state.retryGeneration == generation;
        };
    }

    private static void clearFutureLocked(State state, TaskKind kind) {
        switch (kind) {
            case JOIN -> state.joinFuture = null;
            case AUTH -> state.authFuture = null;
            case RETRY -> state.retryFuture = null;
        }
    }

    private static boolean isActiveLocked(State state) {
        return !state.disconnected && BY_HANDLER.get(state.handler) == state;
    }

    private static void detachLocked(State state) {
        state.disconnected = true;
        cancelAllLocked(state);
        BY_HANDLER.remove(state.handler);
        if (BY_PLAYER.get(state.player) == state) {
            BY_PLAYER.remove(state.player);
        }
    }

    private static void cancelAllLocked(State state) {
        cancelJoinLocked(state);
        cancelAuthLocked(state);
        cancelRetryLocked(state);
        cancelPendingLocked(state);
    }

    private static void cancelJoinLocked(State state) {
        ++state.joinGeneration;
        cancelFuture(state.joinFuture);
        state.joinFuture = null;
    }

    private static void cancelAuthLocked(State state) {
        ++state.authGeneration;
        cancelFuture(state.authFuture);
        state.authFuture = null;
    }

    private static void cancelRetryLocked(State state) {
        ++state.retryGeneration;
        cancelFuture(state.retryFuture);
        state.retryFuture = null;
    }

    private static void cancelPendingLocked(State state) {
        ++state.pendingGeneration;
        cancelFuture(state.pendingCheckFuture);
        state.pendingCheckFuture = null;
    }

    private static void cancelFuture(ScheduledFuture<?> future) {
        if (future != null) {
            future.cancel(false);
        }
    }

    private static long secondsToNanos(int seconds) {
        if (seconds <= 0) {
            return 0L;
        }
        try {
            return Math.multiplyExact((long) seconds, 1_000_000_000L);
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }

    private static long saturatingAdd(long left, long right) {
        long result = left + right;
        if (((left ^ result) & (right ^ result)) < 0L) {
            return Long.MAX_VALUE;
        }
        return result;
    }

    private static ScheduledThreadPoolExecutor createTimer() {
        AtomicInteger number = new AtomicInteger();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "pyp-deadline-" + number.incrementAndGet());
            thread.setDaemon(true);
            thread.setPriority(Thread.NORM_PRIORITY - 1);
            return thread;
        };
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1, factory);
        executor.setRemoveOnCancelPolicy(true);
        executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        executor.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        return executor;
    }

    private static Access access() {
        Access local = access;
        if (local != null) {
            return local;
        }
        synchronized (PypEventScheduler.class) {
            local = access;
            if (local == null) {
                local = new Access();
                access = local;
            }
            return local;
        }
    }

    private static String safeMessage(Throwable throwable) {
        Throwable actual = throwable;
        if (throwable instanceof InvocationTargetException invocation && invocation.getCause() != null) {
            actual = invocation.getCause();
        }
        String message = actual.getMessage();
        return message == null ? actual.getClass().getSimpleName() : message;
    }

    private enum TaskKind {
        JOIN,
        AUTH,
        RETRY
    }

    private static final class State {
        final Object server;
        final Object handler;
        final Object player;
        final long joinedAtNanos;

        volatile boolean authenticated;
        volatile long authenticatedAtNanos = -1L;
        volatile boolean disconnected;

        long joinGeneration;
        long authGeneration;
        long retryGeneration;
        long pendingGeneration;

        ScheduledFuture<?> joinFuture;
        ScheduledFuture<?> authFuture;
        ScheduledFuture<?> retryFuture;
        ScheduledFuture<?> pendingCheckFuture;

        State(Object server, Object handler, Object player, long joinedAtNanos) {
            this.server = server;
            this.handler = handler;
            this.player = player;
            this.joinedAtNanos = joinedAtNanos;
        }
    }

    private record PendingAuth(boolean authenticated, long changedAtNanos) {
    }

    private record Config(boolean delayEnabled, int joinDelaySeconds, int postAuthDelaySeconds, boolean r2Mode) {
    }

    private static final class Access {
        private final Field sessionsField;
        private final Method tickMethod;
        private final Method configMethod;

        private volatile Class<?> sessionType;
        private Field joinedAtField;
        private Field authenticatedAtField;
        private Field initialPackSentField;
        private Field sendPendingField;
        private Field retryAfterField;
        private Field bspUnavailableLoggedField;
        private Field missingAuthLoggedField;
        private Field authReadFailureLoggedField;

        private volatile Class<?> configType;
        private Method delayEnabledMethod;
        private Method joinDelaySecondsMethod;
        private Method postAuthDelaySecondsMethod;
        private Method isR2ModeMethod;

        Access() {
            try {
                Class<?> gate = Class.forName("com.svframe.pyp.PackDelayGate", false, PypEventScheduler.class.getClassLoader());
                sessionsField = gate.getDeclaredField("SESSIONS");
                sessionsField.setAccessible(true);
                tickMethod = findMethod(gate, "tick", 1);

                Class<?> protect = Class.forName("com.svframe.pyp.ProtectYourPack", false, PypEventScheduler.class.getClassLoader());
                configMethod = findMethod(protect, "config", 0);
            } catch (ReflectiveOperationException exception) {
                throw new ExceptionInInitializerError(exception);
            }
        }

        Object session(Object handler) {
            try {
                Object value = sessionsField.get(null);
                if (!(value instanceof Map<?, ?> map)) {
                    return null;
                }
                Object session = map.get(handler);
                if (session != null) {
                    ensureSessionFields(session.getClass());
                }
                return session;
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException("Cannot access PYP session map", exception);
            }
        }

        long joinedAt(Object session) {
            try {
                ensureSessionFields(session.getClass());
                return joinedAtField.getLong(session);
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException(exception);
            }
        }

        void setAuthenticatedAt(Object session, long value) {
            try {
                ensureSessionFields(session.getClass());
                authenticatedAtField.setLong(session, value);
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException(exception);
            }
        }

        boolean initialPackSent(Object session) {
            return getBoolean(session, initialPackSentField);
        }

        boolean sendPending(Object session) {
            return getBoolean(session, sendPendingField);
        }

        long retryAfter(Object session) {
            try {
                ensureSessionFields(session.getClass());
                return retryAfterField.getLong(session);
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException(exception);
            }
        }

        boolean bspUnavailableLogged(Object session) {
            return getBoolean(session, bspUnavailableLoggedField);
        }

        boolean missingAuthLogged(Object session) {
            return getBoolean(session, missingAuthLoggedField);
        }

        boolean authReadFailureLogged(Object session) {
            return getBoolean(session, authReadFailureLoggedField);
        }

        void tick(Object server) {
            try {
                tickMethod.invoke(null, server);
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException(exception);
            } catch (InvocationTargetException exception) {
                Throwable cause = exception.getCause() == null ? exception : exception.getCause();
                if (cause instanceof RuntimeException runtime) {
                    throw runtime;
                }
                if (cause instanceof Error error) {
                    throw error;
                }
                throw new IllegalStateException(cause);
            }
        }

        Config config() {
            try {
                Object config = configMethod.invoke(null);
                ensureConfigMethods(config.getClass());
                return new Config(
                        (Boolean) delayEnabledMethod.invoke(config),
                        (Integer) joinDelaySecondsMethod.invoke(config),
                        (Integer) postAuthDelaySecondsMethod.invoke(config),
                        (Boolean) isR2ModeMethod.invoke(config));
            } catch (IllegalAccessException | InvocationTargetException exception) {
                throw new IllegalStateException("Cannot read PYP config", exception);
            }
        }

        private boolean getBoolean(Object session, Field field) {
            try {
                ensureSessionFields(session.getClass());
                return field.getBoolean(session);
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException(exception);
            }
        }

        private synchronized void ensureSessionFields(Class<?> type) {
            if (sessionType == type) {
                return;
            }
            try {
                joinedAtField = field(type, "joinedAtNanos");
                authenticatedAtField = field(type, "authenticatedAtNanos");
                initialPackSentField = field(type, "initialPackSent");
                sendPendingField = field(type, "sendPending");
                retryAfterField = field(type, "retryAfterNanos");
                bspUnavailableLoggedField = field(type, "bspUnavailableLogged");
                missingAuthLoggedField = field(type, "missingAuthLogged");
                authReadFailureLoggedField = field(type, "authReadFailureLogged");
                sessionType = type;
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("PYP SessionState layout changed", exception);
            }
        }

        private synchronized void ensureConfigMethods(Class<?> type) {
            if (configType == type) {
                return;
            }
            try {
                delayEnabledMethod = findMethod(type, "delayEnabled", 0);
                joinDelaySecondsMethod = findMethod(type, "joinDelaySeconds", 0);
                postAuthDelaySecondsMethod = findMethod(type, "postAuthDelaySeconds", 0);
                isR2ModeMethod = findMethod(type, "isR2Mode", 0);
                configType = type;
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("PYP config layout changed", exception);
            }
        }

        private static Field field(Class<?> type, String name) throws NoSuchFieldException {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        }

        private static Method findMethod(Class<?> type, String name, int parameterCount) throws NoSuchMethodException {
            for (Method method : type.getDeclaredMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == parameterCount) {
                    method.setAccessible(true);
                    return method;
                }
            }
            throw new NoSuchMethodException(type.getName() + "." + name + "/" + parameterCount);
        }
    }
}
