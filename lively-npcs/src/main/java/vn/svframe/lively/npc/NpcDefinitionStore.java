package vn.svframe.lively.npc;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Crash-safe native NPC definition store.
 *
 * Format 3 adds a SHA-256 checksum to every record and a verified backup while
 * retaining transparent read/migration support for formats 1 and 2.
 */
public final class NpcDefinitionStore {
    private static final int FORMAT = 3;
    private static final long MAX_FILE_BYTES = 64L * 1024L * 1024L;
    private static final int MAX_LINE_CHARS = 65_536;
    private static final int MAX_DEFINITIONS = 100_000;

    private final Path file;
    private final Path backup;

    public NpcDefinitionStore(Path file) {
        this.file = file;
        this.backup = file.resolveSibling(file.getFileName() + ".bak");
    }

    public Map<UUID, NpcDefinition> loadAll() {
        LoadResult primary = read(file);
        LoadResult selected = primary.valid() ? primary : read(backup);
        if (!selected.valid()) {
            if (!Files.isRegularFile(file) && !Files.isRegularFile(backup)) return Map.of();
            throw new IllegalStateException("failed to load NPC definitions from primary and backup");
        }
        if (selected.format() < FORMAT || !primary.valid()) saveAll(selected.definitions());
        return selected.definitions();
    }

    private LoadResult read(Path path) {
        if (!Files.isRegularFile(path)) return LoadResult.missing();
        try {
            long size = Files.size(path);
            if (size <= 0L || size > MAX_FILE_BYTES) return LoadResult.invalid();
            Map<UUID, NpcDefinition> result = new HashMap<>();
            try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                String first = reader.readLine();
                int format = Integer.parseInt(first == null ? "0" : first.trim());
                if (format < 1 || format > FORMAT) return LoadResult.invalid();
                String line;
                int records = 0;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) continue;
                    if (line.length() > MAX_LINE_CHARS || ++records > MAX_DEFINITIONS) return LoadResult.invalid();
                    NpcDefinition definition = decodeRecord(line, format);
                    if (definition == null || result.putIfAbsent(definition.id(), definition) != null) return LoadResult.invalid();
                }
                return new LoadResult(true, format, Map.copyOf(result));
            }
        } catch (Exception error) {
            return LoadResult.invalid();
        }
    }

    private static NpcDefinition decodeRecord(String line, int format) {
        String[] p = line.split("\\t", -1);
        if (p.length < 18) return null;
        if (format >= 3) {
            if (p.length < 20) return null;
            int lastTab = line.lastIndexOf('\t');
            if (lastTab <= 0) return null;
            String payload = line.substring(0, lastTab);
            if (!MessageDigest.isEqual(sha256(payload).getBytes(StandardCharsets.US_ASCII), p[19].getBytes(StandardCharsets.US_ASCII))) return null;
        }
        UUID id = UUID.fromString(p[0]);
        Map<String, String> metadata = format >= 2 && p.length >= 19 ? decodeMap(p[18]) : Map.of();
        return new NpcDefinition(
                id, bounded(unescape(p[1]), 256), bounded(unescape(p[2]), 256), NpcDefinition.BodyType.valueOf(p[3]),
                bounded(unescape(p[4]), 4096), bounded(unescape(p[5]), 8192), bounded(unescape(p[6]), 256),
                finite(p[7]), finite(p[8]), finite(p[9]), finiteFloat(p[10]), finiteFloat(p[11]),
                Boolean.parseBoolean(p[12]), Boolean.parseBoolean(p[13]), Boolean.parseBoolean(p[14]),
                Boolean.parseBoolean(p[15]), Boolean.parseBoolean(p[16]), Boolean.parseBoolean(p[17]), metadata);
    }

    public synchronized void saveAll(Map<UUID, NpcDefinition> definitions) {
        if (definitions == null || definitions.size() > MAX_DEFINITIONS) throw new IllegalArgumentException("too many NPC definitions");
        try {
            Files.createDirectories(file.getParent());
            StringBuilder out = new StringBuilder(Math.max(32, definitions.size() * 256));
            out.append(FORMAT).append('\n');
            for (NpcDefinition d : definitions.values().stream().sorted(Comparator.comparing(v -> v.id().toString())).toList()) {
                String payload = encodeRecord(d);
                String record = payload + '\t' + sha256(payload);
                if (record.length() > MAX_LINE_CHARS) throw new IOException("NPC definition record too large: " + d.id());
                out.append(record).append('\n');
                if (out.length() > MAX_FILE_BYTES) throw new IOException("NPC definition state too large");
            }
            byte[] bytes = out.toString().getBytes(StandardCharsets.UTF_8);
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            if (Files.isRegularFile(file) && read(file).valid()) Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
            try { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (IOException ignored) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
        } catch (IOException error) {
            throw new IllegalStateException("failed to save NPC definitions", error);
        }
    }

    private static String encodeRecord(NpcDefinition d) {
        return String.join("\t",
                d.id().toString(), escape(d.name()), escape(d.role()), d.bodyType().name(), escape(d.bodyKey()),
                escape(d.skinName()), escape(d.world()), Double.toString(d.x()), Double.toString(d.y()), Double.toString(d.z()),
                Float.toString(d.yaw()), Float.toString(d.pitch()), Boolean.toString(d.spawned()), Boolean.toString(d.aiEnabled()),
                Boolean.toString(d.invulnerable()), Boolean.toString(d.gravity()), Boolean.toString(d.silent()),
                Boolean.toString(d.nameVisible()), encodeMap(d.metadata()));
    }

    private static String encodeMap(Map<String, String> map) {
        if (map == null || map.isEmpty()) return "";
        if (map.size() > 512) throw new IllegalArgumentException("too many NPC metadata entries");
        StringBuilder out = new StringBuilder();
        map.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            String key = bounded(entry.getKey(), 256);
            String value = bounded(entry.getValue(), 8192);
            if (key.indexOf('\u001f') >= 0 || key.indexOf('\u001e') >= 0 || value.indexOf('\u001f') >= 0 || value.indexOf('\u001e') >= 0) {
                throw new IllegalArgumentException("NPC metadata contains reserved separators");
            }
            out.append(key).append('\u001f').append(value).append('\u001e');
        });
        return Base64.getUrlEncoder().withoutPadding().encodeToString(out.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static Map<String, String> decodeMap(String encoded) {
        if (encoded == null || encoded.isBlank()) return Map.of();
        byte[] decoded = Base64.getUrlDecoder().decode(encoded);
        if (decoded.length > 4 * 1024 * 1024) throw new IllegalArgumentException("NPC metadata payload too large");
        String text = new String(decoded, StandardCharsets.UTF_8);
        HashMap<String, String> map = new HashMap<>();
        for (String entry : text.split(String.valueOf('\u001e'), -1)) {
            if (entry.isEmpty()) continue;
            int split = entry.indexOf('\u001f');
            if (split <= 0 || map.size() >= 512) throw new IllegalArgumentException("invalid NPC metadata");
            String key = bounded(entry.substring(0, split), 256);
            String value = bounded(entry.substring(split + 1), 8192);
            map.put(key, value);
        }
        return Map.copyOf(map);
    }

    private static double finite(String raw) {
        double value = Double.parseDouble(raw);
        if (!Double.isFinite(value) || Math.abs(value) > 30_000_000D) throw new IllegalArgumentException("invalid NPC coordinate");
        return value;
    }

    private static float finiteFloat(String raw) {
        float value = Float.parseFloat(raw);
        if (!Float.isFinite(value) || Math.abs(value) > 360_000F) throw new IllegalArgumentException("invalid NPC rotation");
        return value;
    }

    private static String bounded(String value, int max) {
        if (value == null) return "";
        if (value.length() > max) throw new IllegalArgumentException("NPC definition text exceeds limit");
        return value;
    }

    private static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception impossible) { throw new IllegalStateException(impossible); }
    }

    private static String escape(String value) {
        return bounded(value == null ? "" : value, 8192).replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n");
    }

    private static String unescape(String value) {
        return value.replace("\\n", "\n").replace("\\t", "\t").replace("\\\\", "\\");
    }

    private record LoadResult(boolean valid, int format, Map<UUID, NpcDefinition> definitions) {
        static LoadResult missing() { return new LoadResult(false, 0, Map.of()); }
        static LoadResult invalid() { return new LoadResult(false, 0, Map.of()); }
    }
}
