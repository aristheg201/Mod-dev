package vn.svframe.svarcade.stats;

import java.util.*;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.runtime.ThreadGuard;

/** Persistent generic metric store and leaderboard. Definitions decide which metric IDs are emitted/displayed. */
public final class StatsManager {
    public enum Aggregation { SUM, MAX, LAST }
    public record Metric(Id id, Aggregation aggregation, long minimum, long maximum) {
        public Metric {
            Objects.requireNonNull(id); Objects.requireNonNull(aggregation);
            if (minimum > maximum) throw new IllegalArgumentException("Metric bounds");
        }
    }
    public record Row(UUID subject, long value) { }

    private final ThreadGuard thread;
    private final Map<Id, Metric> metrics;
    private final Map<UUID, Map<Id, Long>> values = new HashMap<>();
    private final int subjectCapacity;
    private long revision;

    public StatsManager(ThreadGuard thread, Collection<Metric> metrics, int subjectCapacity) {
        this.thread = Objects.requireNonNull(thread); if (subjectCapacity < 1 || subjectCapacity > 10000000) throw new IllegalArgumentException("Stats capacity");
        this.subjectCapacity = subjectCapacity; Map<Id, Metric> compiled = new LinkedHashMap<>();
        for (Metric metric : metrics) if (compiled.putIfAbsent(metric.id(), metric) != null) throw new ConfigException("Duplicate metric: " + metric.id());
        if (compiled.isEmpty() || compiled.size() > 10000) throw new IllegalArgumentException("Metric count"); this.metrics = Map.copyOf(compiled);
    }
    public long value(UUID subject, Id metric) {
        thread.check(); requireMetric(metric); return values.getOrDefault(subject, Map.of()).getOrDefault(metric, 0L);
    }
    public long record(UUID subject, Id metricId, long input) {
        thread.check(); Objects.requireNonNull(subject); Metric metric = requireMetric(metricId);
        if (input < metric.minimum() || input > metric.maximum()) throw new IllegalArgumentException("Metric input outside bounds");
        Map<Id, Long> row = values.get(subject); if (row == null) {
            if (values.size() >= subjectCapacity) throw new IllegalStateException("Stats subject capacity"); row = new HashMap<>(); values.put(subject, row);
        }
        long before = row.getOrDefault(metricId, 0L), after;
        try {
            after = switch (metric.aggregation()) {
                case SUM -> Math.addExact(before, input);
                case MAX -> Math.max(before, input);
                case LAST -> input;
            };
        } catch (ArithmeticException overflow) { throw new IllegalStateException("Metric overflow", overflow); }
        if (after < metric.minimum() || after > metric.maximum()) throw new IllegalStateException("Aggregated metric outside bounds");
        row.put(metricId, after); revision = Math.incrementExact(revision); return after;
    }
    public List<Row> leaderboard(Id metric, int maximum) {
        thread.check(); requireMetric(metric); if (maximum < 1 || maximum > 1000) throw new IllegalArgumentException("Leaderboard limit");
        return values.entrySet().stream().map(e -> new Row(e.getKey(), e.getValue().getOrDefault(metric, 0L)))
                .filter(r -> r.value() != 0).sorted(Comparator.comparingLong(Row::value).reversed().thenComparing(r -> r.subject().toString())).limit(maximum).toList();
    }
    public long revision() { thread.check(); return revision; }
    public Map<String,Object> snapshot() {
        thread.check(); Map<String,Object> rows = new TreeMap<>();
        values.forEach((subject, row) -> { Map<String,Object> metricValues = new TreeMap<>(); row.forEach((id, value) -> metricValues.put(id.toString(), value)); rows.put(subject.toString(), metricValues); });
        return Values.map(Map.of("schema", 1, "revision", revision, "subjects", rows));
    }
    public void restore(Map<String,Object> state) {
        thread.check(); if (!values.isEmpty() || revision != 0) throw new IllegalStateException("Stats already initialized");
        Node n = new Node(state, "stats-state"); n.only("schema", "revision", "subjects"); if (n.integer("schema", 1, 1) != 1) throw new ConfigException("Stats schema");
        long restoredRevision = n.integer("revision", 0, Long.MAX_VALUE - 1); Node subjects = n.node("subjects");
        if (subjects.values().size() > subjectCapacity) throw new ConfigException("Stats subject capacity");
        for (String rawSubject : subjects.values().keySet()) {
            UUID subject = UUID.fromString(rawSubject); Node row = subjects.node(rawSubject); Map<Id, Long> restored = new HashMap<>();
            for (String rawMetric : row.values().keySet()) {
                Id id = Id.of(rawMetric); Metric metric = requireMetric(id); long value = row.integer(rawMetric, metric.minimum(), metric.maximum()); restored.put(id, value);
            }
            values.put(subject, restored);
        }
        revision = restoredRevision;
    }
    private Metric requireMetric(Id id) { Metric metric = metrics.get(id); if (metric == null) throw new IllegalArgumentException("Unknown metric"); return metric; }
}
