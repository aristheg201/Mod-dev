package vn.svframe.lively.social;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.svframe.lively.actor.ActorId;
import vn.svframe.lively.api.LivelyApi;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class RumorPropagationTest {
    private RumorPropagationBootstrap propagation;

    @BeforeEach
    void setUp() {
        LivelyApi.resetServerSessionState();
        propagation = new RumorPropagationBootstrap();
    }

    @AfterEach
    void tearDown() {
        LivelyApi.resetServerSessionState();
    }

    @Test
    void rumorMovesAcrossExistingRelationshipGraphOverMultiplePulses() {
        ActorId a = npc(1L), b = npc(2L), c = npc(3L), stranger = npc(4L);
        LivelyApi.social().apply(a, b, friendly("ab"));
        LivelyApi.social().apply(b, c, friendly("bc"));
        SocialEngine.Rumor rumor = LivelyApi.social().createRumor("market", a, a,
                "Nguồn hàng ở bến cảng đang cạn.", .95D, .8D, Duration.ofHours(1));

        assertEquals(1, propagation.pulse());
        SocialEngine.Rumor afterOne = LivelyApi.social().snapshot().rumors().get(rumor.id());
        assertTrue(afterOne.carriers().contains(b));
        assertFalse(afterOne.carriers().contains(c));
        assertFalse(afterOne.carriers().contains(stranger));
        double firstConfidence = afterOne.confidence();

        assertEquals(1, propagation.pulse());
        SocialEngine.Rumor afterTwo = LivelyApi.social().snapshot().rumors().get(rumor.id());
        assertTrue(afterTwo.carriers().contains(c));
        assertFalse(afterTwo.carriers().contains(stranger));
        assertTrue(afterTwo.confidence() < firstConfidence);
        assertTrue(LivelyApi.social().findRelationship(a, stranger).isEmpty(), "gossip must not invent relationships");
    }

    @Test
    void enemyRelationshipDoesNotCarryRoutineGossip() {
        ActorId a = npc(10L), b = npc(11L);
        LivelyApi.social().apply(a, b, new SocialEngine.SocialDelta(-.9D, -.9D, 0D, .9D, 0D, 0D, .4D,
                "enemy", Map.of()));
        SocialEngine.Rumor rumor = LivelyApi.social().createRumor("secret", a, a,
                "Không nên tới kho phía bắc.", .9D, .9D, Duration.ofHours(1));

        assertEquals(0, propagation.pulse());
        assertEquals(java.util.Set.of(a), LivelyApi.social().snapshot().rumors().get(rumor.id()).carriers());
    }

    private static SocialEngine.SocialDelta friendly(String reason) {
        return new SocialEngine.SocialDelta(.75D, .55D, .2D, 0D, .2D, 0D, .65D, reason, Map.of());
    }

    private static ActorId npc(long value) {
        return new ActorId(new UUID(80L, value), ActorId.Kind.NPC);
    }
}
