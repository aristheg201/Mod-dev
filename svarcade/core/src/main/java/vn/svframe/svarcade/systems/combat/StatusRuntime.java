package vn.svframe.svarcade.systems.combat;

import java.util.*;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.systems.combat.CombatAccess.*;

/** Actor-owned timed statuses; advances all pulses arithmetically in O(active statuses). */
public final class StatusRuntime {
    public record Active(Id id, int stacks, long remainingTicks, long nextPulseTicks) { }
    private record Entry(int stacks, long expires, long nextPulse) { }
    private final Map<Id, StatusDefinition> definitions;
    private final int capacity;
    private final NavigableMap<Id, Entry> active = new TreeMap<>();
    private long elapsed;
    public StatusRuntime(Map<Id, StatusDefinition> definitions, int capacity) {
        if (capacity < 1 || capacity > 512) throw new IllegalArgumentException("Status capacity");
        this.definitions = Map.copyOf(definitions); this.capacity = capacity;
        for (StatusDefinition d : this.definitions.values()) {
            if (d.kind() == null || d.stacking() == null || d.baseDurationTicks() < 1 || d.baseDurationTicks() > 1_000_000
                    || d.tickInterval() < 1 || d.tickInterval() > d.baseDurationTicks() || !Double.isFinite(d.magnitude())
                    || Math.abs(d.magnitude()) > 1_000_000 || d.maxStacks() < 1 || d.maxStacks() > 1024
                    || d.kind() == StatusKind.DOT && d.magnitude() < 0) throw new ConfigException("Invalid runtime status definition");
        }
    }
    /** Only accepts resolutions consistent with the pinned combat definitions. */
    public boolean apply(AppliedStatus applied) {
        StatusDefinition d = requireDefinition(applied.status());
        if (applied.kind() != d.kind() || applied.stacking() != d.stacking() || applied.tickInterval() != d.tickInterval()
                || Double.compare(applied.magnitude(), d.magnitude()) != 0 || applied.stacks() < 1 || applied.stacks() > d.maxStacks()
                || applied.durationTicks() < 1 || applied.durationTicks() > d.baseDurationTicks() * 100)
            throw new IllegalArgumentException("Status resolution does not match definition");
        Entry old = active.get(applied.status());
        if (old != null && d.stacking() == Stacking.IGNORE) return false;
        if (old == null && active.size() >= capacity) throw new IllegalStateException("Active status capacity");
        long expires = Math.addExact(elapsed, applied.durationTicks());
        long next = Math.addExact(elapsed, d.tickInterval()); int stacks = applied.stacks();
        if (old != null && d.stacking() != Stacking.REPLACE) {
            next = old.nextPulse();
            stacks = d.stacking() == Stacking.STACK ? Math.min(d.maxStacks(), old.stacks() + stacks) : Math.max(old.stacks(), stacks);
        }
        active.put(applied.status(), new Entry(stacks, expires, next)); return true;
    }
    /** Expiry is inclusive: no DoT pulse fires at or after the expiration boundary. */
    public double advance(long ticks) {
        if (ticks < 0) throw new IllegalArgumentException("Negative status advance");
        long nextElapsed = Math.addExact(elapsed, ticks); double damage = 0;
        NavigableMap<Id, Entry> next = new TreeMap<>();
        for (var item : active.entrySet()) {
            Entry e = item.getValue(); StatusDefinition d = requireDefinition(item.getKey()); long due = e.nextPulse();
            if (d.kind() == StatusKind.DOT) {
                long end = Math.min(nextElapsed, e.expires() - 1);
                if (due <= end) {
                    long count = (end - due) / d.tickInterval() + 1;
                    damage += d.magnitude() * e.stacks() * count;
                    due = Math.addExact(due, Math.multiplyExact(count, d.tickInterval()));
                }
            }
            if (e.expires() > nextElapsed) {
                if (d.kind() != StatusKind.DOT) due = Math.addExact(nextElapsed, d.tickInterval());
                next.put(item.getKey(), new Entry(e.stacks(), e.expires(), due));
            }
        }
        if (!Double.isFinite(damage)) throw new IllegalStateException("Non-finite status damage");
        active.clear(); active.putAll(next); elapsed = nextElapsed; return damage;
    }
    public boolean has(StatusKind kind) { return active.keySet().stream().anyMatch(id -> requireDefinition(id).kind() == kind); }
    public double magnitude(Id id) {
        Entry e = active.get(id); return e == null ? 0 : requireDefinition(id).magnitude() * e.stacks();
    }
    public Set<Id> ids() { return Set.copyOf(active.keySet()); }
    public List<Active> active() {
        List<Active> result = new ArrayList<>();
        active.forEach((id, e) -> result.add(new Active(id, e.stacks(), e.expires() - elapsed, Math.max(1, e.nextPulse() - elapsed))));
        return List.copyOf(result);
    }
    public Map<String, Object> snapshot() {
        return Map.of("schema", 1, "active", active().stream().map(s -> Map.of("id", s.id().toString(), "stacks", s.stacks(),
                "remaining", s.remainingTicks(), "next_pulse", s.nextPulseTicks())).toList());
    }
    /** Validate the entire candidate before replacing live status state. */
    public void restore(Map<String, Object> state) {
        Node n = new Node(state, "statuses"); n.only("schema", "active"); n.integer("schema", 1, 1);
        if (n.list("active").size() > capacity) throw new ConfigException("Restored status capacity");
        NavigableMap<Id, Entry> candidate = new TreeMap<>();
        for (Node row : n.nodes("active")) {
            row.only("id", "stacks", "remaining", "next_pulse"); Id id = Id.of(row.string("id")); StatusDefinition d = requireDefinition(id);
            int stacks = (int) row.integer("stacks", 1, d.maxStacks());
            long remaining = row.integer("remaining", 1, d.baseDurationTicks() * 100), next = row.integer("next_pulse", 1, d.tickInterval());
            if (candidate.putIfAbsent(id, new Entry(stacks, remaining, next)) != null) throw new ConfigException("Duplicate restored status");
        }
        active.clear(); active.putAll(candidate); elapsed = 0;
    }
    private StatusDefinition requireDefinition(Id id) {
        StatusDefinition d = definitions.get(id); if (d == null) throw new ConfigException("Unknown runtime status: " + id); return d;
    }
}
