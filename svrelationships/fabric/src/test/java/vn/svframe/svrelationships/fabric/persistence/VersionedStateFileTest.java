package vn.svframe.svrelationships.fabric.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class VersionedStateFileTest {
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    @TempDir Path directory;

    @Test
    void readsLegacyArrayAndWritesVersionedEnvelope() throws Exception {
        Path file = directory.resolve("state.json");
        Files.writeString(file, gson.toJson(new Sample[]{new Sample("legacy", 1)}));
        var legacy = VersionedStateFile.readArray(file, gson, Sample[].class);
        assertTrue(legacy.legacySchema());
        assertEquals("legacy", legacy.records()[0].id());
        VersionedStateFile.write(file, gson, List.of(new Sample("current", 2)));
        var current = VersionedStateFile.readArray(file, gson, Sample[].class);
        assertFalse(current.legacySchema());
        assertEquals(2, current.records()[0].value());
    }

    @Test
    void recoversFromBackupWhenMainFileIsCorrupt() throws Exception {
        Path file = directory.resolve("state.json");
        VersionedStateFile.write(file, gson, List.of(new Sample("first", 1)));
        VersionedStateFile.write(file, gson, List.of(new Sample("second", 2)));
        Files.writeString(file, "{broken");
        var recovered = VersionedStateFile.readArray(file, gson, Sample[].class);
        assertTrue(recovered.recoveredFromBackup());
        assertEquals("first", recovered.records()[0].id());
    }

    private record Sample(String id, int value) {}
}
