package vn.svframe.lively;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.svframe.lively.actor.ActorId;
import vn.svframe.lively.api.LivelyApi;
import vn.svframe.lively.crime.CrimeEngine;
import vn.svframe.lively.simulation.CausalSimulationService;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class InvestigationProgressionTest {
    private CausalSimulationService simulation;

    @BeforeEach
    void setUp() {
        LivelyApi.resetServerSessionState();
        simulation = new CausalSimulationService();
    }

    @AfterEach
    void tearDown() {
        simulation.close();
        LivelyApi.resetServerSessionState();
    }

    @Test
    void weakWrongChargeIsOverturnedInsteadOfStickingForever() {
        ActorId wrong = actor(1L);
        ActorId real = actor(2L);
        addActor(wrong, "Wrong Suspect");
        addActor(real, "Real Suspect");
        UUID crimeId = new UUID(40L, 1L);
        CrimeEngine.Crime charged = new CrimeEngine.Crime(crimeId, CrimeEngine.Type.THEFT, null, real, null,
                Instant.now().minus(2, ChronoUnit.HOURS), CrimeEngine.Status.CHARGED, "opportunity", Set.of(),
                Map.of("charged_suspect", wrong.uuid().toString()), 1L);
        LivelyApi.crime().restore(new CrimeEngine.Snapshot(1L, Map.of(crimeId, charged), Map.of()));

        simulation.tick(1200L);

        CrimeEngine.Crime after = LivelyApi.crime().crime(crimeId).orElseThrow();
        assertEquals(CrimeEngine.Status.INVESTIGATING, after.status());
        assertEquals("1", after.facts().get("wrong_charge_count"));
        assertEquals(wrong.uuid().toString(), after.facts().get("last_wrong_charge"));
        assertEquals("", after.facts().get("charged_suspect"));
    }

    @Test
    void staleUnsolvedInvestigationBecomesCold() {
        ActorId suspect = actor(3L);
        addActor(suspect, "Suspect");
        UUID crimeId = new UUID(40L, 2L);
        CrimeEngine.Crime open = new CrimeEngine.Crime(crimeId, CrimeEngine.Type.MISSING_PERSON, null, null, null,
                Instant.now().minus(25, ChronoUnit.HOURS), CrimeEngine.Status.INVESTIGATING, "", Set.of(), Map.of(), 1L);
        LivelyApi.crime().restore(new CrimeEngine.Snapshot(1L, Map.of(crimeId, open), Map.of()));

        simulation.tick(1200L);

        CrimeEngine.Crime after = LivelyApi.crime().crime(crimeId).orElseThrow();
        assertEquals(CrimeEngine.Status.COLD, after.status());
        assertEquals("insufficient_evidence", after.facts().get("cold_reason"));
        assertEquals("0", after.facts().get("cold_evidence_count"));
    }

    @Test
    void coldCaseReopensWhenNewEvidenceAppears() {
        ActorId suspect = actor(4L);
        addActor(suspect, "Suspect");
        UUID crimeId = new UUID(40L, 3L);
        CrimeEngine.Crime cold = new CrimeEngine.Crime(crimeId, CrimeEngine.Type.FRAUD, null, suspect, null,
                Instant.now().minus(3, ChronoUnit.DAYS), CrimeEngine.Status.COLD, "money", Set.of(),
                Map.of("cold_evidence_count", "0"), 1L);
        LivelyApi.crime().restore(new CrimeEngine.Snapshot(1L, Map.of(crimeId, cold), Map.of()));
        LivelyApi.crime().addEvidence(crimeId, CrimeEngine.EvidenceType.RECORD, null, suspect,
                .9D, .9D, false, Map.of("new", "true"));

        simulation.tick(1200L);

        CrimeEngine.Crime after = LivelyApi.crime().crime(crimeId).orElseThrow();
        assertEquals(CrimeEngine.Status.INVESTIGATING, after.status());
        assertEquals("new_evidence", after.facts().get("reopened_reason"));
        assertNotNull(after.facts().get("reopened_at"));
    }

    private static ActorId actor(long value) {
        return new ActorId(new UUID(41L, value), ActorId.Kind.NPC);
    }

    private static void addActor(ActorId actor, String name) {
        LivelyApi.actors().upsert(actor, name, Map.of("ambition", .5D, "morality", .5D, "fear", .5D), Map.of(), Set.of("npc"));
    }
}
