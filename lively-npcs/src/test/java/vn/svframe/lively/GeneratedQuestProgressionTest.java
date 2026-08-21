package vn.svframe.lively;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.svframe.lively.actor.ActorId;
import vn.svframe.lively.api.LivelyApi;
import vn.svframe.lively.event.LivingWorldDirectorService;
import vn.svframe.lively.event.WorldEventEngine;
import vn.svframe.lively.quest.QuestRuntime;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class GeneratedQuestProgressionTest {
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
    void socialWorldEventProducesQuestThatCompletesFromIssuerInteractionSignal() {
        ActorId issuer = new ActorId(new UUID(70L, 1L), ActorId.Kind.NPC);
        WorldEventEngine.WorldEvent event = LivelyApi.events().start(new WorldEventEngine.EventProposal(
                WorldEventEngine.Category.SOCIAL, "town_argument", null, Set.of(issuer), .60D,
                Duration.ofHours(1), Map.of())).orElseThrow();
        QuestRuntime.Quest offer = offerFor(event.id());
        QuestRuntime.Objective main = offer.objectives().getFirst();
        assertEquals(QuestRuntime.ObjectiveType.SOCIAL, main.type());
        assertEquals(issuer.uuid().toString(), main.target());

        ActorId player = new ActorId(new UUID(70L, 2L), ActorId.Kind.PLAYER);
        assertTrue(LivelyApi.quests().claim(offer.id(), player).isPresent());
        assertEquals(1, LivelyApi.quests().signal(player, QuestRuntime.ObjectiveType.SOCIAL,
                issuer.uuid().toString(), 1L, Map.of("npc", issuer.uuid().toString())));
        assertEquals(QuestRuntime.Status.COMPLETED, LivelyApi.quests().snapshot().quests().get(offer.id()).status());
    }

    @Test
    void mysteryWorldEventProducesEventAddressableInvestigationQuest() {
        WorldEventEngine.WorldEvent event = LivelyApi.events().start(new WorldEventEngine.EventProposal(
                WorldEventEngine.Category.MYSTERY, "missing_records", null, Set.of(), .55D,
                Duration.ofHours(1), Map.of())).orElseThrow();
        QuestRuntime.Quest offer = offerFor(event.id());
        QuestRuntime.Objective main = offer.objectives().getFirst();
        assertEquals(QuestRuntime.ObjectiveType.INVESTIGATION, main.type());
        assertEquals(event.id().toString(), main.target());

        ActorId player = new ActorId(new UUID(71L, 1L), ActorId.Kind.PLAYER);
        LivelyApi.quests().claim(offer.id(), player).orElseThrow();
        assertEquals(1, LivelyApi.quests().signal(player, QuestRuntime.ObjectiveType.INVESTIGATION,
                new UUID(71L, 9L).toString(), 1L,
                Map.of("event", event.id().toString(), "crime", new UUID(71L, 8L).toString())));
        assertEquals(QuestRuntime.Status.COMPLETED, LivelyApi.quests().snapshot().quests().get(offer.id()).status());
    }

    @Test
    void economicWorldEventCarriesSemanticDeliveryCoordinates() {
        WorldEventEngine.WorldEvent event = LivelyApi.events().start(new WorldEventEngine.EventProposal(
                WorldEventEngine.Category.ECONOMIC, "supply_shortage", null, Set.of(), .70D,
                Duration.ofHours(1), Map.of("world", "minecraft:overworld", "x", "12", "y", "64", "z", "-8", "radius", "5")))
                .orElseThrow();
        QuestRuntime.Objective main = offerFor(event.id()).objectives().getFirst();
        assertEquals(QuestRuntime.ObjectiveType.DELIVERY, main.type());
        assertEquals("true", main.facts().get("semantic_delivery"));
        assertEquals("minecraft:overworld", main.facts().get("world"));
        assertEquals("12", main.facts().get("x"));
        assertEquals("-8", main.facts().get("z"));
    }

    private QuestRuntime.Quest offerFor(UUID eventId) {
        return LivelyApi.quests().publicOffers().stream()
                .filter(q -> eventId.toString().equals(q.facts().get("event")))
                .findFirst().orElseThrow();
    }
}
