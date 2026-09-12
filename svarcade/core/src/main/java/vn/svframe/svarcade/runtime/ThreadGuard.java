package vn.svframe.svarcade.runtime;

/** Capture the authoritative simulation thread at construction. */
public final class ThreadGuard {
    private final Thread owner = Thread.currentThread();
    public void check() {
        if (Thread.currentThread() != owner) throw new IllegalStateException("Mutation outside authoritative thread");
    }
}
