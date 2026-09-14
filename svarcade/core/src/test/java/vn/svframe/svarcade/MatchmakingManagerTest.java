package vn.svframe.svarcade;

import java.util.*;
import org.junit.jupiter.api.Test;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.matchmaking.*;
import vn.svframe.svarcade.runtime.*;
import static org.junit.jupiter.api.Assertions.*;

class MatchmakingManagerTest {
    @Test void queueUsesDefinitionLimitsFreeArenaAndConfiguredBotFill() {
        ThreadGuard thread = new ThreadGuard(); Id game = Id.of("test:game"), queue = Id.of("test:solo_bot");
        Definition definition = new Definition(1, game, "fp", true, 1, 2, Set.of(), List.of(),
                Map.of("one", new Node(Map.of("id", "one"), "arena"), "two", new Node(Map.of("id", "two"), "arena")));
        DefinitionRegistry definitions = new DefinitionRegistry(); assertTrue(definitions.reload(() -> List.of(definition), Set.of(), Runnable::run).join().applied());
        GenericGameRuntime runtime = new GenericGameRuntime(thread, definitions, new Registry<>(Map.of()), 8);
        MatchmakingManager matcher = new MatchmakingManager(thread, definitions, runtime,
                List.of(new QueueDefinition(queue, game, 2, true, "bot")),
                (definitionId, team, ordinal) -> new Participant(UUID.nameUUIDFromBytes((definitionId + "/" + ordinal).getBytes()), Participant.Kind.BOT, team), 16);
        UUID player = UUID.randomUUID(); matcher.enqueue(queue, new QueueParty(UUID.randomUUID(), List.of(new Participant(player, Participant.Kind.PLAYER, "human"))));
        assertEquals(1, matcher.tick(4)); assertEquals(1, runtime.sessions().size()); MatchmakingManager.Match made = matcher.drainMatches(4).getFirst();
        assertEquals(1, made.players()); assertEquals(1, made.bots()); GenericSession session = runtime.sessions().get(made.session()); assertNotNull(session);
        assertEquals(2, session.participants().size()); runtime.close(session.id());
    }
}
