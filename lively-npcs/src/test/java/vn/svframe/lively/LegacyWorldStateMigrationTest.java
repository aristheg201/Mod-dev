package vn.svframe.lively;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.svframe.lively.persistence.LegacyWorldStateMigration;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

final class LegacyWorldStateMigrationTest {
    @TempDir Path temp;

    @Test
    void importsLegacyFilesOnceWithoutDeletingOriginals() throws Exception {
        Path legacy = temp.resolve("config-lively");
        Path world = temp.resolve("world-lively");
        Files.createDirectories(legacy.resolve("npcs"));
        Files.createDirectories(legacy.resolve("state"));
        Files.createDirectories(legacy.resolve("history"));
        Files.writeString(legacy.resolve("npcs/npcs.tsv"), "2\n", StandardCharsets.UTF_8);
        Files.writeString(legacy.resolve("state/simulation.json"), "legacy-simulation", StandardCharsets.UTF_8);
        Files.writeString(legacy.resolve("state/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa.lnpc"), "npc-state", StandardCharsets.UTF_8);
        Files.writeString(legacy.resolve("history/world-history.lwh"), "history", StandardCharsets.UTF_8);

        assertTrue(LegacyWorldStateMigration.importIfNeeded(legacy, world));
        assertEquals("2\n", Files.readString(world.resolve("npcs/npcs.tsv"), StandardCharsets.UTF_8));
        assertEquals("legacy-simulation", Files.readString(world.resolve("state/simulation.json"), StandardCharsets.UTF_8));
        assertEquals("npc-state", Files.readString(world.resolve("state/npcs/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa.lnpc"), StandardCharsets.UTF_8));
        assertEquals("history", Files.readString(world.resolve("history/world-history.lwh"), StandardCharsets.UTF_8));
        assertTrue(Files.isRegularFile(world.resolve(".legacy-config-imported")));
        assertTrue(Files.isRegularFile(legacy.resolve("state/simulation.json")), "migration must retain legacy source files");
        assertFalse(LegacyWorldStateMigration.importIfNeeded(legacy, world), "marker must make migration idempotent");
    }

    @Test
    void existingWorldStateAlwaysWins() throws Exception {
        Path legacy = temp.resolve("legacy-existing");
        Path world = temp.resolve("world-existing");
        Files.createDirectories(legacy.resolve("npcs"));
        Files.createDirectories(world.resolve("npcs"));
        Files.writeString(legacy.resolve("npcs/npcs.tsv"), "legacy", StandardCharsets.UTF_8);
        Files.writeString(world.resolve("npcs/npcs.tsv"), "current", StandardCharsets.UTF_8);

        assertFalse(LegacyWorldStateMigration.importIfNeeded(legacy, world));
        assertEquals("current", Files.readString(world.resolve("npcs/npcs.tsv"), StandardCharsets.UTF_8));
        assertFalse(Files.exists(world.resolve(".legacy-config-imported")));
    }

    @Test
    void absentLegacyStateIsANoop() {
        assertFalse(LegacyWorldStateMigration.importIfNeeded(temp.resolve("missing"), temp.resolve("world-empty")));
    }
}
