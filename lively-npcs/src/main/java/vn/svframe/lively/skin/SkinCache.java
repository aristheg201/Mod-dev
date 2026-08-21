package vn.svframe.lively.skin;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/** Disk + memory cache for signed texture payloads. */
public final class SkinCache {
    public record TextureData(String value, String signature, String model, String source, Instant fetchedAt) {
        public TextureData {
            value = value == null ? "" : value;
            signature = signature == null ? "" : signature;
            model = model == null || model.isBlank() ? "classic" : model;
            source = source == null ? "" : source;
        }
        public boolean usable() { return !value.isBlank(); }
    }

    private final Path directory;
    private final ConcurrentHashMap<String, TextureData> memory = new ConcurrentHashMap<>();

    public SkinCache(Path directory) { this.directory = directory; }

    public Optional<TextureData> get(String source, Duration ttl) {
        String key = key(source);
        TextureData cached = memory.get(key);
        if (fresh(cached, ttl)) return Optional.of(cached);
        Path file = directory.resolve(key + ".properties");
        if (!Files.isRegularFile(file)) return Optional.empty();
        Properties p = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            p.load(reader);
            TextureData loaded = new TextureData(p.getProperty("value", ""), p.getProperty("signature", ""),
                    p.getProperty("model", "classic"), p.getProperty("source", source),
                    Instant.ofEpochMilli(Long.parseLong(p.getProperty("fetched_at", "0"))));
            if (!fresh(loaded, ttl)) { Files.deleteIfExists(file); return Optional.empty(); }
            memory.put(key, loaded); return Optional.of(loaded);
        } catch (Exception error) { return Optional.empty(); }
    }

    public void put(String source, TextureData data) {
        String key = key(source); memory.put(key, data);
        try {
            Files.createDirectories(directory);
            Path file = directory.resolve(key + ".properties");
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Properties p = new Properties();
            p.setProperty("value", data.value()); p.setProperty("signature", data.signature());
            p.setProperty("model", data.model()); p.setProperty("source", data.source());
            p.setProperty("fetched_at", Long.toString(data.fetchedAt().toEpochMilli()));
            try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) { p.store(writer, "Lively skin cache"); }
            try { Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (IOException ignored) { Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING); }
        } catch (IOException ignored) {}
    }

    public void invalidate(String source) {
        String key = key(source); memory.remove(key);
        try { Files.deleteIfExists(directory.resolve(key + ".properties")); } catch (IOException ignored) {}
    }

    private static boolean fresh(TextureData data, Duration ttl) {
        return data != null && data.usable() && data.fetchedAt().plus(ttl).isAfter(Instant.now());
    }

    private static String key(String source) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception impossible) { throw new IllegalStateException(impossible); }
    }
}
