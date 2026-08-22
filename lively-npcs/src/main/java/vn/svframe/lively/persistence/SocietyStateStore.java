package vn.svframe.lively.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSerializer;
import vn.svframe.lively.economy.DebtEngine;
import vn.svframe.lively.economy.GamblingEngine;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Versioned crash-safe persistence for 1.0.1 debt and gambling state. */
public final class SocietyStateStore implements AutoCloseable {
    private static final int SCHEMA = 1;
    private static final long MAX_BYTES = 64L * 1024L * 1024L;
    private final Path file;
    private final Path backup;
    private final Gson gson;
    private final ExecutorService io;

    public SocietyStateStore(Path file) {
        this.file = file;
        this.backup = file.resolveSibling(file.getFileName() + ".bak");
        this.gson = new GsonBuilder().enableComplexMapKeySerialization()
                .registerTypeAdapter(Instant.class, (JsonSerializer<Instant>) (src, type, context) -> context.serialize(src.toString()))
                .registerTypeAdapter(Instant.class, (JsonDeserializer<Instant>) (json, type, context) -> Instant.parse(json.getAsString()))
                .create();
        this.io = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "Lively-Society-IO"); thread.setDaemon(true); return thread;
        });
    }

    public Optional<Bundle> load() {
        Optional<Bundle> primary = read(file);
        return primary.isPresent() ? primary : read(backup);
    }

    public CompletableFuture<Void> saveAsync(Bundle bundle) {
        String payload = gson.toJson(bundle);
        String envelope = gson.toJson(new Envelope(SCHEMA, sha256(payload), payload));
        return CompletableFuture.runAsync(() -> write(envelope), io);
    }

    private Optional<Bundle> read(Path path) {
        if (!Files.isRegularFile(path)) return Optional.empty();
        try {
            long size = Files.size(path);
            if (size <= 0L || size > MAX_BYTES) throw new IOException("invalid society state size");
            JsonObject root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
            if (root.get("schema").getAsInt() != SCHEMA) throw new IOException("unsupported society schema");
            String payload = root.get("payload").getAsString();
            String expected = root.get("checksum").getAsString();
            if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII), sha256(payload).getBytes(StandardCharsets.US_ASCII))) {
                throw new IOException("society checksum mismatch");
            }
            return Optional.ofNullable(gson.fromJson(payload, Bundle.class));
        } catch (Exception ignored) { return Optional.empty(); }
    }

    private void write(String envelope) {
        try {
            byte[] bytes = envelope.getBytes(StandardCharsets.UTF_8);
            if (bytes.length > MAX_BYTES) throw new IOException("society state too large");
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            if (Files.isRegularFile(file)) Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
            try { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (IOException ignored) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
        } catch (IOException error) { throw new CompletionException(error); }
    }

    private static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception impossible) { throw new IllegalStateException(impossible); }
    }

    @Override public void close() { io.shutdown(); }
    private record Envelope(int schema, String checksum, String payload) {}
    public record Bundle(DebtEngine.Snapshot debts, GamblingEngine.Snapshot gambling) {}
}
