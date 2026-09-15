package vn.svframe.svrelationships.fabric.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import vn.svframe.svrelationships.family.LineageRecord;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class LineageRepository implements AutoCloseable {
    private final Path file;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final ConcurrentHashMap<UUID, LineageRecord> records = new ConcurrentHashMap<>();
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "svrelationships-lineage-writer"); t.setDaemon(true); return t; });
    private final AtomicBoolean dirty = new AtomicBoolean();
    private final AtomicBoolean queued = new AtomicBoolean();

    public LineageRepository(Path file) { this.file = file; }
    public void load() { var loaded = VersionedStateFile.readArray(file, gson, LineageRecord[].class); LineageRecord[] data = loaded.records(); if (data != null) Arrays.stream(data).forEach(r -> records.put(r.pokemonId(), r)); if (loaded.legacySchema() || loaded.recoveredFromBackup()) markDirty(); }
    public Optional<LineageRecord> get(UUID pokemonId) { return Optional.ofNullable(records.get(pokemonId)); }
    public List<LineageRecord> byOwner(UUID ownerId) { return records.values().stream().filter(r -> r.ownerId().equals(ownerId)).toList(); }
    public List<LineageRecord> childrenOf(UUID parentId) { return records.values().stream().filter(r -> r.parentPokemonIds().contains(parentId)).toList(); }
    public int size() { return records.size(); }
    public void put(LineageRecord record) { records.put(record.pokemonId(), record); markDirty(); }
    private void markDirty() { dirty.set(true); if (queued.compareAndSet(false, true)) writer.execute(this::drain); }
    private void drain() { try { while (dirty.getAndSet(false)) writeSnapshot(); } finally { queued.set(false); if (dirty.get()) markDirty(); } }
    private synchronized void writeSnapshot() { try { VersionedStateFile.write(file, gson, List.copyOf(records.values())); } catch (IOException e) { dirty.set(true); throw new IllegalStateException("Unable to persist lineage", e); } }
    @Override public void close() { if (dirty.get()) markDirty(); AsyncRepositoryClose.shutdownAndAwait(writer); }
}
