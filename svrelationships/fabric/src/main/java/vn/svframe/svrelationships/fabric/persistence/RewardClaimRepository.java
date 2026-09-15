package vn.svframe.svrelationships.fabric.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import vn.svframe.svrelationships.reward.RewardClaimKey;
import vn.svframe.svrelationships.reward.RewardClaimStatus;
import vn.svframe.svrelationships.reward.RewardResolution;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class RewardClaimRepository implements AutoCloseable {
    private final Path file;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final ConcurrentHashMap<RewardClaimKey, Claim> claims = new ConcurrentHashMap<>();
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "svrelationships-reward-writer"); t.setDaemon(true); return t; });
    private final AtomicBoolean dirty = new AtomicBoolean();
    private final AtomicBoolean queued = new AtomicBoolean();

    public RewardClaimRepository(Path file) { this.file = file; }
    public void load() { var loaded = VersionedStateFile.readArray(file, gson, Claim[].class); Claim[] data = loaded.records(); if (data != null) Arrays.stream(data).forEach(c -> claims.put(c.key(), c)); if (loaded.legacySchema() || loaded.recoveredFromBackup()) markDirty(); }
    public Optional<Claim> get(RewardClaimKey key) { return Optional.ofNullable(claims.get(key)); }
    public synchronized Claim reserve(RewardClaimKey key) { Claim current = claims.get(key); if (current != null) return current; Claim claim = new Claim(key, RewardClaimStatus.RESERVED, null, System.currentTimeMillis()); claims.put(key, claim); markDirty(); return claim; }
    public synchronized Claim resolve(RewardClaimKey key, RewardResolution resolution) { Claim current = claims.get(key); if (current != null && current.status() == RewardClaimStatus.DELIVERED) return current; Claim next = new Claim(key, RewardClaimStatus.RESOLVED, resolution, System.currentTimeMillis()); claims.put(key, next); markDirty(); return next; }
    public synchronized Claim delivered(RewardClaimKey key) { Claim current = claims.get(key); if (current == null || current.resolution() == null) throw new IllegalStateException("Claim is not resolved"); if (current.status() == RewardClaimStatus.DELIVERED) return current; Claim next = new Claim(key, RewardClaimStatus.DELIVERED, current.resolution(), System.currentTimeMillis()); claims.put(key, next); markDirty(); return next; }
    public int size() { return claims.size(); }
    private void markDirty() { dirty.set(true); if (queued.compareAndSet(false, true)) writer.execute(this::drain); }
    private void drain() { try { while (dirty.getAndSet(false)) writeSnapshot(); } finally { queued.set(false); if (dirty.get()) markDirty(); } }
    private synchronized void writeSnapshot() { try { VersionedStateFile.write(file, gson, List.copyOf(claims.values())); } catch (IOException e) { dirty.set(true); throw new IllegalStateException("Unable to persist reward claims", e); } }
    @Override public void close() { if (dirty.get()) markDirty(); AsyncRepositoryClose.shutdownAndAwait(writer); }
    public record Claim(RewardClaimKey key, RewardClaimStatus status, RewardResolution resolution, long updatedAtMillis) {}
}
