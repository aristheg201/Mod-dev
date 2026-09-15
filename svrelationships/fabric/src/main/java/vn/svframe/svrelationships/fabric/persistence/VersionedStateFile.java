package vn.svframe.svrelationships.fabric.persistence;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;

public final class VersionedStateFile {
    public static final int CURRENT_SCHEMA = 2;

    private VersionedStateFile() {}

    public static <T> LoadResult<T[]> readArray(Path file, Gson gson, Class<T[]> arrayType) {
        Path backup = backup(file);
        if (!Files.exists(file)) {
            if (!Files.exists(backup)) return new LoadResult<>(null, false, false);
            return readFrom(backup, gson, arrayType, true);
        }
        try {
            return readFrom(file, gson, arrayType, false);
        } catch (RuntimeException primary) {
            if (!Files.exists(backup)) throw primary;
            try {
                return readFrom(backup, gson, arrayType, true);
            } catch (RuntimeException recovery) {
                primary.addSuppressed(recovery);
                throw primary;
            }
        }
    }

    private static <T> LoadResult<T[]> readFrom(Path source, Gson gson, Class<T[]> arrayType, boolean recovered) {
        try {
            JsonElement root = gson.fromJson(Files.readString(source, StandardCharsets.UTF_8), JsonElement.class);
            if (root == null) return new LoadResult<>(null, false, recovered);
            if (root.isJsonArray()) return new LoadResult<>(gson.fromJson(root, arrayType), true, recovered);
            if (!root.isJsonObject()) throw new IllegalStateException("State root must be an object or legacy array: " + source);
            JsonObject object = root.getAsJsonObject();
            int schema = object.has("schema") ? object.get("schema").getAsInt() : 1;
            if (schema < 1 || schema > CURRENT_SCHEMA) throw new IllegalStateException("Unsupported state schema " + schema + " in " + source);
            JsonElement records = object.get("records");
            if (records == null || !records.isJsonArray()) throw new IllegalStateException("Missing state records in " + source);
            return new LoadResult<>(gson.fromJson(records, arrayType), schema < CURRENT_SCHEMA, recovered);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read state file " + source, exception);
        }
    }

    public static void write(Path file, Gson gson, Collection<?> records) throws IOException {
        Files.createDirectories(file.getParent());
        JsonObject envelope = new JsonObject();
        envelope.addProperty("schema", CURRENT_SCHEMA);
        envelope.addProperty("written_at_millis", System.currentTimeMillis());
        envelope.add("records", gson.toJsonTree(records));
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temp, gson.toJson(envelope), StandardCharsets.UTF_8);
        if (Files.exists(file) && isReadable(file, gson)) {
            Files.copy(file, backup(file), StandardCopyOption.REPLACE_EXISTING);
        }
        try {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static boolean isReadable(Path file, Gson gson) {
        try {
            JsonElement root = gson.fromJson(Files.readString(file, StandardCharsets.UTF_8), JsonElement.class);
            return root != null && (root.isJsonArray() || root.isJsonObject());
        } catch (Exception ignored) {
            return false;
        }
    }

    private static Path backup(Path file) {
        return file.resolveSibling(file.getFileName() + ".bak");
    }

    public record LoadResult<T>(T records, boolean legacySchema, boolean recoveredFromBackup) {}
}
