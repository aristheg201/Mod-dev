package vn.svframe.mythiclibfabric.runtime;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Native Fabric implementation of MythicLib 1.7.1 TemporaryStatModifier lifecycle. */
public final class NativeTemporaryStatModifier extends NativeStatModifier implements AutoCloseable {
    private static final Map<NativeTemporaryStatModifier, ScheduledRemoval> SCHEDULED = new ConcurrentHashMap<>();

    private volatile ScheduledRemoval closeTask;
    private volatile long duration;
    private volatile long startTime;

    public NativeTemporaryStatModifier(String key,
                                       String stat,
                                       double value,
                                       NativeStatEngine.ModifierType type,
                                       NativeStatEngine.EquipmentSlot slot,
                                       NativeStatEngine.ModifierSource source) {
        super(key, stat, value, type, slot, source);
    }

    public NativeTemporaryStatModifier(UUID uniqueId,
                                       String key,
                                       String stat,
                                       double value,
                                       NativeStatEngine.ModifierType type,
                                       NativeStatEngine.EquipmentSlot slot,
                                       NativeStatEngine.ModifierSource source) {
        super(uniqueId, key, stat, value, type, slot, source);
    }

    public long duration() {
        ensureActive();
        return duration;
    }

    public long startTime() {
        ensureActive();
        return startTime;
    }

    public synchronized void register(NativeStatEngine engine, UUID entityId, long delayTicks, long currentTick) {
        if (isActive()) throw new IllegalStateException("Modifier is already active");
        if (delayTicks < 0L) throw new IllegalArgumentException("delayTicks must be >= 0");
        super.register(engine, entityId);
        ScheduledRemoval task = new ScheduledRemoval(this, engine, entityId, saturatingAdd(currentTick, delayTicks));
        closeTask = task;
        SCHEDULED.put(this, task);
        duration = delayTicks;
        startTime = System.currentTimeMillis();
    }

    @Override
    public void register(NativeStatEngine engine, UUID entityId) {
        throw new UnsupportedOperationException("Use #register(NativeStatEngine, UUID, long, long) instead");
    }

    @Override
    public synchronized void close() {
        ensureActive();
        ScheduledRemoval task = closeTask;
        task.cancelled = true;
        SCHEDULED.remove(this, task);
        closeTask = null;
    }

    public boolean isActive() {
        return closeTask != null;
    }

    /** Called from the Fabric server tick. Matches Bukkit runTaskLater execution at the due tick. */
    public static int tick(long currentTick) {
        int executed = 0;
        for (Map.Entry<NativeTemporaryStatModifier, ScheduledRemoval> entry : SCHEDULED.entrySet()) {
            ScheduledRemoval task = entry.getValue();
            if (task.cancelled || currentTick < task.dueTick) continue;
            if (!SCHEDULED.remove(entry.getKey(), task)) continue;
            task.run();
            executed++;
        }
        return executed;
    }

    public static void cancelAll() {
        for (ScheduledRemoval task : SCHEDULED.values()) task.cancelled = true;
        SCHEDULED.clear();
    }

    public static int scheduledCount() {
        return SCHEDULED.size();
    }

    private void ensureActive() {
        if (!isActive()) throw new IllegalStateException("Modifier is not active");
    }

    private static long saturatingAdd(long left, long right) {
        if (right > 0L && left > Long.MAX_VALUE - right) return Long.MAX_VALUE;
        return left + right;
    }

    private static final class ScheduledRemoval {
        private final NativeTemporaryStatModifier modifier;
        private final NativeStatEngine engine;
        private final UUID entityId;
        private final long dueTick;
        private volatile boolean cancelled;

        private ScheduledRemoval(NativeTemporaryStatModifier modifier,
                                 NativeStatEngine engine,
                                 UUID entityId,
                                 long dueTick) {
            this.modifier = Objects.requireNonNull(modifier, "modifier");
            this.engine = Objects.requireNonNull(engine, "engine");
            this.entityId = Objects.requireNonNull(entityId, "entityId");
            this.dueTick = dueTick;
        }

        private void run() {
            if (cancelled) return;
            engine.remove(entityId, modifier.stat(), modifier.uniqueId());
            modifier.unregister(engine, entityId);
            // 1.7.1 intentionally keeps closeTask non-null after natural expiry;
            // isActive()/duration/startTime therefore remain observable until close() is called.
        }
    }
}
