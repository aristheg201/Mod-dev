package vn.svframe.svarcade;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.runtime.*;
import static org.junit.jupiter.api.Assertions.*;

class DefinitionTest {
    @TempDir Path root;
    static final Id SYSTEM = Id.of("test:values");
    Registry<SystemSchema> schemas() { return new Registry<>(Map.of(SYSTEM, n -> { n.only("amount"); n.integer("amount", 0, 100); })); }
    Path fixture(String name) throws Exception {
        Path dir = Files.createDirectory(root.resolve(name)); Files.createDirectory(dir.resolve("arenas"));
        Files.writeString(dir.resolve("game.yml"), """
                schema: 1
                id: test:%s
                players: {min: 1, max: 2}
                systems:
                  - id: test:values
                    config: values.yml
                arenas: {directory: arenas}
                """.formatted(name));
        Files.writeString(dir.resolve("values.yml"), "amount: 1\n");
        Files.writeString(dir.resolve("arenas/one.yml"), "id: one\nworld: minecraft:overworld\n");
        return dir;
    }
    @Test void loadsWithoutGameClassAndRemovingFolderRemovesDefinition() throws Exception {
        Path dir = fixture("sample"); DefinitionLoader loader = new DefinitionLoader(schemas());
        assertEquals(Id.of("test:sample"), loader.loadAll(root).getFirst().id());
        try (var paths = Files.walk(dir)) { for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(p); }
        assertTrue(loader.loadAll(root).isEmpty());
    }
    @Test void fingerprintChangesWithReferencedContentAndIsStableAcrossLoads() throws Exception {
        Path dir = fixture("sample"); DefinitionLoader loader = new DefinitionLoader(schemas());
        String first = loader.load(dir).fingerprint(); assertEquals(first, loader.load(dir).fingerprint());
        Files.writeString(dir.resolve("values.yml"), "amount: 2\n"); assertNotEquals(first, loader.load(dir).fingerprint());
    }
    @Test void duplicateYamlKeysAreRejected() throws Exception {
        Path dir = fixture("sample"); Files.writeString(dir.resolve("values.yml"), "amount: 1\namount: 2\n");
        assertThrows(ConfigException.class, () -> new DefinitionLoader(schemas()).load(dir));
    }
    @Test void arbitraryJavaTagsAreRejected() throws Exception {
        Path dir = fixture("sample"); Files.writeString(dir.resolve("values.yml"), "!!java.net.URL [https://example.invalid]\n");
        assertThrows(ConfigException.class, () -> new DefinitionLoader(schemas()).load(dir));
    }
    @Test void collectionAliasesAreRejected() throws Exception {
        Path dir = fixture("sample"); Files.writeString(dir.resolve("values.yml"), "amount: &a [1]\nother: *a\n");
        assertThrows(ConfigException.class, () -> new DefinitionLoader(schemas()).load(dir));
    }
    @Test void nonfiniteAndWrongTypesAreRejected() throws Exception {
        Path dir = fixture("sample"); Files.writeString(dir.resolve("values.yml"), "amount: '10'\n");
        assertThrows(ConfigException.class, () -> new DefinitionLoader(schemas()).load(dir));
        assertThrows(ConfigException.class, () -> Values.freeze(Double.NaN));
        assertThrows(ConfigException.class, () -> Values.freeze(Double.POSITIVE_INFINITY));
    }
    @Test void pathTraversalIsRejected() throws Exception {
        Path dir = fixture("sample"); String game = Files.readString(dir.resolve("game.yml"));
        Files.writeString(dir.resolve("game.yml"), game.replace("values.yml", "../outside.yml"));
        Files.writeString(root.resolve("outside.yml"), "amount: 9\n");
        assertThrows(ConfigException.class, () -> new DefinitionLoader(schemas()).load(dir));
    }
    @Test void symlinkEscapeIsRejected() throws Exception {
        Path dir = fixture("sample"); Files.delete(dir.resolve("values.yml"));
        Path outside = Files.writeString(root.resolve("outside.yml"), "amount: 9\n");
        Files.createSymbolicLink(dir.resolve("values.yml"), outside);
        assertThrows(ConfigException.class, () -> new DefinitionLoader(schemas()).load(dir));
    }
    @Test void oversizedFileIsRejected() throws Exception {
        Path dir = fixture("sample"); Files.writeString(dir.resolve("values.yml"), "#".repeat(1_048_577));
        assertThrows(ConfigException.class, () -> new DefinitionLoader(schemas()).load(dir));
    }
    @Test void unknownFieldsAndSystemsFailClosed() throws Exception {
        Path dir = fixture("sample"); Files.writeString(dir.resolve("values.yml"), "amount: 1\nammount: 2\n");
        assertThrows(ConfigException.class, () -> new DefinitionLoader(schemas()).load(dir));
        Files.writeString(dir.resolve("values.yml"), "amount: 1\n");
        assertThrows(ConfigException.class, () -> new DefinitionLoader(new Registry<>(Map.of())).load(dir));
    }
    @Test void missingSystemDependencyIsRejected() throws Exception {
        Path dir = fixture("sample");
        SystemSchema schema = new SystemSchema() { public void validate(Node n) { } public Set<Id> dependencies() { return Set.of(Id.of("test:missing")); } };
        assertThrows(ConfigException.class, () -> new DefinitionLoader(new Registry<>(Map.of(SYSTEM, schema))).load(dir));
    }
    @Test void failedCandidatePreservesRegistryAndPinnedSessionData() throws Exception {
        Path dir = fixture("sample"); DefinitionLoader loader = new DefinitionLoader(schemas()); DefinitionRegistry registry = new DefinitionRegistry();
        assertTrue(registry.reload(() -> loader.loadAll(root), Set.of(), Runnable::run).join().applied());
        Definition pinned = registry.snapshot().requireAvailable(Id.of("test:sample"));
        Files.writeString(dir.resolve("values.yml"), "amount: 999\n");
        assertFalse(registry.reload(() -> loader.loadAll(root), Set.of(), Runnable::run).join().applied());
        assertSame(pinned, registry.snapshot().requireAvailable(pinned.id()));
        Files.writeString(dir.resolve("values.yml"), "amount: 2\n");
        assertTrue(registry.reload(() -> loader.loadAll(root), Set.of(), Runnable::run).join().applied());
        assertEquals(1L, pinned.systems().getFirst().config().integer("amount", 0, 100));
        assertEquals(2L, registry.snapshot().requireAvailable(pinned.id()).systems().getFirst().config().integer("amount", 0, 100));
    }
    @Test void outOfOrderReloadCannotOverwriteNewerPublication() throws Exception {
        fixture("sample"); DefinitionLoader loader = new DefinitionLoader(schemas()); DefinitionRegistry registry = new DefinitionRegistry();
        List<Runnable> pending = new ArrayList<>();
        var older = registry.reload(() -> loader.loadAll(root), Set.of(), pending::add);
        var newer = registry.reload(List::of, Set.of(), pending::add);
        pending.get(1).run(); pending.get(0).run();
        assertTrue(newer.join().applied()); assertFalse(older.join().applied()); assertTrue(registry.snapshot().definitions().isEmpty());
    }
    @Test void missingIntegrationDisablesOnlyItsDefinition() throws Exception {
        Path dir = fixture("requires_mod"); fixture("generic");
        Files.writeString(dir.resolve("game.yml"), "requires: {integrations: [cobblemon]}\n", StandardOpenOption.APPEND);
        DefinitionRegistry registry = new DefinitionRegistry(); DefinitionLoader loader = new DefinitionLoader(schemas());
        assertTrue(registry.reload(() -> loader.loadAll(root), Set.of(), Runnable::run).join().applied());
        assertThrows(ConfigException.class, () -> registry.snapshot().requireAvailable(Id.of("test:requires_mod")));
        assertNotNull(registry.snapshot().requireAvailable(Id.of("test:generic")));
        assertTrue(registry.reload(() -> loader.loadAll(root), Set.of("cobblemon"), Runnable::run).join().applied());
        assertNotNull(registry.snapshot().requireAvailable(Id.of("test:requires_mod")));
    }
    @Test void saturationRejectsReloadWithoutRunningOnCaller() {
        DefinitionRegistry registry = new DefinitionRegistry();
        assertFalse(registry.reload(() -> { fail("Must not run"); return List.of(); }, Set.of(), r -> { throw new RejectedExecutionException(); }).join().applied());
    }
    @Test void frozenValuesAreDeepCopiesAndRejectCycles() {
        List<Object> child = new ArrayList<>(List.of(1)); Map<String, Object> source = new HashMap<>(Map.of("a", child));
        Map<String, Object> frozen = Values.map(source); child.add(2); source.put("b", 3);
        assertEquals(Map.of("a", List.of(1)), frozen); assertThrows(UnsupportedOperationException.class, () -> frozen.put("b", 4));
        child.add(child); assertThrows(ConfigException.class, () -> Values.freeze(child));
    }
    @Test void builderCannotShadowRegisteredCapabilities() {
        Registry.Builder<String> builder = new Registry.Builder<>(); builder.add(SYSTEM, "first");
        assertThrows(ConfigException.class, () -> builder.add(SYSTEM, "second"));
    }
    @Test void parserRejectsDeepYamlBeforeRecursiveConstructionAndMalformedUtf8() throws Exception {
        Path dir = fixture("sample");
        Files.writeString(dir.resolve("values.yml"), "amount: " + "[".repeat(10_000) + "1" + "]".repeat(10_000));
        assertThrows(ConfigException.class, () -> new DefinitionLoader(schemas()).load(dir));
        Files.write(dir.resolve("values.yml"), new byte[]{(byte) 0xc3, 0x28});
        assertThrows(ConfigException.class, () -> new DefinitionLoader(schemas()).load(dir));
    }
}
