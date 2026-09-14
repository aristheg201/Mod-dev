package vn.svframe.svarcade;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.matchmaking.*;
import vn.svframe.svarcade.runtime.*;
import static org.junit.jupiter.api.Assertions.*;

class MatchmakingManagerTest {
    @Test void queueUsesDefinitionLimitsFreeArenaAndConfiguredBotFill() {
        Fixture fixture = fixture(true); UUID player = UUID.randomUUID(); fixture.matcher.enqueue(fixture.queue,
                new QueueParty(UUID.randomUUID(), List.of(new Participant(player, Participant.Kind.PLAYER, "human"))));
        assertEquals(1, fixture.matcher.tick(4)); assertEquals(1, fixture.runtime.sessions().size()); MatchmakingManager.Match made = fixture.matcher.drainMatches(4).getFirst();
        assertEquals(1, made.players()); assertEquals(1, made.bots()); GenericSession session = fixture.runtime.sessions().get(made.session()); assertNotNull(session);
        assertEquals(2, session.participants().size()); fixture.runtime.close(session.id());
    }

    @Test void directChallengeUsesSameTargetAndArenaAllocator() {
        Fixture fixture = fixture(false); UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        QueueParty left = new QueueParty(UUID.randomUUID(), List.of(new Participant(a, Participant.Kind.PLAYER, "left")));
        QueueParty right = new QueueParty(UUID.randomUUID(), List.of(new Participant(b, Participant.Kind.PLAYER, "right")));
        GenericSession first = fixture.matcher.challenge(fixture.queue, left, right); assertEquals(2, first.participants().size());
        assertEquals(1, fixture.matcher.drainMatches(4).size());
        GenericSession second = fixture.matcher.challenge(fixture.queue,
                new QueueParty(UUID.randomUUID(), List.of(new Participant(UUID.randomUUID(), Participant.Kind.PLAYER, "left"))),
                new QueueParty(UUID.randomUUID(), List.of(new Participant(UUID.randomUUID(), Participant.Kind.PLAYER, "right"))));
        assertNotEquals(first.lease().arena().arena(), second.lease().arena().arena()); fixture.runtime.closeAll();
    }

    private static Fixture fixture(boolean botFill) {
        ThreadGuard thread = new ThreadGuard(); Id game = Id.of("test:game"), queue = Id.of("test:queue");
        Definition definition = new Definition(1, game, "fp", true, 1, 2, Set.of(), List.of(),
                Map.of("one", new Node(Map.of("id", "one"), "arena"), "two", new Node(Map.of("id", "two"), "arena")));
        DefinitionRegistry definitions = new DefinitionRegistry(); assertTrue(definitions.reload(() -> List.of(definition), Set.of(), Runnable::run).join().applied());
        GenericGameRuntime runtime = new GenericGameRuntime(thread, definitions, new Registry<>(Map.of()), 8);
        MatchmakingManager matcher = new MatchmakingManager(thread, definitions, runtime, List.of(new QueueDefinition(queue, game, 2, botFill, "bot")),
                (definitionId, team, ordinal) -> new Participant(UUID.nameUUIDFromBytes((definitionId + "/" + ordinal).getBytes(StandardCharsets.UTF_8)), Participant.Kind.BOT, team), 16);
        return new Fixture(queue, runtime, matcher);
    }
    private record Fixture(Id queue, GenericGameRuntime runtime, MatchmakingManager matcher) { }
}
