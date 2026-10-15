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

    @Test void optionalPointBlankLootCanBeFilteredWhenPointBlankIsAbsent() {
        var filtered=LootConfig.defaults().filterAvailable(
                id->id.startsWith("minecraft:"),id->id.startsWith("pointblank:"));
        assertEquals(20,filtered.caches().size());
        assertTrue(filtered.caches().stream()
                .allMatch(c->c.choices().stream().allMatch(choice->choice.item().startsWith("minecraft:"))));
    }

    @Test void missingUnrelatedLootIsStillRejected() {
        var invalid=new LootConfig(List.of(new LootConfig.Cache("bad",0,65,0,"missing:unknown",1)));
        assertThrows(IllegalArgumentException.class,
                ()->invalid.filterAvailable(id->id.startsWith("minecraft:"),
                        id->id.startsWith("pointblank:")));
    }

    @Test void optionalOnlyLootCacheKeepsItsLocationUsingSafeFallback() {
        var modOnly=new LootConfig(List.of(new LootConfig.Cache("gun_cache",5,65,7,"pointblank:glock17",1)));
        var filtered=modOnly.filterAvailable(id->id.startsWith("minecraft:"),
                id->id.startsWith("pointblank:"));
        assertEquals("minecraft:iron_ingot",filtered.caches().getFirst().item());
        assertEquals(5,filtered.caches().getFirst().x());
        assertEquals(7,filtered.caches().getFirst().z());
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
