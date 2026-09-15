package vn.svframe.svrelationships.fabric.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import vn.svframe.svrelationships.relationship.RelationshipKey;
import vn.svframe.svrelationships.relationship.RelationshipState;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class RelationshipRepository implements AutoCloseable {
    private final Path file;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Map<RelationshipKey, RelationshipState> states = new ConcurrentHashMap<>();
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "svrelationships-relationship-writer"); t.setDaemon(true); return t; });
    private final AtomicBoolean dirty = new AtomicBoolean();
    private final AtomicBoolean queued = new AtomicBoolean();

    public RelationshipRepository(Path file) { this.file = file; }

    public void load() {
        var loaded = VersionedStateFile.readArray(file, gson, StoredRelationship[].class);
        StoredRelationship[] records = loaded.records();
        if (records != null) Arrays.stream(records).forEach(this::restore);
        if (loaded.legacySchema() || loaded.recoveredFromBackup()) markDirty();
    }

    private void restore(StoredRelationship stored) {
        RelationshipState state = new RelationshipState(new RelationshipKey(stored.playerId(), stored.pokemonId()));
        if (stored.progression() != null) stored.progression().forEach(state::setProgression);
        if (stored.routes() != null) stored.routes().forEach(state::setRouteState);
        if (stored.cooldowns() != null) stored.cooldowns().forEach(state::setCooldownUntil);
        if (stored.flags() != null) stored.flags().forEach(state::setFlag);
        state.setPartner(stored.partner(), stored.partnerSinceMillis());
        state.setPersonalityId(stored.personalityId());
        state.setHouseholdId(stored.householdId());
        states.put(state.key(), state);
    }

    public RelationshipState getOrCreate(RelationshipKey key) { return states.computeIfAbsent(key, RelationshipState::new); }
    public Optional<RelationshipState> get(RelationshipKey key) { return Optional.ofNullable(states.get(key)); }
    public List<RelationshipState> byPlayer(UUID playerId) { return states.values().stream().filter(s -> s.key().playerId().equals(playerId)).toList(); }
    public List<RelationshipState> partners(UUID playerId) { return states.values().stream().filter(s -> s.key().playerId().equals(playerId) && s.partner()).toList(); }
    public List<RelationshipState> snapshot() { return List.copyOf(states.values()); }
    public int size() { return states.size(); }

    public void markDirty() { dirty.set(true); if (queued.compareAndSet(false, true)) writer.execute(this::drain); }
    private void drain() { try { while (dirty.getAndSet(false)) writeSnapshot(); } finally { queued.set(false); if (dirty.get()) markDirty(); } }

    private List<StoredRelationship> storedSnapshot() {
        return snapshot().stream().map(state -> new StoredRelationship(state.key().playerId(), state.key().pokemonId(), state.progressionSnapshot(), state.routeSnapshot(), state.cooldownSnapshot(), state.flagSnapshot(), state.partner(), state.partnerSinceMillis(), state.personalityId(), state.householdId())).toList();
    }

    private synchronized void writeSnapshot() {
        try { VersionedStateFile.write(file, gson, storedSnapshot()); }
        catch (IOException exception) { dirty.set(true); throw new IllegalStateException("Unable to persist relationship state", exception); }
    }

    @Override public void close() { if (dirty.get()) markDirty(); AsyncRepositoryClose.shutdownAndAwait(writer); }

    private record StoredRelationship(UUID playerId, UUID pokemonId, Map<String, Long> progression, Map<String, String> routes, Map<String, Long> cooldowns, Map<String, String> flags, boolean partner, long partnerSinceMillis, String personalityId, UUID householdId) {}
}
