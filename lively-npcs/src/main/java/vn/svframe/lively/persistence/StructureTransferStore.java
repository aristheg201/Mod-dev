package vn.svframe.lively.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.svframe.lively.world.SemanticStructureRegistry;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Non-blocking admin import/export store.
 *
 * <p>The command thread only touches an in-memory cache. Directory scans, JSON reads and atomic writes run on one
 * ordered daemon I/O worker. The cache is preloaded when the store is constructed; exports update it before their
 * durable write is queued, so a subsequent import observes the newest authored structure without waiting on disk.</p>
 */
public final class StructureTransferStore {
    private static final Logger LOGGER = LoggerFactory.getLogger("livelynpcs");
    private static final long MAX_FILE_BYTES = 1_048_576L;
    private static final int MAX_FILES = 4096;

    private final Path directory;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final ConcurrentHashMap<String, SemanticStructureRegistry.Structure> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Throwable> failures = new ConcurrentHashMap<>();
    private final ExecutorService io;
    private volatile CompletableFuture<Void> preload;

    public StructureTransferStore(Path directory) {
        this.directory = directory.toAbsolutePath().normalize();
        this.io = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "Lively-StructureTransfer-IO");
            thread.setDaemon(true);
            return thread;
        });
        this.preload = refreshAsync();
    }

    /** Returns immediately after updating the memory cache; the atomic file write is ordered on the I/O worker. */
    public Path exportStructure(SemanticStructureRegistry.Structure structure) {
        if (structure == null) throw new IllegalArgumentException("structure required");
        String key = safe(structure.id());
        Path file = file(key);
        cache.put(key, structure);
        failures.remove(key);
        CompletableFuture.runAsync(() -> write(file, structure), io).whenComplete((ignored, error) -> {
            if (error != null) {
                Throwable cause = unwrap(error);
                failures.put(key, cause);
                LOGGER.error("Asynchronous Lively structure export failed for {}", key, cause);
            }
        });
        return file;
    }

    /** Memory-only read. Disk is never touched by the Minecraft command thread. */
    public Optional<SemanticStructureRegistry.Structure> importStructure(String name) {
        String key = safe(name);
        Throwable failed = failures.get(key);
        if (failed != null) throw new IllegalStateException("structure transfer failed for " + key + ": " + safeMessage(failed));
        return Optional.ofNullable(cache.get(key));
    }

    /** Rebuilds the transfer cache off-thread; useful after an operator copies JSON files into the directory. */
    public synchronized CompletableFuture<Void> refreshAsync() {
        CompletableFuture<Void> next = CompletableFuture.runAsync(this::loadDirectory, io);
        preload = next;
        return next;
    }

    public CompletableFuture<Void> preloadFuture() { return preload; }
    public Map<String, SemanticStructureRegistry.Structure> snapshot() { return Map.copyOf(cache); }

    private void loadDirectory() {
        try {
            Files.createDirectories(directory);
            List<Path> files;
            try (var stream = Files.list(directory)) {
                files = stream.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".json"))
                        .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                        .limit(MAX_FILES + 1L)
                        .toList();
            }
            if (files.size() > MAX_FILES) throw new IOException("too many structure transfer files");

            ConcurrentHashMap<String, SemanticStructureRegistry.Structure> loaded = new ConcurrentHashMap<>();
            List<String> invalid = new ArrayList<>();
            for (Path path : files) {
                String filename = path.getFileName().toString();
                String key = safe(filename.substring(0, filename.length() - 5));
                try {
                    long size = Files.size(path);
                    if (size <= 0L || size > MAX_FILE_BYTES) throw new IOException("invalid structure file size");
                    SemanticStructureRegistry.Structure structure = gson.fromJson(
                            Files.readString(path, StandardCharsets.UTF_8), SemanticStructureRegistry.Structure.class);
                    if (structure == null) throw new IOException("empty structure file");
                    loaded.put(key, structure);
                    failures.remove(key);
                } catch (Exception error) {
                    failures.put(key, error);
                    invalid.add(filename);
                }
            }
            cache.clear();
            cache.putAll(loaded);
            if (!invalid.isEmpty()) LOGGER.warn("Ignored invalid Lively structure transfer files: {}", invalid);
        } catch (IOException error) {
            throw new IllegalStateException("structure transfer preload failed", error);
        }
    }

    private void write(Path file, SemanticStructureRegistry.Structure structure) {
        try {
            Files.createDirectories(directory);
            byte[] bytes = gson.toJson(structure).getBytes(StandardCharsets.UTF_8);
            if (bytes.length <= 0 || bytes.length > MAX_FILE_BYTES) throw new IOException("structure export too large");
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.write(temp, bytes);
            try { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (IOException ignored) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
        } catch (IOException error) {
            throw new IllegalStateException("structure export failed", error);
        }
    }

    private Path file(String key) {
        Path path = directory.resolve(key + ".json").normalize();
        if (!path.startsWith(directory)) throw new IllegalArgumentException("invalid structure file path");
        return path;
    }

    private static String safe(String value) {
        String normalized = value == null ? "" : value.trim().replaceAll("[^A-Za-z0-9_.-]", "_");
        if (normalized.isBlank() || normalized.length() > 128) throw new IllegalArgumentException("invalid structure file name");
        return normalized;
    }

    private static Throwable unwrap(Throwable error) {
        Throwable value = error;
        while (value.getCause() != null && value instanceof java.util.concurrent.CompletionException) value = value.getCause();
        return value;
    }

    private static String safeMessage(Throwable error) {
        String text = error.getMessage();
        if (text == null || text.isBlank()) return error.getClass().getSimpleName();
        text = text.replaceAll("[\\r\\n\\t]", " ").trim();
        return text.length() <= 160 ? text : text.substring(0, 160);
    }
}
