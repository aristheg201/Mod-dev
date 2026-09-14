package vn.svframe.svarcade.runtime;

import java.util.*;

/** Owns exact platform snapshots only while SVArcade mutates the corresponding player fields. */
public final class PlayerStateProtection {
    public interface Bridge {
        PlayerStateSnapshot capture(UUID player, EnumSet<PlayerStateSnapshot.Field> fields);
        void restore(UUID player, PlayerStateSnapshot snapshot);
    }
    public record Failure(UUID player, String detail) { }

    private final ThreadGuard thread;
    private final Bridge bridge;
    private final int capacity;
    private final Map<UUID, PlayerStateSnapshot> snapshots = new LinkedHashMap<>();
    private final ArrayDeque<Failure> failures = new ArrayDeque<>();

    public PlayerStateProtection(ThreadGuard thread, Bridge bridge, int capacity) {
        this.thread = Objects.requireNonNull(thread); this.bridge = Objects.requireNonNull(bridge);
        if (capacity < 1 || capacity > 100000) throw new IllegalArgumentException("Player state capacity"); this.capacity = capacity;
    }
    public PlayerStateSnapshot capture(UUID player, EnumSet<PlayerStateSnapshot.Field> fields) {
        thread.check(); Objects.requireNonNull(player); Objects.requireNonNull(fields);
        if (fields.isEmpty() || snapshots.containsKey(player) || snapshots.size() >= capacity) throw new IllegalStateException("Player state capture unavailable");
        PlayerStateSnapshot snapshot = Objects.requireNonNull(bridge.capture(player, fields.clone()));
        if (!snapshot.fields().equals(fields)) throw new IllegalStateException("Platform captured wrong player-state fields");
        snapshots.put(player, snapshot); return snapshot;
    }
    /** Recovery path accepts only already-versioned explicit DTOs; it never recaptures mutated live state. */
    public void recover(UUID player, PlayerStateSnapshot snapshot) {
        thread.check(); Objects.requireNonNull(player); Objects.requireNonNull(snapshot);
        if (snapshots.size() >= capacity || snapshots.putIfAbsent(player, snapshot) != null) throw new IllegalStateException("Duplicate recovered player state");
    }
    public boolean protectedPlayer(UUID player) { thread.check(); return snapshots.containsKey(player); }
    public Map<UUID, PlayerStateSnapshot> snapshots() { thread.check(); return Map.copyOf(snapshots); }
    public boolean restore(UUID player) {
        thread.check(); PlayerStateSnapshot snapshot = snapshots.get(player); if (snapshot == null) return false;
        try { bridge.restore(player, snapshot); snapshots.remove(player); return true; }
        catch (RuntimeException failure) { record(player, failure); return false; }
    }
    public List<Failure> restoreAll() {
        thread.check(); for (UUID player : new ArrayList<>(snapshots.keySet())) restore(player); return failures();
    }
    private void record(UUID player, RuntimeException failure) {
        if (failures.size() == 128) failures.removeFirst(); String detail = failure.toString();
        failures.addLast(new Failure(player, detail.length() <= 2048 ? detail : detail.substring(0, 2048)));
    }
    public List<Failure> failures() { thread.check(); return List.copyOf(failures); }
    public int size() { thread.check(); return snapshots.size(); }
}
