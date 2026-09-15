package vn.svframe.svrelationships.fabric.household;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import vn.svframe.svrelationships.household.HouseholdState;

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

public final class HouseholdRepository implements AutoCloseable {
    private final Path file;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Map<UUID, HouseholdState> byOwner = new ConcurrentHashMap<>();
    private final ExecutorService writer = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "svrelationships-household-writer");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean writeQueued = new AtomicBoolean();
    private final AtomicBoolean dirty = new AtomicBoolean();

    public HouseholdRepository(Path file) {
        this.file = file;
    }

    public void load() {
        if (!Files.exists(file)) {
            return;
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            HouseholdState[] states = gson.fromJson(json, HouseholdState[].class);
            if (states != null) {
                Arrays.stream(states).forEach(state -> byOwner.put(state.ownerId(), state));
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load household state", exception);
        }
    }

    public Optional<HouseholdState> get(UUID ownerId) {
        return Optional.ofNullable(byOwner.get(ownerId));
    }

    public void put(HouseholdState state) {
        byOwner.put(state.ownerId(), state);
        markDirty();
    }

    public Optional<HouseholdState> remove(UUID ownerId) {
        HouseholdState removed = byOwner.remove(ownerId);
        if (removed != null) {
            markDirty();
        }
        return Optional.ofNullable(removed);
    }

    public List<HouseholdState> snapshot() {
        return List.copyOf(byOwner.values());
    }

    private void markDirty() {
        dirty.set(true);
        queueWrite();
    }

    private void queueWrite() {
        if (!writeQueued.compareAndSet(false, true)) {
            return;
        }
        writer.execute(() -> {
            try {
                while (dirty.getAndSet(false)) {
                    writeSnapshot();
                }
            } finally {
                writeQueued.set(false);
                if (dirty.get()) {
                    queueWrite();
                }
            }
        });
    }

    public synchronized void flushNow() {
        if (dirty.getAndSet(false) || !Files.exists(file)) {
            writeSnapshot();
        }
    }

    private synchronized void writeSnapshot() {
        try {
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temp, gson.toJson(snapshot()), StandardCharsets.UTF_8);
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            dirty.set(true);
            throw new IllegalStateException("Unable to persist household state", exception);
        }
    }

    @Override
    public void close() {
        flushNow();
        writer.shutdown();
    }
}
