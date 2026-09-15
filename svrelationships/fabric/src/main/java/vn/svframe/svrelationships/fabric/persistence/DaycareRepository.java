package vn.svframe.svrelationships.fabric.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import vn.svframe.svrelationships.family.DaycareSession;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
import java.util.concurrent.atomic.AtomicBoolean;

public final class DaycareRepository implements AutoCloseable {
    private final Path file;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final ConcurrentHashMap<UUID, DaycareSession> sessions = new ConcurrentHashMap<>();
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> { var t = new Thread(r, "svrelationships-daycare-writer"); t.setDaemon(true); return t; });
    private final AtomicBoolean dirty = new AtomicBoolean();
    private final AtomicBoolean queued = new AtomicBoolean();

    public DaycareRepository(Path file) { this.file = file; }
    public void load() {
        if (!Files.exists(file)) return;
        try {
            DaycareSession[] data = gson.fromJson(Files.readString(file, StandardCharsets.UTF_8), DaycareSession[].class);
            if (data != null) Arrays.stream(data).forEach(s -> sessions.put(s.sessionId(), s));
        } catch (IOException e) { throw new IllegalStateException("Unable to load daycare state", e); }
    }
    public Optional<DaycareSession> get(UUID id) { return Optional.ofNullable(sessions.get(id)); }
    public List<DaycareSession> byOwner(UUID ownerId) { return sessions.values().stream().filter(s -> s.ownerId().equals(ownerId)).toList(); }
    public List<DaycareSession> active() { return sessions.values().stream().filter(s -> "ACTIVE".equals(s.status())).toList(); }
    public void put(DaycareSession session) { sessions.put(session.sessionId(), session); markDirty(); }
    public void remove(UUID id) { if (sessions.remove(id) != null) markDirty(); }
    private void markDirty() { dirty.set(true); if (queued.compareAndSet(false, true)) writer.execute(this::drain); }
    private void drain() { try { while (dirty.getAndSet(false)) write(); } finally { queued.set(false); if (dirty.get()) markDirty(); } }
    private synchronized void write() {
        try {
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temp, gson.toJson(List.copyOf(sessions.values())), StandardCharsets.UTF_8);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) { dirty.set(true); throw new IllegalStateException("Unable to persist daycare state", e); }
    }
    public synchronized void flushNow() { if (dirty.getAndSet(false) || !Files.exists(file)) write(); }
    @Override public void close() { flushNow(); writer.shutdown(); }
}
