package vn.svframe.svarcade;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import vn.svframe.svarcade.config.*;
import static org.junit.jupiter.api.Assertions.*;

class DependencyGraphTest {
    @TempDir Path directory;
    private static final Id A = Id.of("test:a"), B = Id.of("test:b"), C = Id.of("test:c");
    @Test void dependenciesPrecedeConsumersRegardlessOfAuthorOrder() {
        Map<Id, Set<Id>> graph = Map.of(A, Set.of(B), B, Set.of(C), C, Set.of());
        assertEquals(List.of(C, B, A), DependencyGraph.order(List.of(A, B, C), id -> id, graph::get));
    }
    @Test void unrelatedReadyNodesKeepDeterministicAuthorOrder() {
        assertEquals(List.of(C, A, B), DependencyGraph.order(List.of(C, A, B), id -> id, id -> Set.of()));
    }
    @Test void diamondSharesDependencyExactlyOnce() {
        Id d = Id.of("test:d");
        Map<Id, Set<Id>> graph = Map.of(A, Set.of(B, C), B, Set.of(d), C, Set.of(d), d, Set.of());
        assertEquals(List.of(d, B, C, A), DependencyGraph.order(List.of(A, B, C, d), id -> id, graph::get));
    }
    @Test void missingDependencyIsDiagnosticAndSelfOrMutualCycleIsRejected() {
        var missing = assertThrows(ConfigException.class, () -> DependencyGraph.order(List.of(A), id -> id, id -> Set.of(B)));
        assertTrue(missing.getMessage().contains("test:a")); assertTrue(missing.getMessage().contains("test:b"));
        assertThrows(ConfigException.class, () -> DependencyGraph.order(List.of(A), id -> id, id -> Set.of(A)));
        assertThrows(ConfigException.class, () -> DependencyGraph.order(List.of(A, B), id -> id, id -> id.equals(A) ? Set.of(B) : Set.of(A)));
    }
    @Test void duplicateNodesRejectedAndLongChainUsesNoRecursion() {
        assertThrows(ConfigException.class, () -> DependencyGraph.order(List.of(A, A), id -> id, id -> Set.of()));
        List<Id> ids = new ArrayList<>(); Map<Id, Set<Id>> edges = new HashMap<>();
        for (int i = 0; i < 4000; i++) { Id id = Id.of("test:n" + i); ids.add(id); edges.put(id, i == 0 ? Set.of() : Set.of(ids.get(i - 1))); }
        List<Id> reverse = new ArrayList<>(ids); Collections.reverse(reverse);
        assertEquals(ids, DependencyGraph.order(reverse, id -> id, edges::get));
    }
    SystemSchema schema(Set<Id> deps) {
        return new SystemSchema() {
            public void validate(Node n) { n.only(); }
            public Set<Id> dependencies() { return deps; }
        };
    }
    Path fixture() throws Exception {
        Files.createDirectories(directory.resolve("arenas"));
        Files.writeString(directory.resolve("game.yml"), """
                schema: 1
                id: test:composition
                players: {min: 1, max: 2}
                systems:
                  - {id: 'test:a', config: empty.yml}
                  - {id: 'test:b', config: empty.yml}
                  - {id: 'test:c', config: empty.yml}
                arenas: {directory: arenas}
                """);
        Files.writeString(directory.resolve("empty.yml"), "{}\n"); return directory;
    }
    @Test void loaderStoresCompiledSystemOrder() throws Exception {
        var loader = new DefinitionLoader(new Registry<>(Map.of(A, schema(Set.of(B)), B, schema(Set.of(C)), C, schema(Set.of()))));
        assertEquals(List.of(C, B, A), loader.load(fixture()).systems().stream().map(Definition.SystemSpec::id).toList());
    }
    @Test void cyclicReloadRetainsPublishedSnapshot() throws Exception {
        Path path = fixture(); DefinitionRegistry registry = new DefinitionRegistry();
        var valid = new DefinitionLoader(new Registry<>(Map.of(A, schema(Set.of(B)), B, schema(Set.of(C)), C, schema(Set.of()))));
        registry.reload(() -> List.of(valid.load(path)), Set.of(), Runnable::run).join(); var old = registry.snapshot();
        var cyclic = new DefinitionLoader(new Registry<>(Map.of(A, schema(Set.of(B)), B, schema(Set.of(C)), C, schema(Set.of(A)))));
        assertFalse(registry.reload(() -> List.of(cyclic.load(path)), Set.of(), Runnable::run).join().applied()); assertSame(old, registry.snapshot());
    }
}
