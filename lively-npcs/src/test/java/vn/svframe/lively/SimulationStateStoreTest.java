package vn.svframe.lively;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.svframe.lively.persistence.SimulationStateStore;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

final class SimulationStateStoreTest {
    @TempDir Path temp;

    @Test
    void primaryRoundTripAndCorruptPrimaryFallsBackToBackup() throws Exception {
        Path file = temp.resolve("simulation.json");
        SimulationStateStore.Bundle first = emptyBundle();
        SimulationStateStore.Bundle second = emptyBundle();
        try (SimulationStateStore store = new SimulationStateStore(file)) {
            store.saveAsync(first).get(5, TimeUnit.SECONDS);
            assertTrue(store.load().isPresent());
            store.saveAsync(second).get(5, TimeUnit.SECONDS);
        }
        Path backup = file.resolveSibling("simulation.json.bak");
        assertTrue(Files.isRegularFile(backup), "second save should preserve a readable backup");
        Files.writeString(file, "{ definitely-not-json", StandardCharsets.UTF_8);
        try (SimulationStateStore store = new SimulationStateStore(file)) {
            Optional<SimulationStateStore.Bundle> recovered = store.load();
            assertTrue(recovered.isPresent(), "corrupt primary must recover from the previous verified backup");
        }
    }

    @Test
    void malformedAndUnsupportedStateIsRejectedWithoutThrowing() throws Exception {
        Path file = temp.resolve("simulation.json");
        Files.writeString(file, "{\"schema\":999,\"checksum\":\"bad\",\"payload\":\"{}\"}", StandardCharsets.UTF_8);
        try (SimulationStateStore store = new SimulationStateStore(file)) {
            assertTrue(store.load().isEmpty());
        }
        Files.writeString(file, "[]", StandardCharsets.UTF_8);
        try (SimulationStateStore store = new SimulationStateStore(file)) {
            assertDoesNotThrow(store::load);
            assertTrue(store.load().isEmpty());
        }
    }

    @Test
    void queuedAsyncSavesRemainSerializedAndReadable() throws Exception {
        Path file = temp.resolve("simulation.json");
        try (SimulationStateStore store = new SimulationStateStore(file)) {
            CompletableFuture<?>[] saves = new CompletableFuture<?>[64];
            for (int i = 0; i < saves.length; i++) saves[i] = store.saveAsync(emptyBundle());
            CompletableFuture.allOf(saves).get(15, TimeUnit.SECONDS);
            assertTrue(store.load().isPresent());
            assertTrue(Files.size(file) > 0L);
        }
    }

    private static SimulationStateStore.Bundle emptyBundle() {
        return new SimulationStateStore.Bundle(null, null, null, null, null, null, null, null, null, null, null, null);
    }
}
