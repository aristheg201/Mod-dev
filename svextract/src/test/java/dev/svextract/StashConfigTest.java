package dev.svextract;

import dev.svextract.config.StashConfig;
import java.nio.file.*;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class StashConfigTest {
    @TempDir Path dir;
    @Test void existingTwoFieldConfigRetainsCapacityAndReceivesConcurrencyBound()throws Exception{
        Path file=dir.resolve("stash.json");Files.writeString(file,"{\"slots\":54,\"requestIntervalTicks\":4}");
        assertEquals(new StashConfig(54,4,2),StashConfig.load(file));
    }
    @Test void invalidCapacityOrUnboundedConcurrencyIsRejectedBeforeStorageAccess()throws Exception{
        Path file=dir.resolve("stash.json");Files.writeString(file,"{\"slots\":28,\"requestIntervalTicks\":4,\"maxConcurrentTransfers\":2}");assertThrows(IOException.class,()->StashConfig.load(file));
        Files.writeString(file,"{\"slots\":54,\"requestIntervalTicks\":4,\"maxConcurrentTransfers\":200}");assertThrows(IOException.class,()->StashConfig.load(file));
    }
}
