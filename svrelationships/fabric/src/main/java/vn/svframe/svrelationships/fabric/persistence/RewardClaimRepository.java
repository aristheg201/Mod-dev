package vn.svframe.svrelationships.fabric.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import vn.svframe.svrelationships.reward.RewardClaimKey;
import vn.svframe.svrelationships.reward.RewardClaimStatus;
import vn.svframe.svrelationships.reward.RewardResolution;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class RewardClaimRepository {
    private final Path file;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final ConcurrentHashMap<RewardClaimKey, Claim> claims = new ConcurrentHashMap<>();
    public RewardClaimRepository(Path file) { this.file = file; }
    public void load() {
        if (!Files.exists(file)) return;
        try {
            Claim[] data = gson.fromJson(Files.readString(file, StandardCharsets.UTF_8), Claim[].class);
            if (data != null) Arrays.stream(data).forEach(c -> claims.put(c.key(), c));
        } catch (IOException e) { throw new IllegalStateException("Unable to load reward claims", e); }
    }
    public Optional<Claim> get(RewardClaimKey key) { return Optional.ofNullable(claims.get(key)); }
    public synchronized Claim reserve(RewardClaimKey key) {
        Claim current = claims.get(key);
        if (current != null) return current;
        Claim claim = new Claim(key, RewardClaimStatus.RESERVED, null, System.currentTimeMillis());
        claims.put(key, claim); write(); return claim;
    }
    public synchronized Claim resolve(RewardClaimKey key, RewardResolution resolution) {
        Claim next = new Claim(key, RewardClaimStatus.RESOLVED, resolution, System.currentTimeMillis());
        claims.put(key, next); write(); return next;
    }
    public synchronized Claim delivered(RewardClaimKey key) {
        Claim current = claims.get(key);
        if (current == null || current.resolution() == null) throw new IllegalStateException("Claim is not resolved");
        Claim next = new Claim(key, RewardClaimStatus.DELIVERED, current.resolution(), System.currentTimeMillis());
        claims.put(key, next); write(); return next;
    }
    private void write() {
        try {
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temp, gson.toJson(List.copyOf(claims.values())), StandardCharsets.UTF_8);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) { throw new IllegalStateException("Unable to persist reward claims", e); }
    }
    public record Claim(RewardClaimKey key, RewardClaimStatus status, RewardResolution resolution, long updatedAtMillis) {}
}
