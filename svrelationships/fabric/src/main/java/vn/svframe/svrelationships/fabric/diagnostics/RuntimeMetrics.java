package vn.svframe.svrelationships.fabric.diagnostics;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

public final class RuntimeMetrics {
    private final ConcurrentHashMap<String, LongAdder> counters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> gauges = new ConcurrentHashMap<>();

    public void increment(String id) { counters.computeIfAbsent(id, ignored -> new LongAdder()).increment(); }
    public void add(String id, long amount) { counters.computeIfAbsent(id, ignored -> new LongAdder()).add(amount); }
    public void gauge(String id, long value) { gauges.computeIfAbsent(id, ignored -> new AtomicLong()).set(value); }
    public long counter(String id) { LongAdder value = counters.get(id); return value == null ? 0 : value.sum(); }
    public long gauge(String id) { AtomicLong value = gauges.get(id); return value == null ? 0 : value.get(); }
    public Map<String, Long> counterSnapshot() {
        Map<String, Long> result = new java.util.TreeMap<>(); counters.forEach((k,v) -> result.put(k, v.sum())); return Map.copyOf(result);
    }
    public Map<String, Long> gaugeSnapshot() {
        Map<String, Long> result = new java.util.TreeMap<>(); gauges.forEach((k,v) -> result.put(k, v.get())); return Map.copyOf(result);
    }
}
