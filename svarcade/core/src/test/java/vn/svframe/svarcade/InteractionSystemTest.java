package vn.svframe.svarcade;

import java.util.*;
import org.junit.jupiter.api.Test;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.runtime.*;
import vn.svframe.svarcade.security.*;
import vn.svframe.svarcade.systems.interaction.*;
import static org.junit.jupiter.api.Assertions.*;

class InteractionSystemTest {
    @Test void bindingsCanOnlyReferenceRegisteredAuthoritativeActionsAndRecover() {
        ThreadGuard thread = new ThreadGuard(); Id action = Id.of("test:click"), handler = Id.of("test:handler"); UUID object = UUID.randomUUID(), player = UUID.randomUUID(), sessionId = UUID.randomUUID();
        ActionHandlerFactory factory = new ActionHandlerFactory() {
            @Override public void validate(Node config) { config.only(); }
            @Override public ActionDispatcher.Handler create(GenericSession session, Node config) {
                return new ActionDispatcher.Handler() {
                    @Override public Optional<String> reject(GenericSession s, IntentGate.Facts facts, IntentGate.Intent intent) { return Optional.empty(); }
                    @Override public ActionDispatcher.Prepared prepare(GenericSession s, IntentGate.Facts facts, IntentGate.Intent intent) { return new ActionDispatcher.Prepared(() -> {}, () -> {}, List.of()); }
                };
            }
        };
        Registry<ActionHandlerFactory> handlers = new Registry.Builder<ActionHandlerFactory>().add(handler, factory).build();
        Node actions = new Node(Map.of("rate_limit", Map.of("max_keys", 16, "capacity", 4, "per_tick", 1), "max_distance", 16, "event_capacity", 16,
                "actions", Map.of(action.toString(), Map.of("handler", handler.toString(), "config", Map.of()))), "actions");
        Node interactions = new Node(Map.of("capacity", 16, "coordinate_limit", 1000, "max_range", 16), "interactions");
        Definition definition = new Definition(1, Id.of("test:interaction"), "fp", true, 1, 1, Set.of(),
                List.of(new Definition.SystemSpec(ActionSystem.ID, actions), new Definition.SystemSpec(InteractionSystem.ID, interactions)), Map.of("arena", new Node(Map.of("id", "arena"), "arena")));
        SystemCatalog catalog = SystemCatalog.builder().add(ActionSystem.ID, new ActionSystem.Plan(handlers)).add(InteractionSystem.ID, new InteractionSystem.Plan()).build(); Participant participant = new Participant(player, Participant.Kind.PLAYER, "one");
        GenericSession first = new GenericSession(sessionId, definition, "arena", List.of(participant), new ArenaRuntime(), thread); first.start(catalog.factories()); InteractionAccess access = first.services().require(InteractionAccess.ACCESS);
        access.prepareBind(object, action, player, new InteractionAccess.Point(1,2,3), 5, Set.of(Id.of("test:board"))).apply(); assertEquals(action, access.binding(object).orElseThrow().action());
        assertThrows(IllegalArgumentException.class, () -> access.prepareBind(UUID.randomUUID(), Id.of("test:missing"), null, new InteractionAccess.Point(0,0,0), 1, Set.of()));
        Map<Id, GenericSession.SystemState> saved = first.snapshot(); first.close(); GenericSession restored = new GenericSession(sessionId, definition, "arena", List.of(participant), new ArenaRuntime(), thread); restored.restore(catalog.factories(), saved);
        assertEquals(action, restored.services().require(InteractionAccess.ACCESS).binding(object).orElseThrow().action()); restored.close();
    }
}
