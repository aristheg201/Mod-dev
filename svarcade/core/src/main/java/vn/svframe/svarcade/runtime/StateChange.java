package vn.svframe.svarcade.runtime;

/** Prepared owner-thread mutation. Preparation is read-only; the dispatcher publishes events. */
public interface StateChange {
    void apply();
    void rollback();
}
