package vn.svframe.lively.faction;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.svframe.lively.actor.ActorId;
import vn.svframe.lively.api.LivelyApi;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class FactionActionTest {
    private FactionActionBootstrap actions;

    @BeforeEach
    void setUp() {
        LivelyApi.resetServerSessionState();
        actions = new FactionActionBootstrap();
    }

    @AfterEach
    void tearDown() {
        LivelyApi.resetServerSessionState();
    }

    @Test
    void recruitStrategyAddsUnassignedNpcAndUpdatesActorFacts() {
        ActorId leader = npc(1L);
        ActorId recruit = npc(2L);
        LivelyApi.actors().upsert(leader, "Leader", Map.of("ambition", .8D), Map.of(), Set.of("npc"));
        LivelyApi.actors().upsert(recruit, "Recruit", Map.of("ambition", .9D, "loyal", .8D), Map.of(), Set.of("npc"));
        FactionEngine.Faction faction = LivelyApi.factions().create("Wardens", Set.of(leader), Map.of(), Map.of("expansion", 1D));
        LivelyApi.factions().updateKnowledge(faction.id(), "current_strategy", "recruit");

        assertEquals(1, actions.pulse(1_000_000L));
        FactionEngine.Faction after = LivelyApi.factions().get(faction.id()).orElseThrow();
        assertTrue(after.members().contains(recruit));
        assertEquals(faction.id().toString(), LivelyApi.actors().get(recruit).orElseThrow().facts().get("faction"));
        assertEquals(recruit.uuid().toString(), after.knowledge().get("last_strategy_result"));
        assertEquals("1000000", after.knowledge().get("last_strategy_epoch_ms"));
    }

    @Test
    void patrolAddsSecurityButCooldownUsesWallClockNotServerTicks() {
        ActorId leader = npc(10L);
        FactionEngine.Faction faction = LivelyApi.factions().create("Guard", Set.of(leader), Map.of(), Map.of("stability", 1D));
        LivelyApi.factions().updateKnowledge(faction.id(), "current_strategy", "increase_patrol");
        long start = 2_000_000L;

        assertEquals(1, actions.pulse(start));
        long security = LivelyApi.factions().get(faction.id()).orElseThrow().resources().getOrDefault("security", 0L);
        assertTrue(security > 0L);
        assertEquals(0, actions.pulse(start + 60_000L));
        assertEquals(security, LivelyApi.factions().get(faction.id()).orElseThrow().resources().get("security"));
        assertEquals(1, actions.pulse(start + 300_000L));
        assertTrue(LivelyApi.factions().get(faction.id()).orElseThrow().resources().get("security") > security);
    }

    @Test
    void persistedEpochDoesNotFreezeFactionWhenMinecraftTickCounterResets() {
        ActorId leader = npc(20L);
        FactionEngine.Faction faction = LivelyApi.factions().create("Restarted", Set.of(leader), Map.of(), Map.of());
        LivelyApi.factions().updateKnowledge(faction.id(), "current_strategy", "secure_supply");
        LivelyApi.factions().updateKnowledge(faction.id(), "last_strategy_epoch_ms", "1000000");
        assertEquals(1, actions.pulse(1_400_000L));
        assertTrue(LivelyApi.factions().get(faction.id()).orElseThrow().resources().getOrDefault("supplies", 0L) > 0L);
    }

    private static ActorId npc(long value) {
        return new ActorId(new UUID(90L, value), ActorId.Kind.NPC);
    }
}
