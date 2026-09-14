package vn.svframe.svarcade;

import java.util.*;
import org.junit.jupiter.api.Test;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.runtime.*;
import vn.svframe.svarcade.systems.render.*;
import static org.junit.jupiter.api.Assertions.*;

class RendererSystemTest {
    @Test void renderObjectsAreVersionedBoundedAndRecoverable() {
        ThreadGuard thread = new ThreadGuard(); Id profile = Id.of("test:pokemon"); UUID object = UUID.randomUUID(), player = UUID.randomUUID(), sessionId = UUID.randomUUID();
        Node config = new Node(Map.of("capacity", 8, "event_capacity", 8, "profiles", List.of(profile.toString())), "renderer");
        Definition definition = new Definition(1, Id.of("test:render"), "fp", true, 1, 1, Set.of(), List.of(new Definition.SystemSpec(RendererSystem.ID, config)), Map.of("arena", new Node(Map.of("id", "arena"), "arena")));
        SystemCatalog catalog = SystemCatalog.builder().add(RendererSystem.ID, new RendererSystem.Plan()).build(); Participant participant = new Participant(player, Participant.Kind.PLAYER, "one");
        GenericSession first = new GenericSession(sessionId, definition, "arena", List.of(participant), new ArenaRuntime(), thread); first.start(catalog.factories()); RendererAccess renderer = first.services().require(RendererAccess.ACCESS);
        renderer.prepareUpsert(object, profile, new RendererAccess.Transform(1,2,3,4,5), Map.of("species", "pikachu")).apply(); assertEquals(1, renderer.objects(8).size()); assertEquals(RendererAccess.EventType.UPSERT, renderer.drainEvents(8).getFirst().type());
        Map<Id, GenericSession.SystemState> saved = first.snapshot(); first.close(); GenericSession restored = new GenericSession(sessionId, definition, "arena", List.of(participant), new ArenaRuntime(), thread); restored.restore(catalog.factories(), saved);
        RendererAccess recovered = restored.services().require(RendererAccess.ACCESS); assertEquals("pikachu", recovered.object(object).orElseThrow().data().get("species")); recovered.prepareRemove(object).apply(); assertTrue(recovered.object(object).isEmpty()); restored.close();
    }
}
