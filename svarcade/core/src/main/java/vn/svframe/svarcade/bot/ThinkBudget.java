package vn.svframe.svarcade.bot;

import java.util.concurrent.CancellationException;

/** Strategies must charge every expanded node/candidate; cancellation is cooperative. */
public final class ThinkBudget {
    private final long started = System.nanoTime();
    private final long maxNanos, maxOperations;
    private long operations;
    public ThinkBudget(long maxNanos, long maxOperations) {
        if (maxNanos < 1 || maxNanos > 30_000_000_000L || maxOperations < 1) throw new IllegalArgumentException("Invalid think budget");
        this.maxNanos = maxNanos; this.maxOperations = maxOperations;
    }
    public void visit() {
        if (operations >= maxOperations) throw new CancellationException("Bot operation budget exceeded");
        operations++; check();
    }
    public void check() {
        if (Thread.currentThread().isInterrupted() || System.nanoTime() - started >= maxNanos) throw new CancellationException("Bot time budget or cancellation");
    }
    public long operations() { return operations; }
}
