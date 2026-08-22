package vn.svframe.lively;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import vn.svframe.lively.actor.ActorId;
import vn.svframe.lively.api.LivelyApi;
import vn.svframe.lively.event.StorySeedEngine;
import vn.svframe.lively.event.WorldEventEngine;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class ServerSessionIsolationTest {
    @AfterEach void clean() { LivelyApi.resetServerSessionState(); }

    @Test
    void resetClearsEveryWorldScopedDomain() {
        ActorId a = new ActorId(new UUID(100L, 1L), ActorId.Kind.NPC);
        ActorId b = new ActorId(new UUID(100L, 2L), ActorId.Kind.NPC);
        LivelyApi.actors().upsert(a, "A", Map.of(), Map.of(), Set.of("npc"));
        LivelyApi.actors().upsert(b, "B", Map.of(), Map.of(), Set.of("npc"));
        LivelyApi.social().apply(a, b, new vn.svframe.lively.social.SocialEngine.SocialDelta(.2D, .2D, 0D, 0D, 0D, 0D, .2D, "test", Map.of()));
        LivelyApi.economy().ensureWallet(a, 500L);
        LivelyApi.storySeeds().register(new StorySeedEngine.Seed("test_seed", WorldEventEngine.Category.SOCIAL, .5D, true, Map.of()));
        LivelyApi.profiler().record("test", 10L, false);

        LivelyApi.resetServerSessionState();

        assertTrue(LivelyApi.actors().snapshot().actors().isEmpty());
        assertTrue(LivelyApi.social().snapshot().relationships().isEmpty());
        assertTrue(LivelyApi.social().snapshot().rumors().isEmpty());
        assertTrue(LivelyApi.economy().snapshot().wallets().isEmpty());
        assertTrue(LivelyApi.crime().snapshot().crimes().isEmpty());
        assertTrue(LivelyApi.factions().snapshot().factions().isEmpty());
        assertTrue(LivelyApi.quests().snapshot().quests().isEmpty());
        assertTrue(LivelyApi.schedules().snapshot().schedules().isEmpty());
        assertTrue(LivelyApi.structures().snapshot().structures().isEmpty());
        assertTrue(LivelyApi.storySeeds().snapshot().isEmpty());
        assertTrue(LivelyApi.storyArcs().snapshot().isEmpty());
        assertTrue(LivelyApi.events().activeEvents().isEmpty());
        assertTrue(LivelyApi.profiler().snapshot().isEmpty());
        assertNull(LivelyApi.states());
        assertNull(LivelyApi.npcs());
        assertNull(LivelyApi.worldNavigation());
        assertNull(LivelyApi.dialogues());
    }
}
