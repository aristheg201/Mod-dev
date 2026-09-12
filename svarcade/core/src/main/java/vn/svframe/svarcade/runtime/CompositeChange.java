package vn.svframe.svarcade.runtime;

import java.util.*;

/** Coordinates individually atomic prepared changes; rollback only touches completed parts. */
public final class CompositeChange implements StateChange {
    private final List<StateChange> parts;
    private final ThreadGuard thread;
    private int completed, state;
    public CompositeChange(ThreadGuard thread, List<StateChange> parts) {
        this.thread = Objects.requireNonNull(thread); this.parts = List.copyOf(parts);
        if (parts.isEmpty() || parts.size() > 256) throw new IllegalArgumentException("Composite transaction size");
    }
    @Override public void apply() {
        thread.check(); if (state != 0) throw new IllegalStateException("Composite transaction already used");
        state = 1;
        try { for (StateChange part : parts) { part.apply(); completed++; } state = 2; }
        catch (RuntimeException failure) { state = 3; throw failure; }
    }
    @Override public void rollback() {
        thread.check(); if (state != 2 && state != 3) throw new IllegalStateException("Composite rollback unavailable");
        state = 4; RuntimeException failure = null;
        for (int i = completed - 1; i >= 0; i--) {
            try { parts.get(i).rollback(); }
            catch (RuntimeException error) { if (failure == null) failure = error; else failure.addSuppressed(error); }
        }
        if (failure != null) throw failure;
    }
}
