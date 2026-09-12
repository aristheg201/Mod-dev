package vn.svframe.svarcade.runtime;

import java.util.*;

/** Reverse-order cleanup. Failed resources remain tracked for retry and diagnostics. */
public final class ResourceTracker {
    @FunctionalInterface public interface Cleanup { void run() throws Exception; }
    public record Failure(String resource, Exception cause) { }
    private final ThreadGuard thread;
    private final LinkedHashMap<String, Cleanup> resources = new LinkedHashMap<>();
    private boolean closing;
    public ResourceTracker(ThreadGuard thread) { this.thread = Objects.requireNonNull(thread); }
    public void own(String id, Cleanup cleanup) {
        thread.check(); Objects.requireNonNull(id); Objects.requireNonNull(cleanup);
        if (closing) throw new IllegalStateException("Owner is closing");
        if (resources.putIfAbsent(id, cleanup) != null) throw new IllegalArgumentException("Duplicate resource: " + id);
    }
    public List<Failure> cleanup() {
        thread.check(); closing = true;
        List<Failure> failures = new ArrayList<>();
        List<String> keys = new ArrayList<>(resources.keySet());
        Collections.reverse(keys);
        for (String key : keys) {
            try { resources.get(key).run(); resources.remove(key); }
            catch (Exception e) { failures.add(new Failure(key, e)); }
        }
        return List.copyOf(failures);
    }
    public int size() { thread.check(); return resources.size(); }
    public Set<String> ids() { thread.check(); return Set.copyOf(resources.keySet()); }
}
