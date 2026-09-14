package vn.svframe.svarcade;

import java.util.*;
import org.junit.jupiter.api.Test;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.runtime.*;
import vn.svframe.svarcade.systems.loadout.*;
import static org.junit.jupiter.api.Assertions.*;

class LoadoutSystemTest {
    private static final Id SPECIES = Id.of("test:species"), TYPE = Id.of("test:electric"), MOVE = Id.of("test:bolt"), ABILITY = Id.of("test:static"),
            DAMAGE = Id.of("test:damage"), RANGE = Id.of("test:range"), MOVE_TAG = Id.of("test:move_tag");

    @Test void immutableSnapshotsDeriveLevelRulesAndMoveEffectsAndRecover() {
        ThreadGuard thread = new ThreadGuard(); UUID owner = UUID.randomUUID(); Participant player = new Participant(owner, Participant.Kind.PLAYER, "one");
        Node config = config(); SystemCatalog catalog = SystemCatalog.builder().add(LoadoutSystem.ID, new LoadoutSystem.Plan()).build();
        Definition definition = new Definition(1, Id.of("test:loadout_game"), "fp", true, 1, 1, Set.of(),
                List.of(new Definition.SystemSpec(LoadoutSystem.ID, config)), Map.of("arena", new Node(Map.of("id", "arena"), "arena")));
        UUID sessionId = UUID.randomUUID(); GenericSession first = new GenericSession(sessionId, definition, "arena", List.of(player), new ArenaRuntime(), thread); first.start(catalog.factories());
        LoadoutAccess loadouts = first.services().require(LoadoutAccess.ACCESS);
        LoadoutAccess.Snapshot snapshot = new LoadoutAccess.Snapshot("pokemon/canonical-1", SPECIES, "base", Set.of(Id.of("test:shiny")), Set.of(TYPE), 100, Set.of(MOVE), ABILITY, "minecraft:magnet");
        loadouts.prepareRegister(owner, snapshot).apply(); LoadoutAccess.Derived derived = loadouts.derived(snapshot.sourceId()).orElseThrow();
        assertEquals(1.10, derived.levelMultiplier(), 1e-9); assertEquals(0.10, derived.modifiers().get(Id.of("svarcade:level_multiplier_delta")), 1e-9);
        assertEquals(7.0, derived.modifiers().get(DAMAGE), 1e-9); assertEquals(2.0, derived.modifiers().get(RANGE), 1e-9); assertTrue(derived.tags().contains(MOVE_TAG));
        assertThrows(IllegalStateException.class, () -> loadouts.prepareRegister(owner, snapshot));
        Map<Id, GenericSession.SystemState> saved = first.snapshot(); first.close();
        GenericSession restored = new GenericSession(sessionId, definition, "arena", List.of(player), new ArenaRuntime(), thread); restored.restore(catalog.factories(), saved);
        assertEquals(snapshot, restored.services().require(LoadoutAccess.ACCESS).snapshot(snapshot.sourceId()).orElseThrow()); restored.close();
    }

    private static Node config() {
        return new Node(Map.of("max_snapshots", 16, "max_per_actor", 6,
                "level_curve", Map.of("minimum", 1, "center", 50, "maximum", 100, "minimum_multiplier", 0.9, "maximum_multiplier", 1.1),
                "base_modifiers", Map.of(DAMAGE.toString(), 1), "base_tags", List.of("test:party"),
                "rules", List.of(Map.of("species", List.of(SPECIES.toString()), "forms", List.of("base"), "all_aspects", List.of("test:shiny"),
                        "any_types", List.of(TYPE.toString()), "all_moves", List.of(), "abilities", List.of(ABILITY.toString()), "tags", List.of("test:matched"),
                        "modifiers", Map.of(DAMAGE.toString(), 2))),
                "move_effects", Map.of(MOVE.toString(), Map.of("tags", List.of(MOVE_TAG.toString()), "modifiers", Map.of(DAMAGE.toString(), 4, RANGE.toString(), 2)))), "loadouts");
    }
}
