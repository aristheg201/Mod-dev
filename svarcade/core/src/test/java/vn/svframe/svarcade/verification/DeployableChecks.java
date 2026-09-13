package vn.svframe.svarcade.verification;

import java.util.*;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.runtime.*;
import vn.svframe.svarcade.systems.currency.*;
import vn.svframe.svarcade.systems.deployable.*;
import vn.svframe.svarcade.systems.deployable.DeployableAccess.*;

public final class DeployableChecks {
    private DeployableChecks() { }

    public static void main(String[] args) {
        Id coins = Id.of("test:coins"), tower = Id.of("test:tower");
        Node currencyConfig = new Node(Map.of("ownership", "PARTICIPANT", "currencies",
                Map.of(coins.toString(), Map.of("initial", 1000, "maximum", 10000))), "currency");
        Node deployConfig = new Node(Map.of("currency", coins.toString(), "max_total", 16, "coordinate_limit", 1000,
                "profiles", Map.of(tower.toString(), Map.of("deploy_cost", 100, "move_cost", 10,
                        "recall_refund", 50, "max_per_actor", 4, "tags", List.of("test:electric")))), "deployables");
        SystemCatalog catalog = SystemCatalog.builder()
                .add(CurrencySystem.ID, new CurrencySystem.Plan())
                .add(DeployableSystem.ID, new DeployableSystem.Plan()).build();
        Definition definition = new Definition(1, Id.of("test:td"), "fingerprint", true, 1, 1, Set.of(),
                List.of(new Definition.SystemSpec(CurrencySystem.ID, currencyConfig), new Definition.SystemSpec(DeployableSystem.ID, deployConfig)),
                Map.of("arena", new Node(Map.of("id", "arena"), "arena")));
        UUID actor = UUID.randomUUID(); Participant player = new Participant(actor, Participant.Kind.PLAYER, "one");
        ThreadGuard thread = new ThreadGuard(); GenericSession session = new GenericSession(UUID.randomUUID(), definition, "arena",
                List.of(player), new ArenaRuntime(), thread);
        session.start(catalog.factories()); CurrencyAccess currency = session.services().require(CurrencySystem.ACCESS);
        DeployableAccess deployables = session.services().require(DeployableAccess.ACCESS);

        StateChange deploy = deployables.prepareDeploy(actor, "pokemon/123", tower, new Point(1, 2, 3));
        deploy.apply(); Checks.equal(900L, currency.balance(actor, coins));
        Deployment first = deployables.bySource("pokemon/123").orElseThrow(); Checks.equal(actor, first.owner());
        Checks.rejects(IllegalStateException.class, () -> deployables.prepareDeploy(actor, "pokemon/123", tower, new Point(2, 2, 2)));

        StateChange move = deployables.prepareMove(actor, first.id(), new Point(4, 5, 6)); move.apply();
        Checks.equal(890L, currency.balance(actor, coins)); Checks.equal(new Point(4, 5, 6), deployables.deployment(first.id()).orElseThrow().position());

        Map<Id, GenericSession.SystemState> saved = session.snapshot(); session.close();
        GenericSession restored = new GenericSession(UUID.randomUUID(), definition, "arena", List.of(player), new ArenaRuntime(), thread);
        restored.restore(catalog.factories(), saved);
        CurrencyAccess restoredCurrency = restored.services().require(CurrencySystem.ACCESS);
        DeployableAccess restoredDeployables = restored.services().require(DeployableAccess.ACCESS);
        Deployment recovered = restoredDeployables.bySource("pokemon/123").orElseThrow();
        Checks.equal(new Point(4, 5, 6), recovered.position()); Checks.equal(890L, restoredCurrency.balance(actor, coins));

        StateChange recall = restoredDeployables.prepareRecall(actor, recovered.id()); recall.apply();
        Checks.equal(940L, restoredCurrency.balance(actor, coins)); Checks.equal(Optional.empty(), restoredDeployables.bySource("pokemon/123"));
        restored.close(); Checks.equal(GenericSession.Status.CLOSED, restored.status());
        System.out.println("DeployableChecks passed");
    }
}
