package vn.svframe.svarcade.verification;

import java.util.*;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.runtime.*;
import vn.svframe.svarcade.systems.wave.*;

public final class WaveChecks {
    private WaveChecks() { }

    public static void main(String[] args) {
        Id enemy = Id.of("test:slime"), lane = Id.of("test:lane_a"), waveId = Id.of("test:wave_1");
        Node config = new Node(Map.of("repeat_from", -1, "max_spawns_per_tick", 4, "max_pending_spawns", 8,
                "waves", List.of(Map.of("id", waveId.toString(), "groups", List.of(Map.of("enemy", enemy.toString(), "count", 2,
                        "interval_ticks", 2, "lane", lane.toString(), "modifiers", List.of("test:fast"), "tags", List.of("test:elite"))),
                        "clear_reward", Map.of("currency", "test:coins", "amount", 50), "shop", Map.of("open", true))))), "waves");
        WaveSystem.Plan plan = new WaveSystem.Plan(); plan.validate(config); SystemCatalog catalog = SystemCatalog.builder().add(WaveSystem.ID, plan).build();
        Definition definition = new Definition(1, Id.of("test:wave_game"), "fingerprint", true, 1, 1, Set.of(),
                List.of(new Definition.SystemSpec(WaveSystem.ID, config)), Map.of("arena", new Node(Map.of("id", "arena"), "arena")));
        Participant player = new Participant(UUID.randomUUID(), Participant.Kind.PLAYER, "one"); ThreadGuard thread = new ThreadGuard();
        GenericSession session = new GenericSession(UUID.randomUUID(), definition, "arena", List.of(player), new ArenaRuntime(), thread); session.start(catalog.factories());
        WaveAccess waves = session.services().require(WaveAccess.ACCESS); waves.prepareStartNext().apply(); Checks.equal(OptionalInt.of(0), waves.activeWaveIndex());
        session.tick(100); WaveAccess.Spawn first = waves.pendingSpawns(8).getFirst(); Checks.equal(enemy, first.enemy()); waves.prepareSpawned(first.sequence()).apply();
        session.tick(101); Checks.equal(List.of(), waves.pendingSpawns(8)); session.tick(102); WaveAccess.Spawn second = waves.pendingSpawns(8).getFirst();
        Map<Id, GenericSession.SystemState> saved = session.snapshot(); session.close();

        GenericSession restored = new GenericSession(UUID.randomUUID(), definition, "arena", List.of(player), new ArenaRuntime(), thread); restored.restore(catalog.factories(), saved);
        WaveAccess recovered = restored.services().require(WaveAccess.ACCESS); Checks.equal(1, recovered.outstandingSpawns().size()); Checks.equal(1, recovered.pendingSpawns(8).size());
        recovered.prepareSpawned(second.sequence()).apply(); recovered.prepareResolved(first.sequence()).apply(); Checks.equal(Optional.empty(), recovered.clearEvent());
        recovered.prepareResolved(second.sequence()).apply(); WaveAccess.Clear clear = recovered.clearEvent().orElseThrow(); Checks.equal(waveId, clear.wave());
        Checks.equal(50, clear.reward().get("amount")); Checks.equal(true, clear.shop().get("open")); recovered.prepareClearAcknowledged().apply();
        Checks.equal(true, recovered.complete()); Checks.equal(OptionalInt.empty(), recovered.activeWaveIndex()); restored.close();
        System.out.println("WaveChecks passed");
    }
}
