package vn.svframe.lively;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.svframe.lively.npc.NpcDefinition;
import vn.svframe.lively.npc.NpcDefinitionStore;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class NpcDefinitionStoreTest {
    @TempDir Path temp;

    @Test
    void formatThreeRoundTripsMetadataAndRecoversFromBackup() throws Exception {
        Path file = temp.resolve("npcs.tsv");
        UUID id = UUID.randomUUID();
        NpcDefinition first = npc(id, "Mara", Map.of("home.structure", "house_mara", "dialogue.auto", "true"));
        NpcDefinition second = npc(id, "Mara Venn", Map.of("home.structure", "house_mara", "dialogue.auto", "true"));
        NpcDefinitionStore store = new NpcDefinitionStore(file);
        store.saveAll(Map.of(id, first));
        assertEquals(first, store.loadAll().get(id));
        store.saveAll(Map.of(id, second));
        assertTrue(Files.isRegularFile(file.resolveSibling("npcs.tsv.bak")));
        Files.writeString(file, "3\ncorrupted-record\n", StandardCharsets.UTF_8);
        Map<UUID, NpcDefinition> recovered = store.loadAll();
        assertEquals(first, recovered.get(id), "verified backup should recover the previous committed definition");
        assertEquals("3", Files.readAllLines(file, StandardCharsets.UTF_8).getFirst());
    }

    @Test
    void formatOneMigratesWithoutLosingIdentity() throws Exception {
        Path file = temp.resolve("legacy.tsv");
        UUID id = UUID.randomUUID();
        String line = String.join("\t", id.toString(), "Legacy", "merchant", "PLAYER", "", "Notch", "minecraft:overworld",
                "1.0", "64.0", "2.0", "0.0", "0.0", "true", "true", "true", "true", "false", "true");
        Files.writeString(file, "1\n" + line + "\n", StandardCharsets.UTF_8);
        NpcDefinitionStore store = new NpcDefinitionStore(file);
        NpcDefinition loaded = store.loadAll().get(id);
        assertNotNull(loaded);
        assertEquals("Legacy", loaded.name());
        assertTrue(loaded.metadata().isEmpty());
        assertEquals("3", Files.readAllLines(file, StandardCharsets.UTF_8).getFirst(), "legacy state should be rewritten as format 3");
    }

    @Test
    void tamperedChecksumIsRejectedInsteadOfPartiallyLoading() throws Exception {
        Path file = temp.resolve("tamper.tsv");
        UUID id = UUID.randomUUID();
        NpcDefinitionStore store = new NpcDefinitionStore(file);
        store.saveAll(Map.of(id, npc(id, "Original", Map.of())));
        String data = Files.readString(file, StandardCharsets.UTF_8).replace("Original", "Tampered");
        Files.writeString(file, data, StandardCharsets.UTF_8);
        assertThrows(IllegalStateException.class, store::loadAll);
    }

    private static NpcDefinition npc(UUID id, String name, Map<String, String> metadata) {
        return new NpcDefinition(id, name, "merchant", NpcDefinition.BodyType.PLAYER, "", "mojang:Notch",
                "minecraft:overworld", 1D, 64D, 2D, 0F, 0F, true, true, true, true, false, true, metadata);
    }
}
