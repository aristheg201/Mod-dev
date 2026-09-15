package vn.svframe.svrelationships.fabric.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import vn.svframe.svrelationships.family.LineageRecord;

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

public final class LineageRepository {
    private final Path file;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final ConcurrentHashMap<UUID, LineageRecord> records = new ConcurrentHashMap<>();
    public LineageRepository(Path file) { this.file = file; }
    public void load() {
        if (!Files.exists(file)) return;
        try {
            LineageRecord[] data = gson.fromJson(Files.readString(file, StandardCharsets.UTF_8), LineageRecord[].class);
            if (data != null) Arrays.stream(data).forEach(r -> records.put(r.pokemonId(), r));
        } catch (IOException e) { throw new IllegalStateException("Unable to load lineage", e); }
    }
    public Optional<LineageRecord> get(UUID pokemonId) { return Optional.ofNullable(records.get(pokemonId)); }
    public List<LineageRecord> byOwner(UUID ownerId) { return records.values().stream().filter(r -> r.ownerId().equals(ownerId)).toList(); }
    public List<LineageRecord> childrenOf(UUID parentId) { return records.values().stream().filter(r -> r.parentPokemonIds().contains(parentId)).toList(); }
    public synchronized void put(LineageRecord record) { records.put(record.pokemonId(), record); write(); }
    private void write() {
        try {
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temp, gson.toJson(List.copyOf(records.values())), StandardCharsets.UTF_8);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) { throw new IllegalStateException("Unable to persist lineage", e); }
    }
}
