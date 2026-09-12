package vn.svframe.svarcade.config;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

/** Publishes complete candidates only. Submission and publication share a monitor. */
public final class DefinitionRegistry {
    public record Snapshot(long generation, Map<Id, Definition> definitions, Map<Id, Set<String>> unavailable) {
        public Snapshot {
            definitions = Map.copyOf(definitions);
            Map<Id, Set<String>> copy = new HashMap<>();
            unavailable.forEach((id, missing) -> copy.put(id, Set.copyOf(missing)));
            unavailable = Map.copyOf(copy);
        }
        public Definition requireAvailable(Id id) {
            Definition d = definitions.get(id);
            if (d == null || !d.enabled() || unavailable.containsKey(id)) throw new ConfigException("Definition unavailable: " + id);
            return d;
        }
    }
    public record ReloadResult(boolean applied, long generation, String reason) { }
    private volatile Snapshot current = new Snapshot(0, Map.of(), Map.of());
    private long requested;
    public Snapshot snapshot() { return current; }

    /** The supplied executor must be a bounded I/O executor, never the server thread. */
    public CompletableFuture<ReloadResult> reload(Supplier<List<Definition>> loader,
                                                  Set<String> integrations, Executor executor) {
        final long ticket;
        final Set<String> available = Set.copyOf(integrations);
        synchronized (this) { ticket = ++requested; }
        try {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    Map<Id, Definition> definitions = new LinkedHashMap<>();
                    Map<Id, Set<String>> unavailable = new LinkedHashMap<>();
                    for (Definition d : loader.get()) {
                        if (definitions.putIfAbsent(d.id(), d) != null) throw new ConfigException("Duplicate definition: " + d.id());
                        Set<String> missing = new TreeSet<>(d.integrations());
                        missing.removeAll(available);
                        if (!missing.isEmpty()) unavailable.put(d.id(), missing);
                    }
                    synchronized (this) {
                        if (ticket != requested) return new ReloadResult(false, current.generation(), "superseded");
                        current = new Snapshot(current.generation() + 1, definitions, unavailable);
                        return new ReloadResult(true, current.generation(), "applied");
                    }
                } catch (RuntimeException e) {
                    return new ReloadResult(false, current.generation(), e.toString());
                }
            }, executor);
        } catch (RejectedExecutionException e) {
            return CompletableFuture.completedFuture(new ReloadResult(false, current.generation(), "executor saturated"));
        }
    }
}
