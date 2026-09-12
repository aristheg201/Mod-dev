package vn.svframe.svarcade.editor;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.runtime.ThreadGuard;

/** RAM transaction. A publisher must validate and atomically save complete definition data. */
public final class EditorSession {
    public enum Status { EDITING, SAVING, SAVED, CANCELLED }
    @FunctionalInterface public interface Validator { void validate(Node candidate); }
    private final ThreadGuard thread;
    private final Map<String, Object> original;
    private final int historyLimit;
    private final Deque<Map<String, Object>> undo = new ArrayDeque<>();
    private Map<String, Object> draft;
    private volatile Status status = Status.EDITING;
    public EditorSession(Map<String, Object> original, int historyLimit, ThreadGuard thread) {
        if (historyLimit < 1 || historyLimit > 1024) throw new IllegalArgumentException("Editor history limit");
        this.original = Values.map(original); draft = this.original; this.historyLimit = historyLimit; this.thread = thread;
    }
    public void select(String key, Object value) {
        editable(); if (key == null || key.isBlank()) throw new IllegalArgumentException("Selection key");
        Map<String, Object> candidate = new LinkedHashMap<>(draft); candidate.put(key, Values.freeze(value));
        remember(); draft = Values.map(candidate);
    }
    public boolean undo() { editable(); if (undo.isEmpty()) return false; draft = undo.removeLast(); return true; }
    public void resetSelection(String key) {
        editable(); if (!draft.containsKey(key)) return;
        Map<String, Object> candidate = new LinkedHashMap<>(draft); candidate.remove(key); remember(); draft = Values.map(candidate);
    }
    public void reset() { editable(); remember(); draft = original; }
    private void remember() { if (undo.size() == historyLimit) undo.removeFirst(); undo.addLast(draft); }
    public Map<String, Object> preview() { thread.check(); return draft; }
    public void cancel() { editable(); draft = original; undo.clear(); status = Status.CANCELLED; }
    public CompletableFuture<Void> save(Validator validator, Function<Map<String, Object>, CompletableFuture<Void>> publisher) {
        editable(); validator.validate(new Node(draft, "editor"));
        Map<String, Object> candidate = draft; status = Status.SAVING;
        try {
            return publisher.apply(candidate).whenComplete((ok, error) -> { status = error == null ? Status.SAVED : Status.EDITING; });
        } catch (RuntimeException e) { status = Status.EDITING; throw e; }
    }
    public Status status() { return status; }
    private void editable() { thread.check(); if (status != Status.EDITING) throw new IllegalStateException("Editor is " + status); }
}
