package vn.svframe.svarcade.bot;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.LongSupplier;
import vn.svframe.svarcade.security.ActionAccess;
import vn.svframe.svarcade.security.IntentGate;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.runtime.*;

/** Server-owned bounded worker service, shared by sessions. Workers never receive live sessions. */
public final class BotRuntime implements AutoCloseable {
    public enum Difficulty { EASY, NORMAL, HARD }
    public record Context(UUID session, UUID participant, long revision, Map<String, Object> visibleState, Node tuning) {
        public Context {
            Objects.requireNonNull(session); Objects.requireNonNull(participant); Objects.requireNonNull(tuning);
            if (revision < 0) throw new IllegalArgumentException("Negative bot revision");
            visibleState = Values.map(visibleState);
        }
    }
    public record Decision(Id action, Map<String, Object> payload) {
        public Decision { Objects.requireNonNull(action); payload = Values.map(payload); }
    }
    @FunctionalInterface public interface Strategy { Decision decide(Context context, ThinkBudget budget); }
    /** Implementations may return a fully computed legal prefix when budget exhaustion ends search.
     * They must propagate interruption, charge their loops, and never return half-computed work. */
    @FunctionalInterface public interface BoundedStrategy extends Strategy {
        Optional<Decision> decideWithinBudget(Context context, ThinkBudget budget);
        @Override default Decision decide(Context context, ThinkBudget budget) {
            return decideWithinBudget(context, budget).orElseThrow(() -> new CancellationException("No completed bot decision"));
        }
    }
    public enum Poll { WAITING, APPLIED, REJECTED, STALE, EMPTY, FAILED }
    private static final class Measurement {
        volatile long nanos, operations;
        volatile boolean exhausted;
    }
    private record Pending(Context context, FutureTask<Optional<Decision>> future, Measurement measurement) { }
    private final ThreadGuard thread;
    private final Registry<Strategy> strategies;
    private final ThreadPoolExecutor executor;
    private final LongSupplier clock;
    private final int capacity;
    private final Map<UUID, Pending> pending = new HashMap<>();
    private final Deque<String> failures = new ArrayDeque<>();
    private long stale, failed, applied, rejected, cancelled, saturated, empty, exhausted, nodes, totalNanos, maxNanos;
    private boolean closed;

    public BotRuntime(ThreadGuard thread, Registry<Strategy> strategies, int workers, int queueCapacity) {
        this(thread, strategies, workers, queueCapacity, System::nanoTime);
    }
    public BotRuntime(ThreadGuard thread, Registry<Strategy> strategies, int workers, int queueCapacity, LongSupplier clock) {
        if (workers < 1 || workers > 32 || queueCapacity < 1 || queueCapacity > 4096) throw new IllegalArgumentException("Bot worker limits");
        this.thread = Objects.requireNonNull(thread); this.strategies = Objects.requireNonNull(strategies);
        this.clock = Objects.requireNonNull(clock); capacity = workers + queueCapacity;
        executor = new ThreadPoolExecutor(workers, workers, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(queueCapacity), runnable -> {
            Thread worker = new Thread(runnable, "svarcade-bot"); worker.setDaemon(true); return worker;
        }, new ThreadPoolExecutor.AbortPolicy());
    }
    public boolean submit(Context context, Id strategy, long nanos, long operations) {
        thread.check(); return submit(context, strategies.require(strategy), nanos, operations);
    }
    /** Accepts an already compiled, detached strategy. It has no authoritative mutation callback. */
    public boolean submit(Context context, Strategy strategy, long nanos, long operations) {
        thread.check(); Objects.requireNonNull(context); Objects.requireNonNull(strategy);
        new ThinkBudget(nanos, operations, clock);
        if (closed || pending.containsKey(context.participant())) return false;
        // Completed but not yet polled tasks also retain snapshots and must count toward the cap.
        if (pending.size() >= capacity) { saturated++; return false; }
        Measurement measurement = new Measurement();
        FutureTask<Optional<Decision>> task = new FutureTask<>(() -> {
            ThinkBudget budget = new ThinkBudget(nanos, operations, clock);
            try {
                budget.check(); Optional<Decision> result;
                if (strategy instanceof BoundedStrategy bounded) {
                    result = Objects.requireNonNull(bounded.decideWithinBudget(context, budget));
                    budget.checkCancellation();
                } else {
                    result = Optional.of(Objects.requireNonNull(strategy.decide(context, budget)));
                    budget.check();
                }
                return result;
            } finally {
                measurement.nanos = Math.max(0, budget.elapsedNanos()); measurement.operations = budget.operations();
                measurement.exhausted = budget.exhaustedReason().isPresent();
            }
        });
        Pending entry = new Pending(context, task, measurement); pending.put(context.participant(), entry);
        try { executor.execute(task); return true; }
        catch (RejectedExecutionException e) { pending.remove(context.participant(), entry); saturated++; return false; }
    }
    public boolean poll(GenericSession session, UUID participant, ActionAccess dispatcher, IntentGate.Facts facts) {
        return pollResult(session, participant, dispatcher, facts) == Poll.APPLIED;
    }
    /** No get() is performed until completion. Every result still crosses the shared intent gate. */
    public Poll pollResult(GenericSession session, UUID participant, ActionAccess dispatcher, IntentGate.Facts facts) {
        thread.check(); if (!participant.equals(facts.actor())) throw new IllegalArgumentException("Bot actor mismatch");
        Pending work = pending.get(participant);
        if (work == null || !work.future().isDone()) return Poll.WAITING;
        pending.remove(participant); measure(work.measurement());
        if (session.status() != GenericSession.Status.RUNNING || !session.id().equals(work.context().session()) || session.revision() != work.context().revision()
                || !session.participants().containsKey(participant) || session.participants().get(participant).kind() != Participant.Kind.BOT) { stale++; return Poll.STALE; }
        try {
            Optional<Decision> decision = work.future().get();
            if (decision.isEmpty()) { empty++; return Poll.EMPTY; }
            IntentGate.Result result = dispatcher.dispatchBot(facts, work.context().session(), work.context().revision(), decision.get());
            if (result.accepted()) { applied++; return Poll.APPLIED; }
            rejected++; return Poll.REJECTED;
        } catch (CancellationException | ExecutionException e) { recordFailure(e); return Poll.FAILED; }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); recordFailure(e); return Poll.FAILED; }
    }
    private void recordFailure(Exception failure) {
        failed++;
        Throwable cause = failure instanceof ExecutionException && failure.getCause() != null ? failure.getCause() : failure;
        String detail = cause.getClass().getName() + ": " + String.valueOf(cause.getMessage());
        if (failures.size() == 32) failures.removeFirst();
        failures.addLast(detail.substring(0, Math.min(1024, detail.length())));
    }
    private void measure(Measurement m) {
        nodes = add(nodes, m.operations); totalNanos = add(totalNanos, m.nanos); maxNanos = Math.max(maxNanos, m.nanos);
        if (m.exhausted) exhausted++;
    }
    private static long add(long current, long value) { return value > Long.MAX_VALUE - current ? Long.MAX_VALUE : current + value; }
    public boolean hasPending(UUID participant) { thread.check(); return pending.containsKey(participant); }
    public boolean done(UUID participant) { thread.check(); Pending work = pending.get(participant); return work != null && work.future().isDone(); }
    public void cancel(UUID participant) {
        thread.check(); Pending work = pending.remove(participant); if (work != null) cancel(work);
    }
    public void cancelSession(UUID session) {
        thread.check();
        Iterator<Pending> work = pending.values().iterator();
        while (work.hasNext()) { Pending task = work.next(); if (task.context().session().equals(session)) { work.remove(); cancel(task); } }
    }
    private void cancel(Pending work) { work.future().cancel(true); executor.remove(work.future()); cancelled++; }
    @Override public void close() {
        thread.check(); if (closed) return; closed = true;
        pending.values().forEach(this::cancel); pending.clear(); executor.shutdownNow();
    }
    /** Join only from shutdown/off-thread test orchestration, never a server tick. */
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException { return executor.awaitTermination(timeout, unit); }
    public int pending() { thread.check(); return pending.size(); }
    public Map<String, Long> metrics() {
        thread.check();
        return Map.ofEntries(Map.entry("stale", stale), Map.entry("failed", failed), Map.entry("applied", applied), Map.entry("rejected", rejected),
                Map.entry("cancelled", cancelled), Map.entry("saturated", saturated), Map.entry("no_decision", empty), Map.entry("exhausted", exhausted),
                Map.entry("nodes", nodes), Map.entry("think_nanos_total", totalNanos), Map.entry("think_nanos_max", maxNanos), Map.entry("pending", (long) pending.size()));
    }
    public List<String> failures() { thread.check(); return List.copyOf(failures); }
}
