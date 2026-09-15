package vn.svframe.svrelationships.fabric.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import vn.svframe.svrelationships.reward.RewardClaimKey;
import vn.svframe.svrelationships.reward.RewardClaimStatus;
import vn.svframe.svrelationships.reward.RewardResolution;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class RewardClaimRepository implements AutoCloseable {
    private final Path file;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final ConcurrentHashMap<RewardClaimKey, Claim> claims = new ConcurrentHashMap<>();
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "svrelationships-reward-writer");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean dirty = new AtomicBoolean();
    private final AtomicBoolean queued = new AtomicBoolean();

    public RewardClaimRepository(Path file) { this.file = file; }

    public void load() {
        if (!Files.exists(file)) return;
        try {
            Claim[] data = gson.fromJson(Files.readString(file, StandardCharsets.UTF_8), Claim[].class);
            if (data != null) Arrays.stream(data).forEach(c -> claims.put(c.key(), c));
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load reward claims", exception);
        }
    }

    public Optional<Claim> get(RewardClaimKey key) { return Optional.ofNullable(claims.get(key)); }

    public synchronized Claim reserve(RewardClaimKey key) {
        Claim current = claims.get(key);
        if (current != null) return current;
        Claim claim = new Claim(key, RewardClaimStatus.RESERVED, null, System.currentTimeMillis());
        claims.put(key, claim);
        markDirty();
        return claim;
    }

    public synchronized Claim resolve(RewardClaimKey key, RewardResolution resolution) {
        Claim current = claims.get(key);
        if (current != null && current.status() == RewardClaimStatus.DELIVERED) return current;
        Claim next = new Claim(key, RewardClaimStatus.RESOLVED, resolution, System.currentTimeMillis());
        claims.put(key, next);
        markDirty();
        return next;
    }

    public synchronized Claim delivered(RewardClaimKey key) {
        Claim current = claims.get(key);
        if (current == null || current.resolution() == null) throw new IllegalStateException("Claim is not resolved");
        if (current.status() == RewardClaimStatus.DELIVERED) return current;
        Claim next = new Claim(key, RewardClaimStatus.DELIVERED, current.resolution(), System.currentTimeMillis());
        claims.put(key, next);
        markDirty();
        return next;
    }

    public int size() { return claims.size(); }

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
            Files.writeString(temp, gson.toJson(List.copyOf(claims.values())), StandardCharsets.UTF_8);
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            dirty.set(true);
            throw new IllegalStateException("Unable to persist reward claims", exception);
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

    public record Claim(RewardClaimKey key, RewardClaimStatus status, RewardResolution resolution, long updatedAtMillis) {}
}
