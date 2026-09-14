package vn.svframe.svarcade;

import java.util.*;
import org.junit.jupiter.api.Test;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.runtime.*;
import vn.svframe.svarcade.systems.combat.*;
import vn.svframe.svarcade.systems.enemy.*;
import vn.svframe.svarcade.systems.path.*;
import vn.svframe.svarcade.systems.targeting.*;
import vn.svframe.svarcade.systems.wave.*;
import static org.junit.jupiter.api.Assertions.*;

class EnemySystemTest {
    private static final Id ENEMY = Id.of("test:slime"), LANE = Id.of("test:lane"), WAVE = Id.of("test:wave"),
            NORMAL = Id.of("test:normal"), BURN = Id.of("test:burn");

    @Test void spawnRecoverStatusAndResolveThroughRealSession() {
        ThreadGuard thread = new ThreadGuard(); UUID sessionId = UUID.randomUUID(), playerId = UUID.randomUUID();
        Participant player = new Participant(playerId, Participant.Kind.PLAYER, "one");
        SystemCatalog catalog = catalog(); Definition definition = definition();
        GenericSession first = new GenericSession(sessionId, definition, "arena", List.of(player), new ArenaRuntime(), thread);
        first.start(catalog.factories()); WaveAccess waves = first.services().require(WaveAccess.ACCESS); waves.prepareStartNext().apply();
        first.tick(1);
        EnemyAccess enemies = first.services().require(EnemyAccess.ACCESS); assertEquals(1, enemies.size());
        EnemyAccess.View initial = enemies.enemies(8).getFirst(); assertEquals(ENEMY, initial.profile()); assertEquals(0.0, initial.progress());
        CombatAccess combat = first.services().require(CombatAccess.ACCESS);
        CombatAccess.Resolution burn = combat.resolve(new CombatAccess.Attack(NORMAL, 1, 10, 1, 0, 1,
                CombatAccess.Delivery.INSTANT, 0, 0, List.of(new CombatAccess.StatusAttempt(BURN, 1, 1, 1))), enemies.combatTarget(initial.id()), 7);
        enemies.prepareHit(initial.id(), burn).apply(); assertTrue(enemies.enemy(initial.id()).orElseThrow().statuses().contains(BURN));
        Map<Id, GenericSession.SystemState> saved = first.snapshot(); first.close();

        GenericSession restored = new GenericSession(sessionId, definition, "arena", List.of(player), new ArenaRuntime(), thread);
        restored.restore(catalog.factories(), saved); EnemyAccess recovered = restored.services().require(EnemyAccess.ACCESS);
        EnemyAccess.View after = recovered.enemy(initial.id()).orElseThrow(); assertEquals(initial.id(), after.id()); assertTrue(after.statuses().contains(BURN));
        assertEquals(1, restored.services().require(TargetingSystem.ACCESS).size());
        CombatAccess restoredCombat = restored.services().require(CombatAccess.ACCESS);
        CombatAccess.Resolution lethal = restoredCombat.resolve(new CombatAccess.Attack(NORMAL, 1000, 10, 1, 0, 1,
                CombatAccess.Delivery.INSTANT, 0, 0, List.of()), recovered.combatTarget(after.id()), 9);
        recovered.prepareHit(after.id(), lethal).apply(); restored.tick(2);
        assertEquals(0, recovered.size()); assertEquals(1, recovered.drainOutcomes(8).size());
        assertTrue(restored.services().require(WaveAccess.ACCESS).clearEvent().isPresent()); restored.close();
    }

    @Test void restoreRejectsEnemyWithoutOutstandingWaveSpawn() {
        ThreadGuard thread = new ThreadGuard(); UUID sessionId = UUID.randomUUID(), playerId = UUID.randomUUID(); Participant player = new Participant(playerId, Participant.Kind.PLAYER, "one");
        SystemCatalog catalog = catalog(); Definition definition = definition(); GenericSession session = new GenericSession(sessionId, definition, "arena", List.of(player), new ArenaRuntime(), thread);
        session.start(catalog.factories()); session.services().require(WaveAccess.ACCESS).prepareStartNext().apply(); session.tick(1); Map<Id, GenericSession.SystemState> saved = new LinkedHashMap<>(session.snapshot()); session.close();
        GenericSession.SystemState wave = saved.get(WaveSystem.ID); Map<String,Object> brokenWave = new LinkedHashMap<>(wave.data()); brokenWave.put("outstanding", List.of()); saved.put(WaveSystem.ID, new GenericSession.SystemState(wave.schema(), brokenWave));
        GenericSession restored = new GenericSession(sessionId, definition, "arena", List.of(player), new ArenaRuntime(), thread);
        assertThrows(ConfigException.class, () -> restored.restore(catalog.factories(), saved)); restored.close();
    }

    private static SystemCatalog catalog() {
        return SystemCatalog.builder().add(PathSystem.ID, new PathSystem.Plan()).add(TargetingSystem.ID, new TargetingSystem.Plan())
                .add(CombatSystem.ID, new CombatSystem.Plan()).add(WaveSystem.ID, new WaveSystem.Plan()).add(EnemySystem.ID, new EnemySystem.Plan()).build();
    }
    private static Definition definition() {
        Node path = new Node(Map.of("capacity", 32, "max_speed", 10, "max_advance_ticks", 1000,
                "paths", Map.of(LANE.toString(), Map.of("points", List.of(List.of(0,0,0), List.of(100,0,0))))), "path");
        Node targeting = new Node(Map.of("cell_size", 8, "max_range", 128, "capacity", 64, "cache_capacity", 64,
                "max_query_cells", 4096, "max_candidates", 64, "modes", Arrays.stream(TargetingAccess.Mode.values()).map(Enum::name).toList()), "targeting");
        Node combat = new Node(Map.of("damage_types", List.of(NORMAL.toString()), "armor_scale", 100,
                "statuses", Map.of(BURN.toString(), Map.of("kind", "DOT", "base_duration_ticks", 20, "tick_interval", 5,
                        "magnitude", 2, "stacking", "STACK", "max_stacks", 3, "immunity_tags", List.of()))), "combat");
        Node wave = new Node(Map.of("repeat_from", -1, "max_spawns_per_tick", 4, "max_pending_spawns", 16,
                "waves", List.of(Map.of("id", WAVE.toString(), "groups", List.of(Map.of("enemy", ENEMY.toString(), "count", 1,
                        "interval_ticks", 1, "lane", LANE.toString(), "modifiers", List.of(), "tags", List.of()))))), "wave");
        Node enemy = new Node(Map.of("capacity", 64, "max_tick_work", 64, "max_spawns_per_tick", 16, "max_outcomes", 64,
                "profiles", Map.of(ENEMY.toString(), Map.of("renderer", Map.of("kind", "test"), "max_health", 50, "armor", 0,
                        "speed", 1, "strength", 5, "resistances", Map.of(), "status_resistances", Map.of(), "tags", List.of("test:ground"),
                        "reward", Map.of("currency", "test:coins", "amount", 3), "status_capacity", 8)), "modifiers", Map.of()), "enemy");
        return new Definition(1, Id.of("test:td"), "fingerprint", true, 1, 1, Set.of(),
                List.of(new Definition.SystemSpec(PathSystem.ID, path), new Definition.SystemSpec(TargetingSystem.ID, targeting),
                        new Definition.SystemSpec(CombatSystem.ID, combat), new Definition.SystemSpec(WaveSystem.ID, wave), new Definition.SystemSpec(EnemySystem.ID, enemy)),
                Map.of("arena", new Node(Map.of("id", "arena"), "arena")));
    }
}
