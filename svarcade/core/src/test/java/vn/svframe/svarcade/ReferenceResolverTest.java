package vn.svframe.svarcade;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import vn.svframe.svarcade.config.*;
import static org.junit.jupiter.api.Assertions.*;

class ReferenceResolverTest {
    @TempDir Path root;
    @Test void resolvesNestedFilesListIndicesAndEscapedPointerKeys() {
        Map<String, Object> files = Map.of("main.yml", Map.of("value", Map.of("$ref", "shared.yml#/a~1b/0/~0key")),
                "shared.yml", Map.of("a/b", List.of(Map.of("~key", Map.of("cost", 12)))));
        assertEquals(Map.of("value", Map.of("cost", 12)), new ReferenceResolver(files::get).resolve("main.yml"));
    }
    @Test void sameFileReferencesResolveAndNamespacedStringsStayOpaque() {
        Map<String, Object> file = Map.of("source", 4, "copy", Map.of("$ref", "#/source"), "species", "cobblemon:example");
        assertEquals(Map.of("source", 4, "copy", 4, "species", "cobblemon:example"), new ReferenceResolver(name -> file).resolve("one.yml"));
    }
    @Test void cyclesMissingPointersAndMixedRefFieldsAreRejected() {
        Map<String, Object> files = Map.of("a", Map.of("$ref", "b"), "b", Map.of("$ref", "a"));
        assertThrows(ConfigException.class, () -> new ReferenceResolver(files::get).resolve("a"));
        assertThrows(ConfigException.class, () -> new ReferenceResolver(f -> Map.of("x", Map.of("$ref", "#/missing"))).resolve("a"));
        assertThrows(ConfigException.class, () -> new ReferenceResolver(f -> Map.of("$ref", "b", "override", 1)).resolve("a"));
    }
    @Test void invalidPointerSyntaxAndReferenceExpansionBombAreBounded() {
        for (String pointer : List.of("#bad", "#/bad~2", "#/bad~")) {
            assertThrows(ConfigException.class, () -> new ReferenceResolver(f -> Map.of("copy", Map.of("$ref", pointer))).resolve("a"));
        }
        Map<String, Object> files = new HashMap<>(); files.put("leaf", Map.of("value", 1));
        String previous = "leaf";
        for (int i = 0; i < 30; i++) { String name = "n" + i; files.put(name, Map.of("a", Map.of("$ref", previous), "b", Map.of("$ref", previous))); previous = name; }
        String top = previous; assertThrows(ConfigException.class, () -> new ReferenceResolver(files::get).resolve(top));
    }
    Path fixture() throws Exception {
        Path dir = Files.createDirectories(root.resolve("game")); Files.createDirectory(dir.resolve("arenas"));
        Files.writeString(dir.resolve("game.yml"), """
                schema: 1
                id: test:reference
                players: {min: 1, max: 2}
                systems: [{id: 'test:value', config: value.yml}]
                arenas: {directory: arenas}
                """);
        Files.writeString(dir.resolve("value.yml"), "cost: {$ref: 'shared.yml#/profiles/normal/cost'}\n");
        Files.writeString(dir.resolve("shared.yml"), "profiles: {normal: {cost: 10}}\n"); return dir;
    }
    DefinitionLoader loader() { return new DefinitionLoader(new Registry<>(Map.of(Id.of("test:value"), n -> { n.only("cost"); n.integer("cost", 1, 100); }))); }
    @Test void loaderValidatesResolvedValuesAndFingerprintsTransitiveFiles() throws Exception {
        Path dir = fixture(); Definition first = loader().load(dir);
        assertEquals(10L, first.systems().getFirst().config().integer("cost", 1, 100));
        Files.writeString(dir.resolve("shared.yml"), "profiles: {normal: {cost: 20}}\n");
        Definition second = loader().load(dir); assertNotEquals(first.fingerprint(), second.fingerprint());
        assertEquals(10L, first.systems().getFirst().config().integer("cost", 1, 100));
        Files.writeString(dir.resolve("shared.yml"), "profiles: {normal: {cost: wrong}}\n");
        assertThrows(ConfigException.class, () -> loader().load(dir));
    }
    @Test void referenceCannotEscapePackageOrFollowSymlink() throws Exception {
        Path dir = fixture(); Path outside = Files.writeString(root.resolve("outside.yml"), "cost: 1\n");
        Files.writeString(dir.resolve("value.yml"), "$ref: '../outside.yml'\n");
        assertThrows(ConfigException.class, () -> loader().load(dir));
        Files.writeString(dir.resolve("value.yml"), "$ref: 'link.yml'\n"); Files.createSymbolicLink(dir.resolve("link.yml"), outside);
        assertThrows(ConfigException.class, () -> loader().load(dir));
    }
}
