package vn.svframe.lively.world;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BuiltStructureDiscoveryTest {
    @Test
    void classifiesHouseFromBed() {
        assertEquals("house", BuiltStructureDiscovery.classify(
                Map.of("minecraft:red_bed", 1), Map.of("sleep", 1)));
    }

    @Test
    void classifiesBlacksmithFromFunctionalBlocks() {
        assertEquals("blacksmith", BuiltStructureDiscovery.classify(
                Map.of("minecraft:blast_furnace", 1, "minecraft:grindstone", 1),
                Map.of("smelt", 1, "smith", 1, "repair", 1)));
    }

    @Test
    void higherPrioritySpecializedRoomsBeatGenericResidence() {
        assertEquals("inn", BuiltStructureDiscovery.classify(
                Map.of("minecraft:white_bed", 4, "minecraft:jukebox", 1, "minecraft:smoker", 1),
                Map.of("sleep", 4, "cook", 1)));
        assertEquals("library", BuiltStructureDiscovery.classify(
                Map.of("minecraft:bookshelf", 20, "minecraft:lectern", 1), Map.of("read", 1, "teach", 1)));
    }

    @Test
    void fallsBackToGenericBuildingWithoutFunctionalPoi() {
        assertEquals("building", BuiltStructureDiscovery.classify(Map.of("minecraft:stone_bricks", 64), Map.of()));
    }

    @Test
    void customRuleCanMatchModdedBlockTagsAndCapabilities() {
        BuiltStructureTypeRegistry.Rule rule = new BuiltStructureTypeRegistry.Rule(
                "poke_center", 500,
                Map.of("cobblemon:healing_machine", 1),
                Map.of("c:storage_blocks", 2),
                Map.of("storage", 1),
                Map.of("_bed", 1),
                Set.of("heal", "pokemon_service"));
        assertTrue(rule.matches(
                Map.of("cobblemon:healing_machine", 1, "minecraft:white_bed", 1),
                Map.of("c:storage_blocks", 2),
                Map.of("storage", 3)));
    }
}
