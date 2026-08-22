package vn.svframe.lively.persistence;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.CRC32;

/** Append-only bounded world history journal with per-record CRC and an off-thread write lane. */
public final class WorldHistoryJournal implements AutoCloseable {
    public record Entry(long sequence, Instant at, String type, String subject, Map<String,String> facts) {
        public Entry {
            Objects.requireNonNull(at);
            Objects.requireNonNull(type);
            Objects.requireNonNull(subject);
            facts = Map.copyOf(facts);
        }
    }

    private final Path file;
    private final long maxBytes;
    private final ExecutorService io;
    private volatile CompletableFuture<Void> tail = CompletableFuture.completedFuture(null);
    private volatile boolean closed;

    public WorldHistoryJournal(Path file, long maxBytes) {
        this.file = Objects.requireNonNull(file);
        this.maxBytes = Math.max(1_048_576L, Math.min(1_073_741_824L, maxBytes));
        this.io = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "Lively-History-IO");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** Synchronous primitive retained for deterministic tests and startup tooling. Runtime code should use appendAsync. */
    public synchronized void append(Entry entry) throws IOException {
        if (closed) throw new IOException("world history journal is closed");
        appendInternal(entry);
    }

    /** Serializes history writes away from the Minecraft thread while preserving event order. */
    public synchronized CompletableFuture<Void> appendAsync(Entry entry) {
        if (closed) return CompletableFuture.failedFuture(new IllegalStateException("world history journal is closed"));
        tail = tail.handle((ignored, previousError) -> null).thenRunAsync(() -> {
            try {
                appendInternal(entry);
            } catch (IOException error) {
                throw new CompletionException(error);
            }
        }, io);
        return tail;
    }

    public synchronized CompletableFuture<Void> flush() { return tail; }

    public synchronized List<Entry> readAll() throws IOException {
        if (!Files.exists(file)) return List.of();
        List<Entry> out = new ArrayList<>();
        try (BufferedReader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.length() > 65536) throw new IOException("history record too large");
                int first = line.indexOf('|');
                if (first < 1) throw new IOException("invalid history record");
                String hex = line.substring(0, first), payload = line.substring(first + 1);
                CRC32 crc = new CRC32();
                crc.update(payload.getBytes(StandardCharsets.UTF_8));
                if (!Long.toHexString(crc.getValue()).equals(hex)) throw new IOException("history crc mismatch");
                String[] p = payload.split("\\|", 5);
                if (p.length != 5) throw new IOException("invalid history payload");
                out.add(new Entry(Long.parseLong(p[0]), Instant.ofEpochMilli(Long.parseLong(p[1])), dec(p[2]), dec(p[3]), parseFacts(dec(p[4]))));
            }
        }
        return List.copyOf(out);
    }

    private synchronized void appendInternal(Entry entry) throws IOException {
        Files.createDirectories(file.getParent());
        if (Files.exists(file) && Files.size(file) > maxBytes) rotate();
        String payload = entry.sequence() + "|" + entry.at().toEpochMilli() + "|" + enc(entry.type()) + "|" + enc(entry.subject()) + "|" + enc(serializeFacts(entry.facts()));
        CRC32 crc = new CRC32();
        crc.update(payload.getBytes(StandardCharsets.UTF_8));
        try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            w.write(Long.toHexString(crc.getValue()));
            w.write('|');
            w.write(payload);
            w.newLine();
        }
    }

    private void rotate() throws IOException {
        Path old = file.resolveSibling(file.getFileName() + ".1");
        Files.deleteIfExists(old);
        Files.move(file, old);
    }

    private static String serializeFacts(Map<String,String> facts) {
        StringBuilder b = new StringBuilder();
        facts.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> b.append(enc(e.getKey())).append('=').append(enc(e.getValue())).append(';'));
        return b.toString();
    }

    private static Map<String,String> parseFacts(String s) {
        java.util.HashMap<String,String> m = new java.util.HashMap<>();
        for (String part : s.split(";")) {
            if (part.isEmpty()) continue;
            int i = part.indexOf('=');
            if (i > 0) m.put(dec(part.substring(0, i)), dec(part.substring(i + 1)));
        }
        return Map.copyOf(m);
    }

    private static String enc(String s) { return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8)); }
    private static String dec(String s) { return new String(Base64.getUrlDecoder().decode(s), StandardCharsets.UTF_8); }

    @Override
    public synchronized void close() {
        closed = true;
        io.shutdown();
    }
}
