package dev.svextract;

import dev.svextract.config.LootConfig;
import dev.svextract.config.WeightedLoot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class WeightedLootTest {
    @TempDir Path directory;

    @Test void defaultsProvideTwentyDistinctSearchableCaches() {
        var caches=LootConfig.defaults().caches();
        assertEquals(20,caches.size());
        Set<String> ids=new HashSet<>(),positions=new HashSet<>();
        for(var cache:caches){
            assertTrue(ids.add(cache.id()));
            assertTrue(positions.add(cache.x()+":"+cache.y()+":"+cache.z()));
            assertTrue(cache.choices().size()>=2);
        }
    }

    @Test void perRaidRollIsStableAndOnlyChoosesValidatedEntries() {
        var cfg=LootConfig.defaults();
        for(int i=0;i<100;i++){
            UUID raid=UUID.nameUUIDFromBytes(("operation:"+i).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            for(var cache:cfg.caches()){
                var first=cfg.roll(cache,raid);
                assertEquals(first,cfg.roll(cache,raid));
                assertTrue(cache.choices().contains(first));
            }
        }
    }

    @Test void legacyLootJsonStillLoadsAndInvalidWeightsAreRejected() throws Exception {
        Path config=directory.resolve("loot.json");
        Files.writeString(config,"{\"caches\":[{\"id\":\"legacy\",\"x\":0,\"y\":65,\"z\":0,\"item\":\"minecraft:iron_ingot\",\"count\":2}]}");
        var loaded=LootConfig.load(config);
        assertEquals("minecraft:iron_ingot",loaded.roll(loaded.caches().getFirst(),UUID.randomUUID()).item());
        assertThrows(IllegalArgumentException.class,()->new WeightedLoot.Entry("minecraft:iron_ingot",1,0));
    }
}
