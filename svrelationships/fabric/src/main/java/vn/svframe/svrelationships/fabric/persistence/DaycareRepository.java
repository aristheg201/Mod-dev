package vn.svframe.svrelationships.fabric.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import vn.svframe.svrelationships.family.DaycareSession;

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

public final class DaycareRepository implements AutoCloseable {
    private final Path file;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final ConcurrentHashMap<UUID, DaycareSession> sessions = new ConcurrentHashMap<>();
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "svrelationships-daycare-writer"); t.setDaemon(true); return t; });
    private final AtomicBoolean dirty = new AtomicBoolean();
    private final AtomicBoolean queued = new AtomicBoolean();

    public DaycareRepository(Path file) { this.file = file; }

    public void load() {
        var loaded = VersionedStateFile.readArray(file, gson, DaycareSession[].class);
        DaycareSession[] data = loaded.records();
        if (data != null) Arrays.stream(data).forEach(s -> sessions.put(s.sessionId(), s));
        if (loaded.legacySchema() || loaded.recoveredFromBackup()) markDirty();
    }

    public Optional<DaycareSession> get(UUID id) { return Optional.ofNullable(sessions.get(id)); }
    public List<DaycareSession> byOwner(UUID ownerId) { return sessions.values().stream().filter(s -> s.ownerId().equals(ownerId)).toList(); }
    public List<DaycareSession> active() { return sessions.values().stream().filter(s -> "ACTIVE".equals(s.status()) || "REWARD_PENDING".equals(s.status())).toList(); }
    public void put(DaycareSession session) { sessions.put(session.sessionId(), session); markDirty(); }
    public void remove(UUID id) { if (sessions.remove(id) != null) markDirty(); }

    private void markDirty() { dirty.set(true); if (queued.compareAndSet(false, true)) writer.execute(this::drain); }
    private void drain() { try { while (dirty.getAndSet(false)) write(); } finally { queued.set(false); if (dirty.get()) markDirty(); } }
    private synchronized void write() { try { VersionedStateFile.write(file, gson, List.copyOf(sessions.values())); } catch (IOException e) { dirty.set(true); throw new IllegalStateException("Unable to persist daycare state", e); } }
    @Override public void close() { if (dirty.get()) markDirty(); AsyncRepositoryClose.shutdownAndAwait(writer); }
}
