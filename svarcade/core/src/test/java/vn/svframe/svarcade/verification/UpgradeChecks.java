package vn.svframe.svarcade.verification;

import java.util.*;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.runtime.*;
import vn.svframe.svarcade.systems.currency.*;
import vn.svframe.svarcade.systems.deployable.*;
import vn.svframe.svarcade.systems.deployable.DeployableAccess.Point;
import vn.svframe.svarcade.systems.upgrade.*;

public final class UpgradeChecks {
    private UpgradeChecks() { }

    public static void main(String[] args) {
        Id coins = Id.of("test:coins"), tower = Id.of("test:electric_tower"), damage = Id.of("test:damage"), chain = Id.of("test:chain");
        Node currencyConfig = new Node(Map.of("ownership", "PARTICIPANT", "currencies",
                Map.of(coins.toString(), Map.of("initial", 2000, "maximum", 10000))), "currency");
        Node deployConfig = new Node(Map.of("currency", coins.toString(), "max_total", 8, "coordinate_limit", 1000,
                "profiles", Map.of(tower.toString(), Map.of("deploy_cost", 100, "move_cost", 10,
                        "recall_refund", 50, "max_per_actor", 4, "tags", List.of("test:electric")))), "deployables");
        Node upgradeConfig = new Node(Map.of("currency", coins.toString(), "upgrades", Map.of(
                damage.toString(), Map.of("max_level", 2, "costs", List.of(100, 200), "required_tags", List.of("test:electric"),
                        "modifiers", Map.of("test:damage_bonus", 10)),
                chain.toString(), Map.of("max_level", 1, "costs", List.of(300), "required_tags", List.of("test:electric"),
                        "prerequisites", List.of(Map.of("upgrade", damage.toString(), "level", 2)), "modifiers", Map.of("test:chain_targets", 1)))), "upgrades");
        SystemCatalog catalog = SystemCatalog.builder().add(CurrencySystem.ID, new CurrencySystem.Plan())
                .add(DeployableSystem.ID, new DeployableSystem.Plan()).add(UpgradeSystem.ID, new UpgradeSystem.Plan()).build();
        Definition definition = new Definition(1, Id.of("test:upgrade_game"), "fingerprint", true, 1, 1, Set.of(),
                List.of(new Definition.SystemSpec(CurrencySystem.ID, currencyConfig), new Definition.SystemSpec(DeployableSystem.ID, deployConfig),
                        new Definition.SystemSpec(UpgradeSystem.ID, upgradeConfig)), Map.of("arena", new Node(Map.of("id", "arena"), "arena")));
        UUID actor = UUID.randomUUID(); Participant player = new Participant(actor, Participant.Kind.PLAYER, "one"); ThreadGuard thread = new ThreadGuard();
        GenericSession session = new GenericSession(UUID.randomUUID(), definition, "arena", List.of(player), new ArenaRuntime(), thread);
        session.start(catalog.factories()); CurrencyAccess currency = session.services().require(CurrencySystem.ACCESS);
        DeployableAccess deployables = session.services().require(DeployableAccess.ACCESS); UpgradeAccess upgrades = session.services().require(UpgradeAccess.ACCESS);
        StateChange deploy = deployables.prepareDeploy(actor, "pokemon/alpha", tower, new Point(1, 2, 3)); deploy.apply();
        long deployed = deployables.bySource("pokemon/alpha").orElseThrow().id(); Checks.equal(1900L, currency.balance(actor, coins));
        Checks.rejects(IllegalStateException.class, () -> upgrades.preparePurchase(actor, deployed, chain));
        upgrades.preparePurchase(actor, deployed, damage).apply(); Checks.equal(1, upgrades.level(deployed, damage)); Checks.equal(1800L, currency.balance(actor, coins));
        upgrades.preparePurchase(actor, deployed, damage).apply(); Checks.equal(2, upgrades.level(deployed, damage)); Checks.equal(20.0, upgrades.modifiers(deployed).get(Id.of("test:damage_bonus")));
        upgrades.preparePurchase(actor, deployed, chain).apply(); Checks.equal(1300L, currency.balance(actor, coins)); Checks.equal(1.0, upgrades.modifiers(deployed).get(Id.of("test:chain_targets")));
        Checks.rejects(IllegalStateException.class, () -> upgrades.preparePurchase(actor, deployed, chain));

        Map<Id, GenericSession.SystemState> saved = session.snapshot(); session.close();
        GenericSession restored = new GenericSession(UUID.randomUUID(), definition, "arena", List.of(player), new ArenaRuntime(), thread); restored.restore(catalog.factories(), saved);
        UpgradeAccess recoveredUpgrades = restored.services().require(UpgradeAccess.ACCESS); DeployableAccess recoveredDeployables = restored.services().require(DeployableAccess.ACCESS);
        Checks.equal(2, recoveredUpgrades.level(deployed, damage)); Checks.equal(1, recoveredUpgrades.level(deployed, chain));
        recoveredDeployables.prepareRecall(actor, deployed).apply(); restored.tick(1);
        Checks.rejects(IllegalArgumentException.class, () -> recoveredUpgrades.level(deployed, damage));
        restored.close();

        Node cyclic = new Node(Map.of("currency", coins.toString(), "upgrades", Map.of(
                "test:a", Map.of("max_level", 1, "costs", List.of(1), "prerequisites", List.of(Map.of("upgrade", "test:b", "level", 1)), "modifiers", Map.of("test:x", 1)),
                "test:b", Map.of("max_level", 1, "costs", List.of(1), "prerequisites", List.of(Map.of("upgrade", "test:a", "level", 1)), "modifiers", Map.of("test:y", 1)))), "cyclic");
        Checks.rejects(ConfigException.class, () -> new UpgradeSystem.Plan().validate(cyclic));
        System.out.println("UpgradeChecks passed");
    }
}
