package vn.svframe.lively;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.svframe.lively.api.LivelyApi;
import vn.svframe.lively.event.LivingWorldDirectorService;
import vn.svframe.lively.event.StoryArcEngine;
import vn.svframe.lively.event.WorldEventEngine;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

final class StoryArcLifecycleTest {
    private LivingWorldDirectorService director;

    @BeforeEach
    void setUp() {
        LivelyApi.resetServerSessionState();
        director = new LivingWorldDirectorService();
    }

    @AfterEach
    void tearDown() {
        director.close();
        LivelyApi.resetServerSessionState();
    }

    @Test
    void completedEventsAdvanceAndEventuallyResolveArc() {
        StoryArcEngine.Arc arc = null;
        for (int i = 0; i < 4; i++) {
            WorldEventEngine.WorldEvent event = LivelyApi.events().start(new WorldEventEngine.EventProposal(
                    WorldEventEngine.Category.MYSTERY, "arc_lifecycle", null, Set.of(), .55D,
                    Duration.ofHours(1), Map.of())).orElseThrow();
            arc = LivelyApi.storyArcs().active().stream().filter(value -> value.seed().equals("arc_lifecycle")).findFirst().orElseThrow();
            assertTrue(arc.events().contains(event.id()));
            LivelyApi.events().finish(event.id()).orElseThrow();
        }
        StoryArcEngine.Arc resolved = LivelyApi.storyArcs().snapshot().values().stream()
                .filter(value -> value.seed().equals("arc_lifecycle")).findFirst().orElseThrow();
        assertEquals(5, resolved.phase());
        assertEquals(StoryArcEngine.State.RESOLVED, resolved.state());
    }

    @Test
    void cancelledEventAbandonsOwningArc() {
        WorldEventEngine.WorldEvent event = LivelyApi.events().start(new WorldEventEngine.EventProposal(
                WorldEventEngine.Category.POLITICAL, "cancelled_arc", null, Set.of(), .50D,
                Duration.ofHours(1), Map.of())).orElseThrow();
        StoryArcEngine.Arc arc = LivelyApi.storyArcs().active().stream().filter(value -> value.seed().equals("cancelled_arc")).findFirst().orElseThrow();
        LivelyApi.events().cancel(event.id()).orElseThrow();
        assertEquals(StoryArcEngine.State.ABANDONED, LivelyApi.storyArcs().get(arc.id()).orElseThrow().state());
    }

    @Test
    void attachingEventDoesNotSecretlySkipPhases() {
        StoryArcEngine engine = new StoryArcEngine();
        StoryArcEngine.Arc arc = engine.start("seed", "title", 5, Map.of());
        engine.attachEvent(arc.id(), java.util.UUID.randomUUID(), .90D);
        assertEquals(1, engine.get(arc.id()).orElseThrow().phase());
        assertEquals(2, engine.advance(arc.id(), 0D).orElseThrow().phase());
    }
}
