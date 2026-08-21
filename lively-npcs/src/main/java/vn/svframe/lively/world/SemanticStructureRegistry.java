package vn.svframe.lively.world;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Semantic regions let AI reason about places without treating blocks as editable story state. */
public final class SemanticStructureRegistry {
    public enum OperationalState {
        OPEN, CLOSED, DAMAGED, RESTRICTED, UNDER_INVESTIGATION, ABANDONED, FESTIVAL, CONTROLLED
    }

    public record Bounds(String world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public Bounds {
            Objects.requireNonNull(world);
            if (minX > maxX || minY > maxY || minZ > maxZ) throw new IllegalArgumentException("invalid bounds");
        }
        public boolean contains(String worldKey, double x, double y, double z) {
            return world.equals(worldKey) && x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
        }
        public long volume() {
            return Math.multiplyExact(Math.multiplyExact((long) maxX - minX + 1L, (long) maxY - minY + 1L), (long) maxZ - minZ + 1L);
        }
    }

    public record Structure(String id, String type, Bounds bounds, Set<String> capabilities, Map<String, String> points,
                            String parentId, String townId, OperationalState state, long revision) {
        public Structure {
            Objects.requireNonNull(id); Objects.requireNonNull(type); Objects.requireNonNull(bounds); Objects.requireNonNull(state);
            if (id.isBlank() || id.length() > 128) throw new IllegalArgumentException("invalid structure id");
            capabilities = Set.copyOf(capabilities); points = Map.copyOf(points);
        }
        public Structure withState(OperationalState next, long nextRevision) {
            return new Structure(id, type, bounds, capabilities, points, parentId, townId, next, nextRevision);
        }
        public Structure withPoints(Map<String, String> next, long nextRevision) {
            return new Structure(id, type, bounds, capabilities, next, parentId, townId, state, nextRevision);
        }
        public Structure withMembership(String parent, String town, long nextRevision) {
            return new Structure(id, type, bounds, capabilities, points, parent, town, state, nextRevision);
        }
        public Structure withCapabilities(Set<String> next, long nextRevision) {
            return new Structure(id, type, bounds, next, points, parentId, townId, state, nextRevision);
        }
    }

    private final ConcurrentHashMap<String, Structure> structures = new ConcurrentHashMap<>();
    private final AtomicLong revision = new AtomicLong();

    public Structure register(Structure structure) {
        Objects.requireNonNull(structure);
        if (structures.size() >= 100_000 && !structures.containsKey(structure.id())) throw new IllegalStateException("structure_limit");
        long next = revision.incrementAndGet();
        Structure normalized = new Structure(structure.id(), structure.type(), structure.bounds(), structure.capabilities(), structure.points(),
                structure.parentId(), structure.townId(), structure.state(), next);
        structures.put(normalized.id(), normalized);
        return normalized;
    }

    public boolean remove(String id) {
        boolean removed = structures.remove(id) != null;
        if (removed) revision.incrementAndGet();
        return removed;
    }

    public Optional<Structure> get(String id) { return Optional.ofNullable(structures.get(id)); }

    public Optional<Structure> setState(String id, OperationalState state) {
        Objects.requireNonNull(state);
        long next = revision.incrementAndGet();
        return Optional.ofNullable(structures.computeIfPresent(id, (key, current) -> current.withState(state, next)));
    }

    public Optional<Structure> setCapabilities(String id, Set<String> capabilities) {
        if (capabilities.size() > 256) throw new IllegalArgumentException("too many structure capabilities");
        Set<String> normalized = capabilities.stream().map(SemanticStructureRegistry::normalize).filter(s -> !s.isBlank()).collect(java.util.stream.Collectors.toUnmodifiableSet());
        long next = revision.incrementAndGet();
        return Optional.ofNullable(structures.computeIfPresent(id, (key, current) -> current.withCapabilities(normalized, next)));
    }

    public Optional<Structure> addCapabilities(String id, Set<String> capabilities) {
        long next = revision.incrementAndGet();
        return Optional.ofNullable(structures.computeIfPresent(id, (key, current) -> {
            java.util.HashSet<String> merged = new java.util.HashSet<>(current.capabilities());
            capabilities.stream().map(SemanticStructureRegistry::normalize).filter(s -> !s.isBlank()).forEach(merged::add);
            if (merged.size() > 256) throw new IllegalArgumentException("too many structure capabilities");
            return current.withCapabilities(merged, next);
        }));
    }

    public Optional<Structure> setPoint(String id, String name, double x, double y, double z) {
        String key = normalize(name); long next = revision.incrementAndGet();
        return Optional.ofNullable(structures.computeIfPresent(id, (ignored, current) -> {
            HashMap<String, String> points = new HashMap<>(current.points());
            points.put(key, x + "," + y + "," + z);
            return current.withPoints(points, next);
        }));
    }

    public Optional<Structure> setPointIfAbsent(String id, String name, double x, double y, double z) {
        String key = normalize(name); long next = revision.incrementAndGet();
        return Optional.ofNullable(structures.computeIfPresent(id, (ignored, current) -> {
            if (current.points().containsKey(key)) return current;
            HashMap<String, String> points = new HashMap<>(current.points());
            points.put(key, x + "," + y + "," + z);
            return current.withPoints(points, next);
        }));
    }

    public Optional<Structure> removePoint(String id, String name) {
        String key = normalize(name); long next = revision.incrementAndGet();
        return Optional.ofNullable(structures.computeIfPresent(id, (ignored, current) -> {
            HashMap<String, String> points = new HashMap<>(current.points()); points.remove(key);
            return current.withPoints(points, next);
        }));
    }

    public Optional<Structure> setMembership(String id, String parent, String town) {
        if (parent != null && !structures.containsKey(parent)) return Optional.empty();
        long next = revision.incrementAndGet();
        return Optional.ofNullable(structures.computeIfPresent(id, (ignored, current) -> current.withMembership(parent, town, next)));
    }

    public List<Structure> at(String world, double x, double y, double z) {
        return structures.values().stream().filter(s -> s.bounds().contains(world, x, y, z))
                .sorted(Comparator.comparingLong(s -> s.bounds().volume())).toList();
    }

    public List<Structure> byType(String type) {
        String normalized = normalize(type);
        return structures.values().stream().filter(s -> normalize(s.type()).equals(normalized)).toList();
    }

    public Snapshot snapshot() { return new Snapshot(revision.get(), Map.copyOf(structures)); }
    public void restore(Snapshot snapshot) {
        structures.clear(); structures.putAll(snapshot.structures()); revision.set(Math.max(0L, snapshot.revision()));
    }

    public record Snapshot(long revision, Map<String, Structure> structures) {
        public Snapshot { structures = Map.copyOf(structures); }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.:-]", "_");
    }
}
