package vn.svframe.svarcade;

import java.util.*;
import org.junit.jupiter.api.Test;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.runtime.*;
import vn.svframe.svarcade.systems.combat.*;
import vn.svframe.svarcade.systems.currency.*;
import vn.svframe.svarcade.systems.deployable.*;
import vn.svframe.svarcade.systems.enemy.*;
import vn.svframe.svarcade.systems.path.*;
import vn.svframe.svarcade.systems.targeting.*;
import vn.svframe.svarcade.systems.tower.*;
import vn.svframe.svarcade.systems.upgrade.*;
import vn.svframe.svarcade.systems.wave.*;
import static org.junit.jupiter.api.Assertions.*;

class TowerSystemTest {
    private static final Id COINS = Id.of("test:coins"), TOWER = Id.of("test:tower"), POWER = Id.of("test:power"), DAMAGE_ADD = Id.of("test:damage_add"),
            ENEMY = Id.of("test:slime"), LANE = Id.of("test:lane"), WAVE = Id.of("test:wave"), NORMAL = Id.of("test:normal");

    @Test void deployedTowerUsesSpatialTargetingUpgradesDeadlinesAndRecovery() {
        ThreadGuard thread = new ThreadGuard(); UUID playerId = UUID.randomUUID(), sessionId = UUID.randomUUID(); Participant player = new Participant(playerId, Participant.Kind.PLAYER, "one");
        SystemCatalog catalog = catalog(); Definition definition = definition(); GenericSession first = new GenericSession(sessionId, definition, "arena", List.of(player), new ArenaRuntime(), thread);
        first.start(catalog.factories()); DeployableAccess deployables = first.services().require(DeployableAccess.ACCESS);
        deployables.prepareDeploy(playerId, "pokemon/canonical-1", TOWER, new DeployableAccess.Point(0, 0, 0)).apply(); long towerId = deployables.bySource("pokemon/canonical-1").orElseThrow().id();
        first.services().require(UpgradeAccess.ACCESS).preparePurchase(playerId, towerId, POWER).apply(); first.services().require(WaveAccess.ACCESS).prepareStartNext().apply();
        first.tick(1); EnemyAccess enemies = first.services().require(EnemyAccess.ACCESS); EnemyAccess.View enemy = enemies.enemies(4).getFirst(); assertEquals(10.0, enemy.health());
        TowerAccess towers = first.services().require(TowerAccess.ACCESS); assertEquals(1, towers.attacks()); assertEquals(TargetingAccess.Mode.FIRST, towers.tower(towerId).orElseThrow().mode());
        towers.prepareTargetMode(playerId, towerId, TargetingAccess.Mode.HIGHEST_HP).apply(); Map<Id, GenericSession.SystemState> saved = first.snapshot(); first.close();

        GenericSession restored = new GenericSession(sessionId, definition, "arena", List.of(player), new ArenaRuntime(), thread); restored.restore(catalog.factories(), saved);
        TowerAccess recoveredTowers = restored.services().require(TowerAccess.ACCESS); assertEquals(TargetingAccess.Mode.HIGHEST_HP, recoveredTowers.tower(towerId).orElseThrow().mode());
        restored.tick(2); assertEquals(10.0, restored.services().require(EnemyAccess.ACCESS).enemy(enemy.id()).orElseThrow().health());
        restored.tick(3); assertEquals(10.0, restored.services().require(EnemyAccess.ACCESS).enemy(enemy.id()).orElseThrow().health());
        restored.tick(4); assertEquals(0.0, restored.services().require(EnemyAccess.ACCESS).enemy(enemy.id()).orElseThrow().health());
        restored.tick(5); assertEquals(0, restored.services().require(EnemyAccess.ACCESS).size()); assertTrue(restored.services().require(WaveAccess.ACCESS).clearEvent().isPresent());
        assertEquals(2, recoveredTowers.attacks()); restored.close();
    }

    private static SystemCatalog catalog() {
        return SystemCatalog.builder().add(CurrencySystem.ID, new CurrencySystem.Plan()).add(DeployableSystem.ID, new DeployableSystem.Plan())
                .add(UpgradeSystem.ID, new UpgradeSystem.Plan()).add(PathSystem.ID, new PathSystem.Plan()).add(TargetingSystem.ID, new TargetingSystem.Plan())
                .add(CombatSystem.ID, new CombatSystem.Plan()).add(WaveSystem.ID, new WaveSystem.Plan()).add(EnemySystem.ID, new EnemySystem.Plan())
                .add(TowerSystem.ID, new TowerSystem.Plan()).build();
    }

    private static Definition definition() {
        Node currency = new Node(Map.of("ownership", "PARTICIPANT", "currencies", Map.of(COINS.toString(), Map.of("initial", 1000, "maximum", 100000))), "currency");
        Node deployable = new Node(Map.of("currency", COINS.toString(), "max_total", 16, "coordinate_limit", 1000,
                "profiles", Map.of(TOWER.toString(), Map.of("deploy_cost", 10, "move_cost", 2, "recall_refund", 5, "max_per_actor", 8, "tags", List.of("test:ground")))), "deployable");
        Node upgrade = new Node(Map.of("currency", COINS.toString(), "upgrades", Map.of(POWER.toString(), Map.of("max_level", 1, "costs", List.of(10),
                "profiles", List.of(TOWER.toString()), "required_tags", List.of(), "forbidden_tags", List.of(), "prerequisites", List.of(), "modifiers", Map.of(DAMAGE_ADD.toString(), 5)))), "upgrade");
        Node path = new Node(Map.of("capacity", 32, "max_speed", 10, "max_advance_ticks", 1000,
                "paths", Map.of(LANE.toString(), Map.of("points", List.of(List.of(0,0,0), List.of(100,0,0))))), "path");
        Node targeting = new Node(Map.of("cell_size", 8, "max_range", 128, "capacity", 64, "cache_capacity", 64,
                "max_query_cells", 4096, "max_candidates", 64, "modes", Arrays.stream(TargetingAccess.Mode.values()).map(Enum::name).toList()), "targeting");
        Node combat = new Node(Map.of("damage_types", List.of(NORMAL.toString()), "armor_scale", 100, "statuses", Map.of()), "combat");
        Node wave = new Node(Map.of("repeat_from", -1, "max_spawns_per_tick", 4, "max_pending_spawns", 16,
                "waves", List.of(Map.of("id", WAVE.toString(), "groups", List.of(Map.of("enemy", ENEMY.toString(), "count", 1, "interval_ticks", 1,
                        "lane", LANE.toString(), "modifiers", List.of(), "tags", List.of()))))), "wave");
        Node enemy = new Node(Map.of("capacity", 64, "max_tick_work", 64, "max_spawns_per_tick", 16, "max_outcomes", 64,
                "profiles", Map.of(ENEMY.toString(), Map.of("renderer", Map.of(), "max_health", 20, "armor", 0, "speed", 1, "strength", 1,
                        "resistances", Map.of(), "status_resistances", Map.of(), "tags", List.of("test:ground"), "reward", Map.of(), "status_capacity", 8)), "modifiers", Map.of()), "enemy");
        Node tower = new Node(Map.of("max_attacks_per_tick", 16, "idle_retry_ticks", 1, "modifier_bindings", Map.of(DAMAGE_ADD.toString(), "DAMAGE_ADD"),
                "profiles", Map.of(TOWER.toString(), Map.of("attack", Map.of("damage_type", NORMAL.toString(), "damage", 5, "range", 32, "cooldown_ticks", 2,
                        "crit_chance", 0, "crit_multiplier", 1, "delivery", "INSTANT", "aoe_cap", 0, "chain_cap", 0, "statuses", List.of()),
                        "target_modes", Arrays.stream(TargetingAccess.Mode.values()).map(Enum::name).toList(), "default_target_mode", "FIRST",
                        "filter", Map.of("all", List.of("test:ground"), "any", List.of(), "none", List.of())))), "tower");
        return new Definition(1, Id.of("test:tower_game"), "fingerprint", true, 1, 1, Set.of(), List.of(
                new Definition.SystemSpec(CurrencySystem.ID, currency), new Definition.SystemSpec(DeployableSystem.ID, deployable), new Definition.SystemSpec(UpgradeSystem.ID, upgrade),
                new Definition.SystemSpec(PathSystem.ID, path), new Definition.SystemSpec(TargetingSystem.ID, targeting), new Definition.SystemSpec(CombatSystem.ID, combat),
                new Definition.SystemSpec(WaveSystem.ID, wave), new Definition.SystemSpec(EnemySystem.ID, enemy), new Definition.SystemSpec(TowerSystem.ID, tower)),
                Map.of("arena", new Node(Map.of("id", "arena"), "arena")));
    }
}
