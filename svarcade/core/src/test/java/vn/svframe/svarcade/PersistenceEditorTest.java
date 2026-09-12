package vn.svframe.svarcade;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.runtime.*;
import vn.svframe.svarcade.persistence.*;
import vn.svframe.svarcade.editor.*;
import static org.junit.jupiter.api.Assertions.*;

class PersistenceEditorTest {
    @TempDir Path directory;
    @Test void codecRoundTripsExplicitTypesIncludingUnicode() throws Exception {
        Map<String, Object> state = Map.of("text", "Tiên Kiếm", "integer", 3, "long", 4L, "double", 1.25,
                "bool", true, "list", List.of("x", Map.of("uuid", UUID.randomUUID().toString())));
        assertEquals(state, StateCodec.decode(StateCodec.encode(state)));
    }
    @Test void codecRejectsCorruptionTruncationTrailingDataAndWrongSchema() throws Exception {
        byte[] encoded = StateCodec.encode(Map.of("state", "running"));
        byte[] corrupt = encoded.clone(); corrupt[corrupt.length - 1] ^= 1;
        assertThrows(IOException.class, () -> StateCodec.decode(corrupt));
        assertThrows(IOException.class, () -> StateCodec.decode(Arrays.copyOf(encoded, encoded.length - 1)));
        assertThrows(IOException.class, () -> StateCodec.decode(Arrays.copyOf(encoded, encoded.length + 1)));
        byte[] wrong = encoded.clone(); wrong[7] = 2; assertThrows(IOException.class, () -> StateCodec.decode(wrong));
    }
    @Test void codecRejectsUnsupportedGraphsAndExcessiveDepth() {
        assertThrows(ConfigException.class, () -> StateCodec.encode(Map.of("object", new Object())));
        Object nested = "leaf"; for (int i = 0; i < 60; i++) nested = List.of(nested);
        Object value = nested; assertThrows(ConfigException.class, () -> StateCodec.encode(Map.of("deep", value)));
    }
    @Test void encoderEnforcesTotalSizeWhileWriting() {
        String oneMegabyte = "x".repeat(1_048_576);
        assertThrows(IOException.class, () -> StateCodec.encode(Map.of("large", Collections.nCopies(9, oneMegabyte))));
    }
    @Test void storeIsDurableAcrossReopenAndWritesAreOrdered() throws Exception {
        try (AtomicStore store = new AtomicStore(directory, 32)) {
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (int i = 0; i < 20; i++) futures.add(store.write("session", Map.of("revision", i)));
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).get(10, TimeUnit.SECONDS);
            assertEquals(19, store.read("session").get(10, TimeUnit.SECONDS).orElseThrow().get("revision"));
        }
        try (AtomicStore store = new AtomicStore(directory, 2)) {
            assertEquals(Map.of("revision", 19), store.read("session").get(10, TimeUnit.SECONDS).orElseThrow());
            assertTrue(store.read("absent").get(10, TimeUnit.SECONDS).isEmpty());
        }
        try (var paths = Files.list(directory)) { assertEquals(List.of("session.state"), paths.map(p -> p.getFileName().toString()).toList()); }
    }
    @Test void storeRejectsPathTraversalSymlinksAndWritesAfterShutdown() throws Exception {
        AtomicStore store = new AtomicStore(directory, 2);
        assertThrows(IllegalArgumentException.class, () -> store.write("../escape", Map.of()));
        Path elsewhere = Files.writeString(directory.resolve("elsewhere"), "outside"); Files.createSymbolicLink(directory.resolve("link.state"), elsewhere);
        assertThrows(ExecutionException.class, () -> store.read("link").get(10, TimeUnit.SECONDS));
        store.close(); assertThrows(CompletionException.class, () -> store.write("closed", Map.of()).join());
        assertTrue(store.awaitTermination(10, TimeUnit.SECONDS));
    }
    @Test void sessionEnvelopeValidatesDefinitionBeforeRecovery() {
        RuntimeTest.Stub stub = new RuntimeTest.Stub(); var session = RuntimeTest.session(new ArenaRuntime(), new ThreadGuard());
        session.start(new Registry<>(Map.of(RuntimeTest.SYSTEM, (s, c) -> stub)));
        SessionSnapshot saved = SessionSnapshot.capture(session); assertEquals(saved, SessionSnapshot.decode(saved.encode())); saved.validateAgainst(session.definition());
        Definition changed = new Definition(1, session.definition().id(), "different", true, 1, 2, Set.of(), session.definition().systems(), session.definition().arenas());
        assertThrows(ConfigException.class, () -> saved.validateAgainst(changed)); session.close();
    }
    @Test void editorPreviewUndoCancelAndResetNeverWrite() {
        EditorSession editor = new EditorSession(Map.of("id", "arena"), 3, new ThreadGuard());
        editor.select("point", List.of(1, 2, 3)); Map<String, Object> preview = editor.preview();
        editor.select("point", List.of(4, 5, 6)); assertEquals(List.of(1, 2, 3), preview.get("point"));
        assertTrue(editor.undo()); assertEquals(preview, editor.preview());
        editor.resetSelection("point"); assertFalse(editor.preview().containsKey("point")); assertTrue(editor.undo());
        editor.reset(); assertEquals(Map.of("id", "arena"), editor.preview()); editor.cancel();
        assertEquals(EditorSession.Status.CANCELLED, editor.status()); assertThrows(IllegalStateException.class, () -> editor.select("x", 1));
    }
    @Test void editorValidationFailureDoesNotPublishAndFailedSaveIsRetryable() {
        EditorSession editor = new EditorSession(Map.of("id", "arena"), 3, new ThreadGuard()); AtomicInteger writes = new AtomicInteger();
        assertThrows(ConfigException.class, () -> editor.save(n -> { throw new ConfigException("invalid"); }, data -> { writes.incrementAndGet(); return CompletableFuture.completedFuture(null); }));
        assertEquals(0, writes.get()); assertEquals(EditorSession.Status.EDITING, editor.status());
        assertThrows(CompletionException.class, () -> editor.save(n -> { }, data -> CompletableFuture.failedFuture(new IOException("disk"))).join());
        assertEquals(EditorSession.Status.EDITING, editor.status());
        CompletableFuture<Void> pending = new CompletableFuture<>(); var done = editor.save(n -> { }, data -> pending);
        assertEquals(EditorSession.Status.SAVING, editor.status()); assertThrows(IllegalStateException.class, editor::cancel);
        pending.complete(null); done.join(); assertEquals(EditorSession.Status.SAVED, editor.status());
    }
    @Test void explicitSavePublishesOneCompleteImmutableCandidate() throws Exception {
        EditorSession editor = new EditorSession(Map.of("id", "arena"), 2, new ThreadGuard());
        editor.select("path", List.of(List.of(0, 0, 0), List.of(5, 0, 0)));
        try (AtomicStore store = new AtomicStore(directory, 2)) {
            editor.save(n -> assertEquals(2, n.list("path").size()), data -> store.write("arena", data)).get(10, TimeUnit.SECONDS);
            assertEquals(editor.preview(), store.read("arena").get(10, TimeUnit.SECONDS).orElseThrow());
        }
    }
}
