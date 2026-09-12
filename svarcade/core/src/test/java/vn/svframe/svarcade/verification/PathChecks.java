package vn.svframe.svarcade.verification;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.runtime.*;
import vn.svframe.svarcade.systems.path.*;
import static vn.svframe.svarcade.verification.Checks.*;

public final class PathChecks {
    private PathChecks() { }
    public static void main(String[] ignored) {
        Id route = Id.of("test:route");
        Polyline path = new Polyline(List.of(new Vec3(0, 0, 0), new Vec3(3, 0, 0), new Vec3(3, 0, 4)));
        equal(7.0, path.length()); equal(new Vec3(3, 0, 2), path.at(5)); equal(new Vec3(3, 0, 4), path.at(7));
        rejects(IllegalArgumentException.class, () -> path.at(Double.NaN));
        rejects(IllegalArgumentException.class, () -> path.at(8));
        rejects(IllegalArgumentException.class, () -> new Polyline(List.of(new Vec3(0, 0, 0), new Vec3(0, 0, 0))));
        rejects(IllegalArgumentException.class, () -> new Vec3(Double.POSITIVE_INFINITY, 0, 0));
        PathSystem.Config config = new PathSystem.Config(2, 4, 100, Map.of(route, path));
        AtomicInteger changes = new AtomicInteger(); PathSystem first = new PathSystem(config, new ThreadGuard(), changes::incrementAndGet);
        UUID a = new UUID(0, 1), b = new UUID(0, 2); first.start(); first.spawn(a, route, 1); first.spawn(b, route, 0);
        rejects(IllegalStateException.class, () -> first.spawn(a, route, 1));
        rejects(IllegalStateException.class, () -> first.spawn(UUID.randomUUID(), route, 1));
        first.tick(100); first.tick(105); equal(new Vec3(3, 0, 2), first.position(a)); equal(new Vec3(0, 0, 0), first.position(b));
        Map<String, Object> state = first.snapshot(); PathSystem restored = new PathSystem(config, new ThreadGuard(), () -> { });
        restored.restore(1, state); restored.tick(500); restored.tick(502); first.tick(107);
        equal(first.snapshot(), restored.snapshot()); equal(List.of(a), restored.drainArrivals(10)); equal(List.of(), restored.drainArrivals(10));
        restored.tick(503); equal(List.of(), restored.drainArrivals(10)); equal(true, restored.remove(a)); equal(false, restored.remove(a));
        restored.speed(b, 2); restored.tick(507); equal(List.of(b), restored.drainArrivals(1));
        rejects(IllegalArgumentException.class, () -> restored.tick(507));
        rejects(IllegalArgumentException.class, () -> restored.tick(700));
        equal(new Vec3(3, 0, 4), restored.position(b));
        PathSystem invalid = new PathSystem(config, new ThreadGuard(), () -> { });
        Map<String, Object> bad = new LinkedHashMap<>(state); bad.put("arrivals", List.of(b.toString()));
        rejects(ConfigException.class, () -> invalid.restore(1, bad)); invalid.restore(1, state); equal(state, invalid.snapshot());
        Node authored = new Node(Map.of("capacity", 2, "max_speed", 4, "max_advance_ticks", 100,
                "paths", Map.of(route.toString(), Map.of("points", List.of(List.of(0, 0, 0), List.of(3, 0, 0), List.of(3, 0, 4))))), "paths");
        PathSystem.Plan plan = new PathSystem.Plan(); plan.validate(authored);
        Definition definition = new Definition(1, Id.of("test:composed"), "paths-v1", true, 1, 1, Set.of(),
                List.of(new Definition.SystemSpec(PathSystem.ID, authored)), Map.of("arena", new Node(Map.of(), "arena")));
        ArenaRuntime arenas = new ArenaRuntime();
        GenericSession session = new GenericSession(UUID.randomUUID(), definition, "arena",
                List.of(new Participant(UUID.randomUUID(), Participant.Kind.PLAYER, "team")), arenas, new ThreadGuard());
        session.start(new Registry<>(Map.of(PathSystem.ID, plan)));
        PathAccess access = session.services().require(PathSystem.ACCESS); access.spawn(a, route, 1);
        session.tick(1); session.tick(8); equal(List.of(a), access.drainArrivals(1));
        equal(1, session.snapshot().size()); session.close(); equal(0, arenas.snapshot().size());
        first.close(); restored.close(); invalid.close();
        rejects(IllegalStateException.class, first::start);
        rejects(IllegalStateException.class, () -> first.spawn(UUID.randomUUID(), route, 1));
        System.out.println("PathChecks: PASS (arc length, deterministic progress, pause/speed, duplicate/capacity, arrivals, atomic restore, tick bounds)");
    }
}
