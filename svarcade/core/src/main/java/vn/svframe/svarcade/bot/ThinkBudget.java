package vn.svframe.svarcade.bot;

import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.function.LongSupplier;

/** A worker-local budget. Exhaustion ends search; cancellation must discard its result. */
public final class ThinkBudget {
    public enum Reason { TIME, OPERATIONS }
    public static final class Exhausted extends CancellationException {
        private static final long serialVersionUID = 1L;
        private final Reason reason;
        private Exhausted(Reason reason) { super("Bot budget exhausted: " + reason); this.reason = reason; }
        public Reason reason() { return reason; }
    }
    private final LongSupplier clock;
    private final long started, maxNanos, maxOperations;
    private long operations;
    public ThinkBudget(long maxNanos, long maxOperations) { this(maxNanos, maxOperations, System::nanoTime); }
    /** A monotonic clock can be injected for deterministic deadline verification. */
    public ThinkBudget(long maxNanos, long maxOperations, LongSupplier clock) {
        if (maxNanos < 1 || maxNanos > 30_000_000_000L || maxOperations < 1) throw new IllegalArgumentException("Invalid think budget");
        this.clock = Objects.requireNonNull(clock); this.started = clock.getAsLong();
        this.maxNanos = maxNanos; this.maxOperations = maxOperations;
    }
    public void visit() {
        check();
        if (operations >= maxOperations) throw new Exhausted(Reason.OPERATIONS);
        operations++;
    }
    public void check() {
        checkCancellation();
        if (elapsedNanos() >= maxNanos) throw new Exhausted(Reason.TIME);
    }
    public void checkCancellation() {
        if (Thread.currentThread().isInterrupted()) throw new CancellationException("Bot search cancelled");
    }
    public long operations() { return operations; }
    public long elapsedNanos() { return clock.getAsLong() - started; }
}
