package vn.svframe.lively;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.svframe.lively.combat.CombatCortex;
import vn.svframe.lively.model.NpcState;
import vn.svframe.lively.navigation.NavigationCortex;
import vn.svframe.lively.persistence.NpcStateStore;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class CoreCortexTest {
    @TempDir Path tempDir;

    @Test
    void stateRoundTripsAndRejectsCorruption() throws Exception {
        UUID id = UUID.randomUUID(); UUID player = UUID.randomUUID();
        NpcState state = new NpcState(id, "Marcus", "fisherman", 64);
        state.setTrait("brave", 0.7D);
        state.updateBelief("missing_resource", "minecraft:iron_ingot", 0.9D, player);
        state.remember("player_helped", Map.of("player", player.toString()), 0.9D, 1D);

        NpcStateStore store = new NpcStateStore(tempDir);
        store.save(state.exportData());
        NpcState.StateData loaded = store.load(id).orElseThrow();
        NpcState restored = new NpcState(id, "x", "x", 64); restored.importData(loaded);
        assertEquals("Marcus", restored.snapshot().name());
        assertEquals("minecraft:iron_ingot", restored.snapshot().beliefValue("missing_resource"));

        Path file = tempDir.resolve(id + ".lnpc");
        byte[] bytes = Files.readAllBytes(file); bytes[bytes.length - 1] ^= 0x01; Files.write(file, bytes);
        assertThrows(java.io.IOException.class, () -> store.load(id));
    }

    @Test
    void aStarUsesBoundedGraphAndFindsShortestRoute() {
        NavigationCortex.Graph graph = new NavigationCortex.Graph(7L,
                Map.of(1L, new NavigationCortex.Node(1L, 0, 0, 0, 1, 0),
                        2L, new NavigationCortex.Node(2L, 1, 0, 0, 1, 0),
                        3L, new NavigationCortex.Node(3L, 2, 0, 0, 1, 0)),
                Map.of(1L, List.of(new NavigationCortex.Edge(2L, 1D), new NavigationCortex.Edge(3L, 10D)),
                        2L, List.of(new NavigationCortex.Edge(3L, 1D))));
        NavigationCortex.Path path = new NavigationCortex().findPath(
                graph, 1L, 3L, new NavigationCortex.Budget(128, 10_000_000L)).orElseThrow();
        assertEquals(List.of(1L, 2L, 3L), path.nodes());
        assertEquals(2D, path.cost(), 0.0001D);
    }

    @Test
    void combatFallbackDoesNotPreferFirstActionWhenBudgetExpires() {
        CombatCortex.CombatAction weak = new CombatCortex.CombatAction("weak", 0.2D, 0.1D, Map.of());
        CombatCortex.CombatAction strong = new CombatCortex.CombatAction("strong", 0.9D, 0.2D, Map.of());
        CombatCortex.CombatState state = new CombatCortex.CombatState(1L, 1, 0.8D, 0.2D,
                List.of(weak, strong), Map.of());
        CombatCortex.Decision decision = new CombatCortex().choose(state, (s, a) -> List.of(),
                new CombatCortex.SearchBudget(5, 7, 240, 100_000L)).orElseThrow();
        assertEquals("strong", decision.action().id());
    }
}
