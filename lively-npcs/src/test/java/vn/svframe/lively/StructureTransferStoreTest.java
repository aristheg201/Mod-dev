package vn.svframe.lively;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.svframe.lively.persistence.StructureTransferStore;
import vn.svframe.lively.world.SemanticStructureRegistry;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class StructureTransferStoreTest {
    @TempDir Path temp;

    @Test
    void exportIsImmediatelyVisibleFromCacheAndDurableAfterOrderedRefresh() {
        StructureTransferStore store = new StructureTransferStore(temp.resolve("structures"));
        store.preloadFuture().join();

        SemanticStructureRegistry.Structure structure = new SemanticStructureRegistry.Structure(
                "market_square", "market",
                new SemanticStructureRegistry.Bounds("minecraft:overworld", 0, 60, 0, 8, 70, 8),
                Set.of("trade", "gather"), Map.of("entrance", "4.5,61,0.5"),
                null, "spawn_town", SemanticStructureRegistry.OperationalState.OPEN, 0L);

        Path target = store.exportStructure(structure);
        assertEquals(structure, store.importStructure("market_square").orElseThrow());

        // refreshAsync is queued on the same single I/O worker, therefore reaching its completion also proves the
        // preceding export write finished before the directory was reloaded.
        store.refreshAsync().join();
        assertTrue(Files.isRegularFile(target));
        assertEquals(structure, store.importStructure("market_square").orElseThrow());
    }
}
