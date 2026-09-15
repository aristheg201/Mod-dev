package vn.svframe.svrelationships.fabric.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import vn.svframe.svrelationships.relationship.RelationshipKey;
import vn.svframe.svrelationships.relationship.RelationshipState;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "svrelationships-relationship-writer");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean dirty = new AtomicBoolean();
    private final AtomicBoolean queued = new AtomicBoolean();

    public RelationshipRepository(Path file) { this.file = file; }

    public void load() {
        if (!Files.exists(file)) return;
        try {
            StoredRelationship[] stored = gson.fromJson(Files.readString(file, StandardCharsets.UTF_8), StoredRelationship[].class);
            if (stored != null) Arrays.stream(stored).forEach(this::restore);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load relationship state", exception);
        }
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

    public void markDirty() {
        dirty.set(true);
        if (!queued.compareAndSet(false, true)) return;
        writer.execute(this::drain);
    }

    private void drain() {
        try {
            while (dirty.getAndSet(false)) writeSnapshot();
        } finally {
            queued.set(false);
            if (dirty.get()) markDirty();
        }
    }

    private List<StoredRelationship> storedSnapshot() {
        return snapshot().stream().map(state -> new StoredRelationship(
                state.key().playerId(), state.key().pokemonId(), state.progressionSnapshot(), state.routeSnapshot(),
                state.cooldownSnapshot(), state.flagSnapshot(), state.partner(), state.partnerSinceMillis(),
                state.personalityId(), state.householdId())).toList();
    }

    private synchronized void writeSnapshot() {
        try {
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temp, gson.toJson(storedSnapshot()), StandardCharsets.UTF_8);
            try { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
        } catch (IOException exception) {
            dirty.set(true);
            throw new IllegalStateException("Unable to persist relationship state", exception);
        }
    }

    @Override
    public void close() {
        if (dirty.get()) markDirty();
        AsyncRepositoryClose.shutdownAndAwait(writer);
    }

    private record StoredRelationship(UUID playerId, UUID pokemonId, Map<String, Long> progression,
                                      Map<String, String> routes, Map<String, Long> cooldowns,
                                      Map<String, String> flags, boolean partner, long partnerSinceMillis,
                                      String personalityId, UUID householdId) {}
}
