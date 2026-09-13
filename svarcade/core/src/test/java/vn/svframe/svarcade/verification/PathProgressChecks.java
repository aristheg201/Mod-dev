package vn.svframe.svarcade.verification;

import java.util.*;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.runtime.*;
import vn.svframe.svarcade.systems.path.*;

public final class PathProgressChecks {
    private PathProgressChecks() { }
    public static void main(String[] args) {
        Id route = Id.of("test:lane"); Node config = new Node(Map.of("capacity", 8, "max_speed", 10, "max_advance_ticks", 100,
                "paths", Map.of(route.toString(), Map.of("points", List.of(List.of(0,0,0), List.of(10,0,0))))), "path");
        PathSystem.Plan plan = new PathSystem.Plan(); SystemCatalog catalog = SystemCatalog.builder().add(PathSystem.ID, plan).build();
        Definition definition = new Definition(1, Id.of("test:path_game"), "fingerprint", true, 1, 1, Set.of(),
                List.of(new Definition.SystemSpec(PathSystem.ID, config)), Map.of("arena", new Node(Map.of("id", "arena"), "arena")));
        ThreadGuard thread = new ThreadGuard(); GenericSession session = new GenericSession(UUID.randomUUID(), definition, "arena",
                List.of(new Participant(UUID.randomUUID(), Participant.Kind.PLAYER, "one")), new ArenaRuntime(), thread); session.start(catalog.factories());
        PathAccess path = session.services().require(PathSystem.ACCESS); UUID id = UUID.randomUUID(); path.spawn(id, route, 2);
        Checks.equal(0.0, path.progress(id)); session.tick(1); session.tick(3); Checks.equal(0.4, path.progress(id)); session.tick(6); Checks.equal(1.0, path.progress(id));
        session.close(); System.out.println("PathProgressChecks passed");
    }
}
