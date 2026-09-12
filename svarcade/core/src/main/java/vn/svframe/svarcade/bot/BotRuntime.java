package vn.svframe.svarcade.bot;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.runtime.*;

/** Off-thread pure decisions, bounded queue/workers, owner-thread stale-checked application. */
public final class BotRuntime implements AutoCloseable {
    public enum Difficulty { EASY, NORMAL, HARD }
    public record Context(UUID session, UUID participant, long revision, Map<String, Object> visibleState, Node tuning) {
        public Context { Objects.requireNonNull(session); Objects.requireNonNull(participant); Objects.requireNonNull(tuning); visibleState = Values.map(visibleState); }
    }
    public record Decision(Id action, Map<String, Object> payload) {
        public Decision { Objects.requireNonNull(action); payload = Values.map(payload); }
    }
    @FunctionalInterface public interface Strategy { Decision decide(Context context, ThinkBudget budget); }
    private record Pending(Context context, FutureTask<Decision> future) { }
    private final ThreadGuard thread;
    private final Registry<Strategy> strategies;
    private final ThreadPoolExecutor executor;
    private final Map<UUID, Pending> pending = new HashMap<>();
    private long stale, failed, applied;
    private boolean closed;
    public BotRuntime(ThreadGuard thread, Registry<Strategy> strategies, int workers, int queueCapacity) {
        if (workers < 1 || workers > 32 || queueCapacity < 1) throw new IllegalArgumentException("Bot worker limits");
        this.thread = thread; this.strategies = strategies;
        executor = new ThreadPoolExecutor(workers, workers, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(queueCapacity), runnable -> {
            Thread worker = new Thread(runnable, "svarcade-bot"); worker.setDaemon(true); return worker;
        }, new ThreadPoolExecutor.AbortPolicy());
    }
    public boolean submit(Context context, Id strategy, long nanos, long operations) {
        thread.check(); if (closed || pending.containsKey(context.participant())) return false;
        Strategy selected = strategies.require(strategy);
        // Validate budget before queueing; start elapsed timing when the worker begins.
        new ThinkBudget(nanos, operations);
        FutureTask<Decision> task = new FutureTask<>(() -> {
            ThinkBudget budget = new ThinkBudget(nanos, operations); budget.check();
            Decision decision = Objects.requireNonNull(selected.decide(context, budget)); budget.check(); return decision;
        });
        Pending entry = new Pending(context, task); pending.put(context.participant(), entry);
        try { executor.execute(task); return true; }
        catch (RejectedExecutionException e) { pending.remove(context.participant(), entry); return false; }
    }
    /** applyValidated must pass the decision through the same authoritative intent gate as players. */
    public boolean poll(GenericSession session, UUID participant, Consumer<Decision> applyValidated) {
        thread.check(); Pending work = pending.get(participant);
        if (work == null || !work.future().isDone()) return false;
        pending.remove(participant);
        if (session.status() != GenericSession.Status.RUNNING || !session.id().equals(work.context().session()) || session.revision() != work.context().revision()
                || !session.participants().containsKey(participant) || session.participants().get(participant).kind() != Participant.Kind.BOT) { stale++; return false; }
        try { applyValidated.accept(work.future().get()); applied++; return true; }
        catch (CancellationException | ExecutionException e) { failed++; return false; }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); failed++; return false; }
    }
    public void cancel(UUID participant) {
        thread.check(); Pending work = pending.remove(participant);
        if (work != null) { work.future().cancel(true); executor.remove(work.future()); }
    }
    @Override public void close() {
        thread.check(); closed = true;
        for (Pending work : pending.values()) work.future().cancel(true);
        pending.clear(); executor.shutdownNow();
    }
    public int pending() { thread.check(); return pending.size(); }
    public Map<String, Long> metrics() { thread.check(); return Map.of("stale", stale, "failed", failed, "applied", applied); }
}
