package vn.svframe.svarcade.verification;

import java.util.*;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.runtime.*;
import vn.svframe.svarcade.systems.path.Vec3;
import vn.svframe.svarcade.systems.targeting.*;
import vn.svframe.svarcade.systems.targeting.TargetingAccess.*;
import static vn.svframe.svarcade.verification.Checks.*;

public final class TargetingChecks {
    private TargetingChecks() { }
    private static double score(Target t, Query q) {
        return switch (q.mode()) {
            case FIRST -> -t.progress(); case LAST -> t.progress();
            case CLOSEST -> t.position().distance(q.origin()); case FARTHEST -> -t.position().distance(q.origin());
            case STRONGEST -> -t.strength(); case WEAKEST -> t.strength();
            case LOWEST_HP -> t.health(); case HIGHEST_HP -> -t.health();
        };
    }
    public static void main(String[] ignored) {
        SpatialTargetIndex index = new SpatialTargetIndex(new ThreadGuard(), 8, 25, 500, 16, 100, 500, Set.of(Mode.values()));
        List<Target> targets = new ArrayList<>(); Random random = new Random(764329L);
        for (int i = 0; i < 500; i++) {
            Target t = new Target(new UUID(0, i), new Vec3(random.nextDouble() * 100 - 50, random.nextDouble() * 20 - 10, random.nextDouble() * 100 - 50),
                    random.nextInt(101), random.nextDouble() * 100, random.nextDouble(), i % 2 == 0 ? Set.of("ground", "type:water") : Set.of("flying", "boss"));
            targets.add(t); index.upsert(t);
        }
        for (int i = 0; i < 100; i++) for (Mode mode : Mode.values()) {
            Filter filter = i % 2 == 0 ? Filter.unrestricted() : new Filter(Set.of("flying"), Set.of("boss", "elite"), Set.of("immune"));
            Query q = new Query(new Vec3(random.nextDouble() * 80 - 40, 0, random.nextDouble() * 80 - 40), random.nextDouble() * 25, mode, filter);
            Optional<Target> expected = targets.stream().filter(t -> t.health() > 0 && filter.accepts(t.tags()) && t.position().distance(q.origin()) <= q.range())
                    .min(Comparator.<Target>comparingDouble(t -> score(t, q)).thenComparing(Target::id));
            UUID requester = new UUID(1, i % 16); equal(expected, index.select(requester, q)); equal(expected, index.select(requester, q));
        }
        equal(true, index.metrics().get("cache_hits") >= 800); equal(16L, index.metrics().get("caches"));
        index.clear(); equal(0, index.size()); equal(0L, index.metrics().get("caches"));
        UUID a = new UUID(0, 1), requester = new UUID(1, 0);
        Query q = new Query(new Vec3(0, 0, 0), 2, Mode.CLOSEST, Filter.unrestricted());
        index.upsert(new Target(a, new Vec3(7, 0, 0), 10, 1, 0, Set.of())); equal(Optional.empty(), index.select(requester, q));
        index.upsert(new Target(a, new Vec3(1, 0, 0), 10, 1, 0, Set.of())); equal(a, index.select(requester, q).orElseThrow().id());
        index.upsert(new Target(a, new Vec3(-20, 0, 0), 10, 1, 0, Set.of())); equal(Optional.empty(), index.select(requester, q));
        index.upsert(new Target(a, new Vec3(0, 2, 0), 10, 1, 0, Set.of())); equal(a, index.select(requester, q).orElseThrow().id());
        index.upsert(new Target(a, new Vec3(0, 2.001, 0), 10, 1, 0, Set.of())); equal(Optional.empty(), index.select(requester, q));
        equal(true, index.remove(a)); equal(false, index.remove(a)); equal(Optional.empty(), index.select(requester, q));
        rejects(IllegalArgumentException.class, () -> index.select(requester, new Query(new Vec3(0, 0, 0), 26, Mode.CLOSEST, Filter.unrestricted())));
        SpatialTargetIndex bounded = new SpatialTargetIndex(new ThreadGuard(), 8, 2, 3, 1, 9, 1, Set.of(Mode.CLOSEST));
        bounded.upsert(new Target(a, new Vec3(0, 0, 0), 1, 1, 0, Set.of()));
        bounded.upsert(new Target(new UUID(0, 2), new Vec3(1, 0, 0), 1, 1, 0, Set.of()));
        rejects(SpatialTargetIndex.BudgetExceeded.class, () -> bounded.select(requester, q));
        rejects(IllegalArgumentException.class, () -> new SpatialTargetIndex(new ThreadGuard(), 1, 1000, 3, 1, 4, 3, Set.of(Mode.FIRST)));
        Node n = new Node(Map.of("cell_size", 8, "max_range", 25, "capacity", 500, "cache_capacity", 16,
                "max_query_cells", 100, "max_candidates", 500, "modes", Arrays.stream(Mode.values()).map(Enum::name).toList()), "targeting");
        TargetingSystem.Plan plan = new TargetingSystem.Plan(); plan.validate(n);
        Definition definition = new Definition(1, Id.of("test:index"), "v1", true, 1, 1, Set.of(),
                List.of(new Definition.SystemSpec(TargetingSystem.ID, n)), Map.of("arena", new Node(Map.of(), "arena")));
        ArenaRuntime arenas = new ArenaRuntime(); GenericSession session = new GenericSession(UUID.randomUUID(), definition, "arena",
                List.of(new Participant(UUID.randomUUID(), Participant.Kind.PLAYER, "team")), arenas, new ThreadGuard());
        session.start(new Registry<>(Map.of(TargetingSystem.ID, plan)));
        TargetingAccess access = session.services().require(TargetingSystem.ACCESS);
        access.upsert(new Target(a, new Vec3(0, 0, 0), 1, 1, 0, Set.of()));
        equal(a, access.select(requester, q).orElseThrow().id()); equal(Map.of(), session.snapshot().get(TargetingSystem.ID).data());
        session.close(); equal(0, access.size()); equal(0, arenas.snapshot().size());
        rejects(IllegalStateException.class, () -> access.select(requester, q));
        System.out.println("TargetingChecks: PASS (1600 indexed/oracle comparisons, eight modes, filters, moving-cache invalidation, 3D range, bounded workload)");
    }
}
