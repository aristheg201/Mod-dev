package vn.svframe.svarcade.runtime;

import java.util.*;

/** Bounded deadline queue. No callback runs from an I/O or worker thread. */
public final class TimerRuntime {
    private record Entry(String id, long deadline, long sequence, Runnable action) { }
    private final PriorityQueue<Entry> queue = new PriorityQueue<>(Comparator.comparingLong(Entry::deadline).thenComparingLong(Entry::sequence));
    private final Map<String, Entry> live = new HashMap<>();
    private final ThreadGuard thread;
    private final int capacity;
    private long sequence;
    private long lastTick;
    public TimerRuntime(ThreadGuard thread, int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("Timer capacity");
        this.thread = thread; this.capacity = capacity;
    }
    public void schedule(String id, long deadline, Runnable callback) {
        thread.check(); Objects.requireNonNull(id); Objects.requireNonNull(callback);
        if (deadline < lastTick) throw new IllegalArgumentException("Deadline in the past");
        if (!live.containsKey(id) && live.size() >= capacity) throw new IllegalStateException("Timer capacity reached");
        // Remove replaced entry immediately: no unbounded tombstone accumulation.
        Entry previous = live.remove(id);
        if (previous != null) queue.remove(previous);
        Entry entry = new Entry(id, deadline, sequence++, callback);
        live.put(id, entry); queue.add(entry);
    }
    public void cancel(String id) {
        thread.check(); Entry old = live.remove(id); if (old != null) queue.remove(old);
    }
    public int advance(long tick, int maxCallbacks) {
        thread.check();
        if (tick < lastTick || maxCallbacks < 1) throw new IllegalArgumentException("Invalid timer advance");
        lastTick = tick;
        int count = 0;
        // New timers scheduled by callbacks wait for the next advance.
        long cutoff = sequence;
        List<Entry> due = new ArrayList<>();
        while (due.size() < maxCallbacks && !queue.isEmpty() && queue.peek().deadline() <= tick && queue.peek().sequence() < cutoff) due.add(queue.remove());
        for (Entry entry : due) {
            if (!live.remove(entry.id(), entry)) continue;
            try { entry.action().run(); }
            catch (RuntimeException e) {
                // Preserve the other due timers; the failed callback is not retried implicitly.
                for (Entry pending : due) if (live.get(pending.id()) == pending) queue.add(pending);
                throw e;
            }
            count++;
        }
        return count;
    }
    public void clear() { thread.check(); live.clear(); queue.clear(); }
    public int size() { thread.check(); return live.size(); }
    public Map<String, Long> remaining(long tick) {
        thread.check(); Map<String, Long> result = new TreeMap<>();
        live.forEach((id, entry) -> result.put(id, Math.max(0, entry.deadline() - tick)));
        return Map.copyOf(result);
    }
}
