package vn.svframe.lively.world;

import java.util.ArrayList;
import java.util.List;
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
    }

    public record Structure(
            String id, String type, Bounds bounds, Set<String> capabilities, Map<String, String> points,
            String parentId, String townId, OperationalState state, long revision
    ) {
        public Structure {
            Objects.requireNonNull(id); Objects.requireNonNull(type); Objects.requireNonNull(bounds); Objects.requireNonNull(state);
            if (id.isBlank() || id.length() > 128) throw new IllegalArgumentException("invalid structure id");
            capabilities = Set.copyOf(capabilities); points = Map.copyOf(points);
        }
        public Structure withState(OperationalState next, long nextRevision) {
            return new Structure(id, type, bounds, capabilities, points, parentId, townId, next, nextRevision);
        }
    }

    private final ConcurrentHashMap<String, Structure> structures = new ConcurrentHashMap<>();
    private final AtomicLong revision = new AtomicLong();

    public Structure register(Structure structure) {
        Objects.requireNonNull(structure);
        if (structures.size() >= 100_000 && !structures.containsKey(structure.id())) throw new IllegalStateException("structure_limit");
        long nextRevision = revision.incrementAndGet();
        Structure normalized = new Structure(structure.id(), structure.type(), structure.bounds(), structure.capabilities(), structure.points(),
                structure.parentId(), structure.townId(), structure.state(), nextRevision);
        structures.put(normalized.id(), normalized);
        return normalized;
    }

    public Optional<Structure> get(String id) { return Optional.ofNullable(structures.get(id)); }

    public Optional<Structure> setState(String id, OperationalState state) {
        Objects.requireNonNull(state);
        final long nextRevision = revision.incrementAndGet();
        return Optional.ofNullable(structures.computeIfPresent(id, (key, current) -> current.withState(state, nextRevision)));
    }

    public List<Structure> at(String world, double x, double y, double z) {
        List<Structure> result = new ArrayList<>();
        for (Structure structure : structures.values()) if (structure.bounds().contains(world, x, y, z)) result.add(structure);
        return List.copyOf(result);
    }

    public Snapshot snapshot() { return new Snapshot(revision.get(), Map.copyOf(structures)); }

    public record Snapshot(long revision, Map<String, Structure> structures) {
        public Snapshot { structures = Map.copyOf(structures); }
    }
}
