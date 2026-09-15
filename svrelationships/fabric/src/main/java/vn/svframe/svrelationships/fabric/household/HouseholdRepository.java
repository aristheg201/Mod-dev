package vn.svframe.svrelationships.fabric.household;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import vn.svframe.svrelationships.fabric.persistence.AsyncRepositoryClose;
import vn.svframe.svrelationships.fabric.persistence.VersionedStateFile;
import vn.svframe.svrelationships.household.HouseholdState;

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

public final class HouseholdRepository implements AutoCloseable {
    private final Path file;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Map<UUID, HouseholdState> byOwner = new ConcurrentHashMap<>();
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "svrelationships-household-writer"); t.setDaemon(true); return t; });
    private final AtomicBoolean queued = new AtomicBoolean();
    private final AtomicBoolean dirty = new AtomicBoolean();

    public HouseholdRepository(Path file) { this.file = file; }
    public void load() { var loaded = VersionedStateFile.readArray(file, gson, HouseholdState[].class); HouseholdState[] data = loaded.records(); if (data != null) Arrays.stream(data).forEach(s -> byOwner.put(s.ownerId(), s)); if (loaded.legacySchema() || loaded.recoveredFromBackup()) markDirty(); }
    public Optional<HouseholdState> get(UUID ownerId) { return Optional.ofNullable(byOwner.get(ownerId)); }
    public void put(HouseholdState state) { byOwner.put(state.ownerId(), state); markDirty(); }
    public Optional<HouseholdState> remove(UUID ownerId) { HouseholdState removed = byOwner.remove(ownerId); if (removed != null) markDirty(); return Optional.ofNullable(removed); }
    public List<HouseholdState> snapshot() { return List.copyOf(byOwner.values()); }
    private void markDirty() { dirty.set(true); if (queued.compareAndSet(false, true)) writer.execute(this::drain); }
    private void drain() { try { while (dirty.getAndSet(false)) writeSnapshot(); } finally { queued.set(false); if (dirty.get()) markDirty(); } }
    private synchronized void writeSnapshot() { try { VersionedStateFile.write(file, gson, snapshot()); } catch (IOException e) { dirty.set(true); throw new IllegalStateException("Unable to persist household state", e); } }
    @Override public void close() { if (dirty.get()) markDirty(); AsyncRepositoryClose.shutdownAndAwait(writer); }
}
