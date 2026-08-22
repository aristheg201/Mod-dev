package vn.svframe.lively.persistence;

import vn.svframe.lively.model.NpcSnapshot;
import vn.svframe.lively.model.NpcState;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.CRC32;

/** Versioned, checksummed, atomic per-NPC persistence. No world access occurs here. */
public final class NpcStateStore {
    private static final int MAGIC = 0x4C4E5043;
    private static final int VERSION = 1;
    private static final int MAX_FILE_BYTES = 4 * 1024 * 1024;
    private static final int MAX_STRING_BYTES = 16 * 1024;
    private static final int MAX_MAP = 4096;
    private static final int MAX_MEMORIES = 4096;
    private final Path root;

    public NpcStateStore(Path root) { this.root = root.toAbsolutePath().normalize(); }

    public void save(NpcState.StateData state) throws IOException {
        Files.createDirectories(root);
        byte[] payload = encode(state);
        CRC32 crc = new CRC32(); crc.update(payload);
        Path target = path(state.id());
        Path temp = target.resolveSibling(target.getFileName() + ".tmp-" + UUID.randomUUID());
        try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(temp, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE))) {
            out.writeInt(MAGIC); out.writeInt(VERSION); out.writeInt(payload.length); out.writeLong(crc.getValue()); out.write(payload);
        }
        try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE)) { channel.force(true); }
        try {
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    public Optional<NpcState.StateData> load(UUID id) throws IOException {
        Path file = path(id);
        if (!Files.exists(file)) return Optional.empty();
        long fileSize = Files.size(file);
        if (fileSize < 20L || fileSize > MAX_FILE_BYTES + 20L) throw new IOException("invalid lively state size");
        try (DataInputStream in = new DataInputStream(Files.newInputStream(file))) {
            if (in.readInt() != MAGIC) throw new IOException("invalid lively state magic");
            int version = in.readInt();
            if (version != VERSION) throw new IOException("unsupported lively state version " + version);
            int length = in.readInt();
            if (length < 0 || length > MAX_FILE_BYTES) throw new IOException("invalid lively payload length");
            long expectedCrc = in.readLong();
            byte[] payload = in.readNBytes(length);
            if (payload.length != length) throw new EOFException("truncated lively state");
            CRC32 crc = new CRC32(); crc.update(payload);
            if (crc.getValue() != expectedCrc) throw new IOException("lively state checksum mismatch");
            NpcState.StateData data = decode(payload);
            if (!id.equals(data.id())) throw new IOException("lively state id mismatch");
            return Optional.of(data);
        }
    }

    public List<UUID> storedIds() throws IOException {
        if (!Files.exists(root)) return List.of();
        try (var stream = Files.list(root)) {
            return stream.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".lnpc"))
                    .map(name -> name.substring(0, name.length() - 5))
                    .map(name -> {
                        try { return UUID.fromString(name); }
                        catch (IllegalArgumentException ignored) { return null; }
                    })
                    .filter(java.util.Objects::nonNull)
                    .toList();
        }
    }

    private Path path(UUID id) {
        Path result = root.resolve(id.toString() + ".lnpc").normalize();
        if (!result.getParent().equals(root)) throw new IllegalArgumentException("invalid npc path");
        return result;
    }

    private byte[] encode(NpcState.StateData state) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(buffer)) {
            writeUuid(out, state.id()); out.writeLong(state.revision()); writeString(out, state.name()); writeString(out, state.role());
            writeDoubleMap(out, state.traits()); writeDoubleMap(out, state.needs());
            out.writeInt(state.beliefs().size());
            for (var entry : state.beliefs().entrySet()) {
                writeString(out, entry.getKey()); var b = entry.getValue(); writeString(out, b.value()); out.writeDouble(b.confidence());
                writeNullableUuid(out, b.source()); out.writeLong(b.updatedAt().toEpochMilli());
            }
            out.writeInt(state.relationships().size());
            for (var entry : state.relationships().entrySet()) {
                writeUuid(out, entry.getKey()); var r = entry.getValue(); out.writeDouble(r.trust()); out.writeDouble(r.affinity());
                out.writeDouble(r.suspicion()); out.writeDouble(r.fear()); out.writeLong(r.interactions());
            }
            out.writeInt(state.memories().size());
            for (var m : state.memories()) {
                writeUuid(out, m.id()); out.writeLong(m.occurredAt().toEpochMilli()); writeString(out, m.type());
                writeStringMap(out, m.facts()); out.writeDouble(m.importance()); out.writeDouble(m.confidence());
            }
        }
        byte[] data = buffer.toByteArray();
        if (data.length > MAX_FILE_BYTES) throw new IOException("lively state exceeds maximum size");
        return data;
    }

    private NpcState.StateData decode(byte[] payload) throws IOException {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload))) {
            UUID id = readUuid(in); long revision = Math.max(0L, in.readLong()); String name = readString(in); String role = readString(in);
            Map<String, Double> traits = readDoubleMap(in); Map<String, Double> needs = readDoubleMap(in);
            int beliefCount = boundedCount(in.readInt(), MAX_MAP, "belief"); Map<String, NpcSnapshot.BeliefView> beliefs = new HashMap<>();
            for (int i = 0; i < beliefCount; i++) {
                String key = readString(in), value = readString(in); double confidence = in.readDouble(); UUID source = readNullableUuid(in);
                Instant updated = Instant.ofEpochMilli(in.readLong()); beliefs.put(key, new NpcSnapshot.BeliefView(key, value, confidence, source, updated));
            }
            int relationCount = boundedCount(in.readInt(), MAX_MAP, "relationship"); Map<UUID, NpcSnapshot.RelationshipView> relationships = new HashMap<>();
            for (int i = 0; i < relationCount; i++) {
                UUID subject = readUuid(in); relationships.put(subject, new NpcSnapshot.RelationshipView(subject, in.readDouble(), in.readDouble(), in.readDouble(), in.readDouble(), in.readLong()));
            }
            int memoryCount = boundedCount(in.readInt(), MAX_MEMORIES, "memory"); List<NpcSnapshot.MemoryView> memories = new ArrayList<>(memoryCount);
            for (int i = 0; i < memoryCount; i++) {
                UUID memoryId = readUuid(in); Instant time = Instant.ofEpochMilli(in.readLong()); String type = readString(in);
                Map<String, String> facts = readStringMap(in); double importance = in.readDouble(), confidence = in.readDouble();
                memories.add(new NpcSnapshot.MemoryView(memoryId, time, type, facts, importance, confidence));
            }
            if (in.available() != 0) throw new IOException("unexpected trailing lively state data");
            return new NpcState.StateData(id, revision, name, role, traits, needs, beliefs, relationships, memories);
        } catch (RuntimeException ex) {
            throw new IOException("invalid lively state payload", ex);
        }
    }

    private static void writeDoubleMap(DataOutputStream out, Map<String, Double> map) throws IOException {
        if (map.size() > MAX_MAP) throw new IOException("map too large"); out.writeInt(map.size());
        for (var entry : map.entrySet()) { writeString(out, entry.getKey()); out.writeDouble(entry.getValue()); }
    }
    private static Map<String, Double> readDoubleMap(DataInputStream in) throws IOException {
        int count = boundedCount(in.readInt(), MAX_MAP, "double map"); Map<String, Double> map = new HashMap<>();
        for (int i = 0; i < count; i++) map.put(readString(in), in.readDouble()); return map;
    }
    private static void writeStringMap(DataOutputStream out, Map<String, String> map) throws IOException {
        if (map.size() > MAX_MAP) throw new IOException("map too large"); out.writeInt(map.size());
        for (var entry : map.entrySet()) { writeString(out, entry.getKey()); writeString(out, entry.getValue()); }
    }
    private static Map<String, String> readStringMap(DataInputStream in) throws IOException {
        int count = boundedCount(in.readInt(), MAX_MAP, "string map"); Map<String, String> map = new HashMap<>();
        for (int i = 0; i < count; i++) map.put(readString(in), readString(in)); return map;
    }
    private static int boundedCount(int count, int max, String kind) throws IOException { if (count < 0 || count > max) throw new IOException("invalid " + kind + " count"); return count; }
    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8); if (bytes.length > MAX_STRING_BYTES) throw new IOException("string too large");
        out.writeInt(bytes.length); out.write(bytes);
    }
    private static String readString(DataInputStream in) throws IOException {
        int length = in.readInt(); if (length < 0 || length > MAX_STRING_BYTES) throw new IOException("invalid string length");
        byte[] bytes = in.readNBytes(length); if (bytes.length != length) throw new EOFException("truncated string");
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }
    private static void writeUuid(DataOutputStream out, UUID id) throws IOException { out.writeLong(id.getMostSignificantBits()); out.writeLong(id.getLeastSignificantBits()); }
    private static UUID readUuid(DataInputStream in) throws IOException { return new UUID(in.readLong(), in.readLong()); }
    private static void writeNullableUuid(DataOutputStream out, UUID id) throws IOException { out.writeBoolean(id != null); if (id != null) writeUuid(out, id); }
    private static UUID readNullableUuid(DataInputStream in) throws IOException { return in.readBoolean() ? readUuid(in) : null; }
}
