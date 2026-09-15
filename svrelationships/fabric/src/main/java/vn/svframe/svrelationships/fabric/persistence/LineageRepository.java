package vn.svframe.svrelationships.fabric.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import vn.svframe.svrelationships.family.LineageRecord;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class LineageRepository implements AutoCloseable {
    private final Path file;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final ConcurrentHashMap<UUID, LineageRecord> records = new ConcurrentHashMap<>();
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "svrelationships-lineage-writer");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean dirty = new AtomicBoolean();
    private final AtomicBoolean queued = new AtomicBoolean();

    public LineageRepository(Path file) { this.file = file; }

    public void load() {
        if (!Files.exists(file)) return;
        try {
            LineageRecord[] data = gson.fromJson(Files.readString(file, StandardCharsets.UTF_8), LineageRecord[].class);
            if (data != null) Arrays.stream(data).forEach(r -> records.put(r.pokemonId(), r));
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load lineage", exception);
        }
    }

    public Optional<LineageRecord> get(UUID pokemonId) { return Optional.ofNullable(records.get(pokemonId)); }
    public List<LineageRecord> byOwner(UUID ownerId) { return records.values().stream().filter(r -> r.ownerId().equals(ownerId)).toList(); }
    public List<LineageRecord> childrenOf(UUID parentId) { return records.values().stream().filter(r -> r.parentPokemonIds().contains(parentId)).toList(); }
    public int size() { return records.size(); }

    public void put(LineageRecord record) {
        records.put(record.pokemonId(), record);
        markDirty();
    }

    private void markDirty() {
        dirty.set(true);
        if (queued.compareAndSet(false, true)) writer.execute(this::drain);
    }

    private void drain() {
        try {
            while (dirty.getAndSet(false)) writeSnapshot();
        } finally {
            queued.set(false);
            if (dirty.get()) markDirty();
        }
    }

    private synchronized void writeSnapshot() {
        try {
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temp, gson.toJson(List.copyOf(records.values())), StandardCharsets.UTF_8);
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            dirty.set(true);
            throw new IllegalStateException("Unable to persist lineage", exception);
        }
    }

    @Override
    public void close() {
        if (dirty.get()) markDirty();
        writer.shutdown();
        try {
            if (!writer.awaitTermination(10, TimeUnit.SECONDS)) writer.shutdownNow();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            writer.shutdownNow();
        }
    }
}
