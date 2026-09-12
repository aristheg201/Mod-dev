package vn.svframe.svarcade.persistence;

import java.io.*;
import java.nio.charset.*;
import java.security.*;
import java.util.*;
import vn.svframe.svarcade.config.Values;

/** Explicit bounded typed data format with schema, payload length and SHA-256 integrity. */
public final class StateCodec {
    private static final int MAGIC = 0x53564153;
    private static final int VERSION = 1;
    private static final int MAX_BYTES = 8 * 1024 * 1024;
    private static final int MAX_STRING = 1024 * 1024;
    private StateCodec() { }
    public static byte[] encode(Map<String, Object> state) throws IOException {
        Map<String, Object> frozen = Values.map(state);
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(new OutputStream() {
            private int written;
            private void reserve(int bytes) throws IOException {
                if (bytes > MAX_BYTES - written) throw new IOException("State exceeds byte limit");
                written += bytes;
            }
            @Override public void write(int value) throws IOException { reserve(1); payload.write(value); }
            @Override public void write(byte[] bytes, int offset, int length) throws IOException {
                reserve(length); payload.write(bytes, offset, length);
            }
        })) { write(out, frozen); }
        byte[] bytes = payload.toByteArray();
        if (bytes.length > MAX_BYTES) throw new IOException("State exceeds byte limit");
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(encoded)) {
            out.writeInt(MAGIC); out.writeInt(VERSION); out.writeInt(bytes.length); out.write(digest(bytes)); out.write(bytes);
        }
        return encoded.toByteArray();
    }
    public static Map<String, Object> decode(byte[] encoded) throws IOException {
        if (encoded.length > MAX_BYTES + 44) throw new IOException("State exceeds byte limit");
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(encoded))) {
            if (in.readInt() != MAGIC || in.readInt() != VERSION) throw new IOException("Unsupported state header");
            int length = in.readInt();
            if (length < 0 || length > MAX_BYTES || length != encoded.length - 44) throw new IOException("State length mismatch");
            byte[] checksum = in.readNBytes(32), bytes = in.readNBytes(length);
            if (!MessageDigest.isEqual(checksum, digest(bytes))) throw new IOException("State checksum mismatch");
            try (DataInputStream data = new DataInputStream(new ByteArrayInputStream(bytes))) {
                Object value = read(data, 0, new int[]{0});
                if (data.available() != 0) throw new IOException("Trailing state data");
                return Values.map(value);
            }
        } catch (IllegalArgumentException e) { throw new IOException("Invalid state", e); }
    }
    private static byte[] digest(byte[] bytes) {
        try { return MessageDigest.getInstance("SHA-256").digest(bytes); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private static void string(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_STRING) throw new IOException("State string too large");
        out.writeInt(bytes.length); out.write(bytes);
    }
    private static String string(DataInputStream in) throws IOException {
        int size = in.readInt();
        if (size < 0 || size > MAX_STRING || size > in.available()) throw new IOException("Invalid string length");
        byte[] bytes = in.readNBytes(size);
        try { return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString(); }
        catch (CharacterCodingException e) { throw new IOException("Invalid UTF-8", e); }
    }
    private static void write(DataOutputStream out, Object value) throws IOException {
        if (value instanceof String s) { out.writeByte(1); string(out, s); }
        else if (value instanceof Boolean b) { out.writeByte(2); out.writeBoolean(b); }
        else if (value instanceof Integer i) { out.writeByte(3); out.writeInt(i); }
        else if (value instanceof Long l) { out.writeByte(4); out.writeLong(l); }
        else if (value instanceof Double d) { out.writeByte(5); out.writeDouble(d); }
        else if (value instanceof List<?> list) { out.writeByte(6); out.writeInt(list.size()); for (Object item : list) write(out, item); }
        else if (value instanceof Map<?, ?> map) {
            out.writeByte(7); out.writeInt(map.size());
            for (var entry : map.entrySet()) { string(out, (String) entry.getKey()); write(out, entry.getValue()); }
        } else throw new IOException("Unsupported state value");
    }
    private static Object read(DataInputStream in, int depth, int[] count) throws IOException {
        if (depth > 48 || ++count[0] > 100_000) throw new IOException("State complexity limit");
        return switch (in.readUnsignedByte()) {
            case 1 -> string(in);
            case 2 -> { int b = in.readUnsignedByte(); if (b > 1) throw new IOException("Invalid boolean"); yield b == 1; }
            case 3 -> in.readInt();
            case 4 -> in.readLong();
            case 5 -> { double d = in.readDouble(); if (!Double.isFinite(d)) throw new IOException("Invalid floating value"); yield d; }
            case 6 -> {
                int size = size(in); List<Object> list = new ArrayList<>();
                for (int i = 0; i < size; i++) list.add(read(in, depth + 1, count)); yield List.copyOf(list);
            }
            case 7 -> {
                int size = size(in); Map<String, Object> map = new LinkedHashMap<>();
                for (int i = 0; i < size; i++) { String key = string(in); if (map.putIfAbsent(key, read(in, depth + 1, count)) != null) throw new IOException("Duplicate state key"); }
                yield Map.copyOf(map);
            }
            default -> throw new IOException("Unknown state tag");
        };
    }
    private static int size(DataInputStream in) throws IOException {
        int size = in.readInt(); if (size < 0 || size > 100_000 || size > in.available()) throw new IOException("Invalid collection length"); return size;
    }
}
