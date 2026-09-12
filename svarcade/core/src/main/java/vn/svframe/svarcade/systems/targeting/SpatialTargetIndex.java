package vn.svframe.svarcade.systems.targeting;

import java.util.*;
import vn.svframe.svarcade.runtime.ThreadGuard;
import vn.svframe.svarcade.systems.path.Vec3;
import vn.svframe.svarcade.systems.targeting.TargetingAccess.*;

/** X/Z broad phase plus exact 3D range; caches cell membership, never stale target stats. */
public final class SpatialTargetIndex implements TargetingAccess {
    public static final class BudgetExceeded extends IllegalStateException {
        private static final long serialVersionUID = 1L;
        public BudgetExceeded(String message) { super(message); }
    }
    private record Cell(long x, long z) { }
    private record Area(long minX, long maxX, long minZ, long maxZ) { }
    private static final class Bucket {
        private long generation;
        private final Set<UUID> ids = new LinkedHashSet<>();
    }
    private record Cache(Area area, Map<Cell, Long> generations, List<UUID> candidates) { }
    private final ThreadGuard thread;
    private final double cellSize, maxRange;
    private final int capacity, cacheCapacity, maxCells, maxCandidates;
    private final Set<Mode> modes;
    private final Map<UUID, Target> targets = new HashMap<>();
    private final Map<Cell, Bucket> buckets = new HashMap<>();
    private final LinkedHashMap<UUID, Cache> caches = new LinkedHashMap<>(16, 0.75f, true);
    private long generation, queries, cacheHits, examined, visitedCells;
    public SpatialTargetIndex(ThreadGuard thread, double cellSize, double maxRange, int capacity,
                              int cacheCapacity, int maxCells, int maxCandidates, Set<Mode> modes) {
        if (!Double.isFinite(cellSize) || cellSize <= 0 || !Double.isFinite(maxRange) || maxRange < 0 || capacity < 1 || capacity > 100_000
                || cacheCapacity < 1 || cacheCapacity > 16_384 || maxCells < 1 || maxCells > 65_536 || maxCandidates < 1 || maxCandidates > capacity || modes.isEmpty()) throw new IllegalArgumentException("Target index limits");
        this.thread = Objects.requireNonNull(thread); this.cellSize = cellSize; this.maxRange = maxRange;
        this.capacity = capacity; this.cacheCapacity = cacheCapacity; this.maxCells = maxCells; this.maxCandidates = maxCandidates; this.modes = Set.copyOf(modes);
        // A definition's maximum legal range must fit at every sub-cell alignment.
        double span = Math.ceil(2 * maxRange / cellSize) + 1;
        if (!Double.isFinite(span) || span * span > maxCells) throw new IllegalArgumentException("Configured maximum range exceeds cell query budget");
    }
    private long coordinate(double value) {
        double c = Math.floor(value / cellSize);
        if (!Double.isFinite(c) || Math.abs(c) > 4_000_000_000_000_000L) throw new IllegalArgumentException("Coordinate outside spatial index precision");
        return (long) c;
    }
    private Cell cell(Vec3 position) { return new Cell(coordinate(position.x()), coordinate(position.z())); }
    private long nextGeneration() { return Math.incrementExact(generation); }
    @Override public void upsert(Target target) {
        thread.check(); Objects.requireNonNull(target); Cell destination = cell(target.position()); Target previous = targets.get(target.id());
        if (previous == null && targets.size() >= capacity) throw new IllegalStateException("Target capacity reached");
        Cell source = previous == null ? null : cell(previous.position());
        if (!destination.equals(source)) {
            long next = nextGeneration();
            if (source != null) unlink(source, target.id(), next);
            Bucket bucket = buckets.computeIfAbsent(destination, key -> new Bucket()); bucket.ids.add(target.id()); bucket.generation = next;
            generation = next;
        }
        targets.put(target.id(), target);
    }
    private void unlink(Cell cell, UUID id, long version) {
        Bucket bucket = buckets.get(cell); bucket.ids.remove(id); bucket.generation = version;
        if (bucket.ids.isEmpty()) buckets.remove(cell);
    }
    @Override public boolean remove(UUID id) {
        thread.check(); Target previous = targets.get(id); if (previous == null) return false;
        long next = nextGeneration(); unlink(cell(previous.position()), id, next); targets.remove(id); generation = next; return true;
    }
    private Area area(Query query) {
        long minX = coordinate(query.origin().x() - query.range()), maxX = coordinate(query.origin().x() + query.range());
        long minZ = coordinate(query.origin().z() - query.range()), maxZ = coordinate(query.origin().z() + query.range());
        long width = maxX - minX + 1, height = maxZ - minZ + 1;
        if (width > maxCells || height > maxCells || width * height > maxCells) throw new BudgetExceeded("Target cell budget exceeded");
        return new Area(minX, maxX, minZ, maxZ);
    }
    private boolean valid(Cache cache, Area area) {
        if (!cache.area().equals(area)) return false;
        for (var entry : cache.generations().entrySet()) {
            Bucket bucket = buckets.get(entry.getKey());
            if ((bucket == null ? 0 : bucket.generation) != entry.getValue()) return false;
        }
        return true;
    }
    private Cache collect(Area area) {
        Map<Cell, Long> versions = new LinkedHashMap<>(); List<UUID> candidates = new ArrayList<>();
        for (long x = area.minX(); x <= area.maxX(); x++) for (long z = area.minZ(); z <= area.maxZ(); z++) {
            Cell c = new Cell(x, z); Bucket bucket = buckets.get(c); visitedCells++;
            versions.put(c, bucket == null ? 0L : bucket.generation);
            if (bucket == null) continue;
            if (bucket.ids.size() > maxCandidates - candidates.size()) throw new BudgetExceeded("Target candidate budget exceeded");
            candidates.addAll(bucket.ids);
        }
        return new Cache(area, Map.copyOf(versions), List.copyOf(candidates));
    }
    @Override public Optional<Target> select(UUID requester, Query query) {
        thread.check(); Objects.requireNonNull(requester); Objects.requireNonNull(query);
        if (query.range() > maxRange || !modes.contains(query.mode())) throw new IllegalArgumentException("Targeting mode or range unavailable");
        Area area = area(query); Cache cached = caches.get(requester); queries++;
        if (cached != null && valid(cached, area)) cacheHits++;
        else {
            cached = collect(area);
            if (!caches.containsKey(requester) && caches.size() == cacheCapacity) caches.remove(caches.keySet().iterator().next());
            caches.put(requester, cached);
        }
        Target best = null; double bestScore = 0;
        for (UUID id : cached.candidates()) {
            Target target = targets.get(id); examined++;
            if (target == null || target.health() == 0 || !query.filter().accepts(target.tags())) continue;
            double distance = target.position().distance(query.origin()); if (distance > query.range()) continue;
            double score = switch (query.mode()) {
                case FIRST -> -target.progress(); case LAST -> target.progress();
                case CLOSEST -> distance; case FARTHEST -> -distance;
                case STRONGEST -> -target.strength(); case WEAKEST -> target.strength();
                case LOWEST_HP -> target.health(); case HIGHEST_HP -> -target.health();
            };
            if (best == null || score < bestScore || score == bestScore && target.id().compareTo(best.id()) < 0) { best = target; bestScore = score; }
        }
        return Optional.ofNullable(best);
    }
    public void clear() { thread.check(); targets.clear(); buckets.clear(); caches.clear(); }
    @Override public int size() { thread.check(); return targets.size(); }
    @Override public Map<String, Long> metrics() {
        thread.check(); return Map.of("queries", queries, "cache_hits", cacheHits, "candidates_examined", examined,
                "cells_visited", visitedCells, "targets", (long) targets.size(), "caches", (long) caches.size());
    }
}
