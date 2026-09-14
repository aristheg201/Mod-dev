package vn.svframe.svarcade.persistence;

import java.util.*;
import java.util.concurrent.*;
import vn.svframe.svarcade.runtime.*;

/** Startup recovery scanner. File reads are asynchronous; runtime acquisition/recovery stays on the owner thread. */
public final class RecoveryManager {
    public record Candidate(String key, SessionSnapshot snapshot) { }
    public record Failure(String key, String detail) { }
    public record Scan(List<Candidate> candidates, List<Failure> failures) {
        public Scan { candidates = List.copyOf(candidates); failures = List.copyOf(failures); }
    }
    public record Applied(List<UUID> recovered, List<Failure> aborted) {
        public Applied { recovered = List.copyOf(recovered); aborted = List.copyOf(aborted); }
    }

    private final ThreadGuard thread;
    private final AtomicStore store;
    private final int capacity;

    public RecoveryManager(ThreadGuard thread, AtomicStore store, int capacity) {
        this.thread = Objects.requireNonNull(thread); this.store = Objects.requireNonNull(store);
        if (capacity < 1 || capacity > 100_000) throw new IllegalArgumentException("Recovery capacity"); this.capacity = capacity;
    }

    /** Safe on any thread; all work is delegated to AtomicStore's bounded I/O executor. */
    public CompletableFuture<Scan> scan() {
        return store.keys().thenCompose(keys -> {
            List<String> sessionKeys = keys.stream().filter(key -> key.startsWith("session_")).toList();
            if (sessionKeys.size() > capacity) return CompletableFuture.failedFuture(new IllegalStateException("Recovery candidate capacity"));
            CompletableFuture<Scan> chain = CompletableFuture.completedFuture(new Scan(List.of(), List.of()));
            for (String key : sessionKeys) chain = chain.thenCompose(scan -> store.read(key).handle((state, failure) -> {
                List<Candidate> candidates = new ArrayList<>(scan.candidates()); List<Failure> failures = new ArrayList<>(scan.failures());
                if (failure != null) failures.add(new Failure(key, concise(unwrap(failure))));
                else if (state.isEmpty()) failures.add(new Failure(key, "state disappeared during scan"));
                else {
                    try {
                        SessionSnapshot snapshot = SessionSnapshot.decode(state.get()); UUID keyId = PersistenceManager.sessionId(key);
                        if (!snapshot.id().equals(keyId)) throw new IllegalArgumentException("state key/session id mismatch");
                        candidates.add(new Candidate(key, snapshot));
                    } catch (RuntimeException invalid) { failures.add(new Failure(key, concise(invalid))); }
                }
                return new Scan(candidates, failures);
            }));
            return chain;
        });
    }

    /** Owner-thread application. Invalid/unrecoverable candidates are asynchronously removed to prevent restart loops. */
    public Applied apply(GenericGameRuntime runtime, PersistenceManager persistence, Scan scan) {
        thread.check(); Objects.requireNonNull(runtime); Objects.requireNonNull(persistence); Objects.requireNonNull(scan);
        List<UUID> recovered = new ArrayList<>(); List<Failure> aborted = new ArrayList<>(scan.failures());
        Set<String> failedKeys = new LinkedHashSet<>(); scan.failures().forEach(failure -> failedKeys.add(failure.key()));
        for (Candidate candidate : scan.candidates()) {
            try {
                GenericSession session = runtime.recover(candidate.snapshot()); persistence.recovered(session); recovered.add(session.id());
            } catch (RuntimeException failure) { aborted.add(new Failure(candidate.key(), concise(failure))); failedKeys.add(candidate.key()); }
        }
        for (String key : failedKeys) store.delete(key); // asynchronous; failure is surfaced by the returned store future only in diagnostics layer
        return new Applied(recovered, aborted);
    }

    private static Throwable unwrap(Throwable failure) {
        if (failure instanceof CompletionException e && e.getCause() != null) return e.getCause(); return failure;
    }
    private static String concise(Throwable failure) { String text = String.valueOf(failure); return text.length() <= 2048 ? text : text.substring(0, 2048); }
}
