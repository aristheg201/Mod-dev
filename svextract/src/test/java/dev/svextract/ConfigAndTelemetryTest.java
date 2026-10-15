package dev.svextract;

import dev.svextract.config.ServerConfig;
import dev.svextract.performance.Telemetry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class ConfigAndTelemetryTest {
    @TempDir Path dir;
    @Test void defaultsRoundTripAndInvalidConfigIsActionable() throws Exception{
        Path file=dir.resolve("server.json");assertEquals(ServerConfig.defaults(),ServerConfig.load(file));
        Files.writeString(file,Files.readString(file).replace("\"blocksPerTick\": 128","\"blocksPerTick\": 100000"));
        var error=assertThrows(java.io.IOException.class,()->ServerConfig.load(file));assertTrue(error.getMessage().contains("performance.blocksPerTick"));
    }
    @Test void sampleRingIsBoundedAndPercentilesReflectOnlyRetainedSamples(){
        Telemetry t=new Telemetry(4);t.tick(200_000_000);for(int i=1;i<=4;i++)t.tick(i*1_000_000L);
        var s=t.snapshot();assertEquals(4,s.samples());assertEquals(2,s.p50Ms());assertEquals(4,s.p95Ms());assertEquals(1,s.stalls());
    }
    @Test void olderConfigurationGetsBoundedCommandThrottleWithoutLosingSettings() throws Exception{
        Path file=dir.resolve("server.json");ServerConfig.load(file);
        var tree=com.google.gson.JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        tree.getAsJsonObject("performance").remove("commandCooldownTicks");
        Files.writeString(file,tree.toString());
        assertEquals(40,ServerConfig.load(file).performance().commandCooldownTicks());
        tree.getAsJsonObject("performance").addProperty("commandCooldownTicks",0);
        Files.writeString(file,tree.toString());
        assertTrue(assertThrows(java.io.IOException.class,()->ServerConfig.load(file)).getMessage().contains("commandCooldownTicks"));
    }
}
