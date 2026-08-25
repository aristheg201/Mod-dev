package vn.svframe.mythiclibfabric.runtime.session;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;

/** Native Fabric player/profile session runtime preserving MythicLib 1.7.1 lifecycle semantics. */
public final class MythicPlayerSessionRuntime {
    private final boolean lookup;
    private final LongSupplier clock;
    private final Set<String> modules;
    private final Map<UUID, ProfileSessionRuntime> savedSessions = new HashMap<>();
    private ProfileSessionRuntime profileSession;
    private boolean nextSessionBuffered;
    private UUID nextSessionProfile;
    private ProfileSessionRuntime.UpdateReason nextSessionReason;
    private int temporaryHandlers;
    private long nextLeftClick;
    private int closedDataSessions;

    public MythicPlayerSessionRuntime(boolean lookup, LongSupplier clock, Set<String> modules) {
        this.lookup = lookup;
        this.clock = clock;
        this.modules = Set.copyOf(modules);
    }

    public synchronized ProfileSessionRuntime chooseProfile(UUID profile, ProfileSessionRuntime.UpdateReason reason) {
        if (lookup) throw new IllegalStateException("Cannot choose a profile in lookup mode");
        if (profileSession != null) {
            nextSessionBuffered = true;
            nextSessionProfile = profile;
            nextSessionReason = reason;
            return profileSession;
        }
        ProfileSessionRuntime previous = savedSessions.remove(profile);
        profileSession = newSession(previous != null ? previous.profileId() : profile);
        profileSession.initializeSession(reason, modules);
        return profileSession;
    }

    private ProfileSessionRuntime newSession(UUID profile) {
        return new ProfileSessionRuntime(profile, clock, this::clearTemporaryHandlers, () -> closedDataSessions++,
                this::saveCurrentProfileSession, this::applyNextSessionBuffer);
    }

    public synchronized void shutdownSession() {
        if (profileSession != null) {
            profileSession.initializeClosing(ProfileSessionRuntime.UpdateReason.LOG_OUT);
            profileSession = null;
        }
    }

    public synchronized void saveCurrentProfileSession() {
        if (profileSession == null) throw new IllegalStateException("No profile session to save");
        if (!profileSession.isDead()) throw new IllegalStateException("Current profile session is still alive");
        savedSessions.put(profileSession.profileId(), profileSession);
        profileSession = null;
    }

    public synchronized void applyNextSessionBuffer() {
        if (!nextSessionBuffered) return;
        nextSessionBuffered = false;
        chooseProfile(nextSessionProfile, nextSessionReason);
    }

    public synchronized void clearNextSessionBuffer() { nextSessionBuffered = false; }
    public synchronized void flushTimedOutSessions() { savedSessions.values().removeIf(ProfileSessionRuntime::isTimedOut); }
    public ProfileSessionRuntime profileSession() { return profileSession; }
    public synchronized Map<UUID, ProfileSessionRuntime> savedSessions() { return Map.copyOf(savedSessions); }
    public boolean nextSessionBuffered() { return nextSessionBuffered; }
    public int closedDataSessions() { return closedDataSessions; }

    public void addTemporaryHandler() { temporaryHandlers++; }
    public void removeTemporaryHandler() {
        if (temporaryHandlers <= 0) throw new IllegalStateException("Handler is not registered");
        temporaryHandlers--;
    }
    public void clearTemporaryHandlers() { temporaryHandlers = 0; }
    public int temporaryHandlers() { return temporaryHandlers; }

    public void blockLeftClicks(long millis) { nextLeftClick = clock.getAsLong() + millis; }
    public boolean canLeftClick() { return clock.getAsLong() > nextLeftClick; }
}
