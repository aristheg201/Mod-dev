package vn.svframe.lively.ai;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** Bounded, deduplicated worker scheduler with main-thread apply and stale-result rejection. */
public final class AiScheduler implements AutoCloseable {
    public enum Priority { CRITICAL, HIGH, NORMAL, LOW }
    public record TaskKey(UUID npcId, String lane) {}
    public record Submission(boolean accepted, String reason) {}

    private final AtomicLong sequence = new AtomicLong();
    private final ConcurrentHashMap<TaskKey, Future<?>> pending = new ConcurrentHashMap<>();
    private final ThreadPoolExecutor workers;
    private final Executor mainThread;
    private final int maxPending;

    public AiScheduler(int workerCount, int maxPending, Executor mainThread) {
        this.maxPending = Math.max(8, maxPending);
        this.mainThread = Objects.requireNonNull(mainThread);
        this.workers = new ThreadPoolExecutor(
                Math.max(1, workerCount), Math.max(1, workerCount), 30L, TimeUnit.SECONDS,
                new PriorityBlockingQueue<>(), r -> {
                    Thread t = new Thread(r, "Lively-AI-Worker");
                    t.setDaemon(true);
                    return t;
                });
    }

    public <T> Submission submit(TaskKey key, Priority priority, long capturedRevision,
                                 LongSupplier currentRevision, Callable<T> work, Consumer<T> apply) {
        if (pending.size() >= maxPending) return new Submission(false, "backpressure");
        Future<?> old = pending.get(key);
        if (old != null && !old.isDone()) return new Submission(false, "duplicate_pending");

        PriorityTask task = new PriorityTask(priority, sequence.incrementAndGet(), () -> {
            try {
                T value = work.call();
                mainThread.execute(() -> {
                    if (currentRevision.getAsLong() == capturedRevision) apply.accept(value);
                });
            } finally {
                pending.remove(key);
            }
            return null;
        });
        pending.put(key, task);
        workers.execute(task);
        return new Submission(true, "accepted");
    }

    public int pendingCount() { return pending.size(); }

    @Override public void close() {
        workers.shutdownNow();
        pending.clear();
    }

    private static final class PriorityTask extends FutureTask<Void> implements Comparable<PriorityTask> {
        private final Priority priority;
        private final long sequence;
        PriorityTask(Priority priority, long sequence, Callable<Void> work) {
            super(work); this.priority = priority; this.sequence = sequence;
        }
        @Override public int compareTo(PriorityTask other) {
            int p = Integer.compare(priority.ordinal(), other.priority.ordinal());
            return p != 0 ? p : Long.compare(sequence, other.sequence);
        }
    }
}
