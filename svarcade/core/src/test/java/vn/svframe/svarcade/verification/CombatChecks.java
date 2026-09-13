package vn.svframe.svarcade.verification;

import java.util.*;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.runtime.*;
import vn.svframe.svarcade.systems.combat.*;
import vn.svframe.svarcade.systems.combat.CombatAccess.*;

public final class CombatChecks {
    private CombatChecks() { }
    public static void main(String[] args) {
        Id physical = Id.of("test:physical"), burn = Id.of("test:burn"), immune = Id.of("test:burn_immune");
        Node config = new Node(Map.of("damage_types", List.of(physical.toString()), "armor_scale", 100,
                "statuses", Map.of(burn.toString(), Map.of("kind", "DOT", "base_duration_ticks", 100,
                        "tick_interval", 20, "magnitude", 5, "stacking", "STACK", "max_stacks", 3,
                        "immunity_tags", List.of(immune.toString())))), "combat");
        CombatSystem.Plan plan = new CombatSystem.Plan(); plan.validate(config);
        SystemCatalog catalog = SystemCatalog.builder().add(CombatSystem.ID, plan).build();
        Definition definition = new Definition(1, Id.of("test:combat_game"), "fingerprint", true, 1, 1, Set.of(),
                List.of(new Definition.SystemSpec(CombatSystem.ID, config)), Map.of("arena", new Node(Map.of("id", "arena"), "arena")));
        ThreadGuard thread = new ThreadGuard(); GenericSession session = new GenericSession(UUID.randomUUID(), definition, "arena",
                List.of(new Participant(UUID.randomUUID(), Participant.Kind.PLAYER, "one")), new ArenaRuntime(), thread);
        session.start(catalog.factories()); CombatAccess combat = session.services().require(CombatAccess.ACCESS);
        Attack attack = new Attack(physical, 100, 12, 20, 0, 2, Delivery.AOE, 4, 0,
                List.of(new StatusAttempt(burn, 1, 1, 2)));
        Target target = new Target(100, Map.of(physical, 0.25), Set.of(), Map.of(burn, 0.0));
        Resolution result = combat.resolve(attack, target, 1234);
        Checks.equal(37.5, result.damage()); Checks.equal(false, result.critical()); Checks.equal(1, result.statuses().size());
        Checks.equal(2, result.statuses().getFirst().stacks()); Checks.equal(100L, result.statuses().getFirst().durationTicks());
        Target immuneTarget = new Target(0, Map.of(), Set.of(immune), Map.of());
        Checks.equal(0, combat.resolve(attack, immuneTarget, 1234).statuses().size());
        Checks.rejects(IllegalArgumentException.class, () -> combat.resolve(attack, new Target(0, Map.of(physical, 2.0), Set.of(), Map.of()), 0));
        session.close(); Checks.equal(GenericSession.Status.CLOSED, session.status());
        System.out.println("CombatChecks passed");
    }
}
