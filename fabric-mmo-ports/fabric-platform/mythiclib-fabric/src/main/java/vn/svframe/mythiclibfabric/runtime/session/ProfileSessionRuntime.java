package vn.svframe.mythiclibfabric.runtime.session;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;

/** Native Fabric profile-session state machine ported from MythicLib 1.7.1 semantics. */
public final class ProfileSessionRuntime {
    private final UUID profileId;
    private final LongSupplier clock;
    private final Runnable clearTemporaryHandlers;
    private final Runnable closeDataSession;
    private final Runnable saveCurrentSession;
    private final Runnable applyNextSessionBuffer;
    private final List<Runnable> closeCallbacks = new ArrayList<>();
    private final List<Transition> transitions = new ArrayList<>();
    private State state = State.CREATED;
    private Set<String> waiting = new LinkedHashSet<>();
    private Set<String> loaded = new LinkedHashSet<>();
    private UpdateReason lastUpdateReason;
    private long lastActivity;

    public ProfileSessionRuntime(UUID profileId, LongSupplier clock, Runnable clearTemporaryHandlers,
                                 Runnable closeDataSession, Runnable saveCurrentSession,
                                 Runnable applyNextSessionBuffer) {
        this.profileId = profileId;
        this.clock = clock;
        this.clearTemporaryHandlers = clearTemporaryHandlers;
        this.closeDataSession = closeDataSession;
        this.saveCurrentSession = saveCurrentSession;
        this.applyNextSessionBuffer = applyNextSessionBuffer;
    }

    public UUID profileId() { return profileId; }
    public State state() { return state; }
    public boolean hasProfile() { return profileId != null; }
    public boolean isReady() { return state == State.OPEN; }
    public boolean isDead() { return state.isDead(); }
    public boolean wasReady() { return state.wasReady(); }
    public List<Transition> transitions() { return List.copyOf(transitions); }
    public Set<String> waitingModules() { return Set.copyOf(waiting); }
    public Set<String> loadedModules() { return Set.copyOf(loaded); }

    public synchronized void initializeSession(UpdateReason reason, Set<String> modules) {
        Objects.requireNonNull(reason, "Reason cannot be null");
        if (state != State.CREATED) throw new IllegalStateException("Can only initialize new session from state CREATED");
        transition(State.DEAD, reason);
        lastUpdateReason = reason;
        State old = state;
        state = State.OPENING;
        waiting = new LinkedHashSet<>(modules);
        loaded = new LinkedHashSet<>();
        record(old, state, reason);
        checkReadiness();
    }

    public synchronized void markAsReady(String module) {
        Objects.requireNonNull(module, "Module key cannot be null");
        if (state != State.OPENING) throw new IllegalStateException("Session is not opening: " + state);
        if (!waiting.remove(module)) throw new IllegalStateException("Module " + module + " already synced");
        loaded.add(module);
        checkReadiness();
    }

    private void checkReadiness() {
        if (!waiting.isEmpty()) return;
        State old = state;
        state = State.OPEN;
        record(old, state, lastUpdateReason);
    }

    public synchronized void initializeClosing(UpdateReason reason) {
        Objects.requireNonNull(reason, "Reason cannot be null");
        if (state.isClosing() || state.isDead()) return;
        State old = state;
        if (state == State.CREATED || state == State.OPENING) state = State.ABORTING;
        else if (state == State.OPEN) {
            state = State.CLOSING;
            closeDataSession.run();
        } else throw new IllegalStateException("Cannot close session from state " + state);
        lastUpdateReason = reason;
        closeCallbacks.clear();
        clearTemporaryHandlers.run();
        waiting = new LinkedHashSet<>(loaded);
        record(old, state, reason);
        checkClosed();
    }

    public synchronized void addCloseCallback(Runnable callback) {
        Objects.requireNonNull(callback, "Callback cannot be null");
        if (!state.isClosing()) throw new IllegalStateException("Session is not closing");
        closeCallbacks.add(callback);
    }

    public synchronized void markAsClosed(String module) {
        Objects.requireNonNull(module, "Module key cannot be null");
        if (!state.isClosing()) throw new IllegalStateException("Session is not closing: " + state);
        if (!waiting.remove(module)) throw new IllegalStateException("Module " + module + " already marked as closed");
        checkClosed();
    }

    private void checkClosed() {
        if (!waiting.isEmpty()) return;
        lastActivity = clock.getAsLong();
        State old = state;
        state = old == State.ABORTING ? State.DEAD_EARLY : State.DEAD;
        saveCurrentSession.run();
        closeCallbacks.forEach(Runnable::run);
        record(old, state, lastUpdateReason);
        lastUpdateReason = null;
        applyNextSessionBuffer.run();
    }

    public boolean isTimedOut() { return isDead() && lastActivity + 86_400_000L < clock.getAsLong(); }

    private void transition(State reportedPrevious, UpdateReason reason) { transitions.add(new Transition(reportedPrevious, state, reason)); }
    private void record(State oldState, State newState, UpdateReason reason) { transitions.add(new Transition(oldState, newState, reason)); }

    public enum State {
        CREATED, OPENING, OPEN, CLOSING, ABORTING, DEAD, DEAD_EARLY;
        public boolean wasReady() { return this == OPEN || this == CLOSING || this == DEAD; }
        public boolean isClosing() { return this == CLOSING || this == ABORTING; }
        public boolean isWaiting() { return isClosing() || this == OPENING; }
        public boolean isDead() { return this == DEAD || this == DEAD_EARLY; }
    }

    public enum UpdateReason { AUTOSAVE, LOG_OUT, QUIT_PROFILE, SWITCH_PROFILE, LOGIN, UNSPECIFIED }
    public record Transition(State oldState, State newState, UpdateReason reason) {}
}
