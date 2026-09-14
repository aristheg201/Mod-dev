package vn.svframe.svarcade;

import java.util.*;
import org.junit.jupiter.api.Test;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.runtime.*;
import vn.svframe.svarcade.systems.spectator.*;
import static org.junit.jupiter.api.Assertions.*;

class SpectatorSystemTest {
    @Test void dynamicSpectatorsRecoverWithoutChangingFixedRoster() {
        ThreadGuard thread = new ThreadGuard(); UUID player = UUID.randomUUID(), viewer = UUID.randomUUID(), sessionId = UUID.randomUUID();
        Definition definition = new Definition(1, Id.of("test:spectate"), "fp", true, 1, 1, Set.of(),
                List.of(new Definition.SystemSpec(SpectatorSystem.ID,
                        new Node(Map.of("enabled", true, "max_spectators", 2, "team", "viewers", "event_capacity", 8), "spectators"))),
                Map.of("arena", new Node(Map.of("id", "arena"), "arena")));
        SystemCatalog catalog = SystemCatalog.builder().add(SpectatorSystem.ID, new SpectatorSystem.Plan()).build();
        Participant owner = new Participant(player, Participant.Kind.PLAYER, "one");
        GenericSession first = new GenericSession(sessionId, definition, "arena", List.of(owner), new ArenaRuntime(), thread);
        first.start(catalog.factories()); SpectatorAccess access = first.services().require(SpectatorAccess.ACCESS);
        access.prepareJoin(viewer).apply(); assertTrue(access.contains(viewer)); assertEquals(Participant.Kind.SPECTATOR, first.participants().get(viewer).kind());
        assertTrue(first.fixedParticipant(player)); assertFalse(first.fixedParticipant(viewer));
        Map<Id, GenericSession.SystemState> saved = first.snapshot(); first.close();

        GenericSession restored = new GenericSession(sessionId, definition, "arena", List.of(owner), new ArenaRuntime(), thread);
        restored.restore(catalog.factories(), saved); SpectatorAccess recovered = restored.services().require(SpectatorAccess.ACCESS);
        assertTrue(recovered.contains(viewer)); assertEquals(Participant.Kind.SPECTATOR, restored.participants().get(viewer).kind());
        recovered.prepareLeave(viewer).apply(); assertFalse(restored.participants().containsKey(viewer)); restored.close();
    }
}
