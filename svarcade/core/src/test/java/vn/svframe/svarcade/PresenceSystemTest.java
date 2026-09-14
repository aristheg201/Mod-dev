package vn.svframe.svarcade;

import java.util.*;
import org.junit.jupiter.api.Test;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.runtime.*;
import vn.svframe.svarcade.systems.presence.*;
import static org.junit.jupiter.api.Assertions.*;

class PresenceSystemTest {
    @Test void disconnectTimeoutAndRecoveryAreDefinitionDriven() {
        ThreadGuard thread = new ThreadGuard(); UUID player = UUID.randomUUID(), sessionId = UUID.randomUUID();
        Definition definition = new Definition(1, Id.of("test:presence"), "fp", true, 1, 1, Set.of(),
                List.of(new Definition.SystemSpec(PresenceSystem.ID, new Node(Map.of(
                        "player", Map.of("grace_ticks", 20, "timeout_policy", "FORFEIT"),
                        "bot", Map.of("grace_ticks", 0, "timeout_policy", "RETAIN"), "event_capacity", 8), "presence"))),
                Map.of("arena", new Node(Map.of("id", "arena"), "arena")));
        SystemCatalog catalog = SystemCatalog.builder().add(PresenceSystem.ID, new PresenceSystem.Plan()).build();
        Participant participant = new Participant(player, Participant.Kind.PLAYER, "one");
        GenericSession first = new GenericSession(sessionId, definition, "arena", List.of(participant), new ArenaRuntime(), thread);
        first.start(catalog.factories()); PresenceAccess presence = first.services().require(PresenceAccess.ACCESS);
        presence.disconnect(player, 100); assertFalse(presence.status(player).connected()); assertEquals(120, presence.status(player).deadline());
        first.tick(110); assertTrue(presence.drainTimeouts(4).isEmpty()); Map<Id, GenericSession.SystemState> saved = first.snapshot(); first.close();

        GenericSession restored = new GenericSession(sessionId, definition, "arena", List.of(participant), new ArenaRuntime(), thread);
        restored.restore(catalog.factories(), saved); PresenceAccess recovered = restored.services().require(PresenceAccess.ACCESS);
        restored.tick(120); var event = recovered.drainTimeouts(4).getFirst(); assertEquals(player, event.participant()); assertEquals(PresenceAccess.TimeoutPolicy.FORFEIT, event.policy());
        recovered.reconnect(player, 121); assertTrue(recovered.status(player).connected()); restored.close();
    }
}
