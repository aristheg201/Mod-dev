package vn.svframe.svarcade.verification;

import java.util.*;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.runtime.*;
import vn.svframe.svarcade.systems.currency.*;
import vn.svframe.svarcade.systems.shop.*;

public final class ShopChecks {
    private ShopChecks() { }

    public static void main(String[] args) {
        Id coins = Id.of("test:coins"), item = Id.of("test:overcharge");
        Node currencyConfig = new Node(Map.of("ownership", "PARTICIPANT", "currencies",
                Map.of(coins.toString(), Map.of("initial", 500, "maximum", 5000))), "currency");
        Node stateConfig = new Node(Map.of("initial", "SHOP", "data", Map.of(),
                "states", Map.of("SHOP", Map.of("terminal", false))), "states");
        Node shopConfig = new Node(Map.of("currency", coins.toString(), "allowed_states", List.of("SHOP"), "max_active_grants", 16,
                "items", Map.of(item.toString(), Map.of("cost", 100, "stock", 2, "per_actor", 1, "duration_ticks", 3,
                        "effect", "test:attack_speed", "payload", Map.of("multiplier", 1.25)))), "shop");
        ShopSystem.Plan shopPlan = new ShopSystem.Plan();
        Checks.equal(Set.of(CurrencySystem.ID, StateMachineSystem.ID), shopPlan.dependencies(shopConfig));
        SystemCatalog catalog = SystemCatalog.builder().add(CurrencySystem.ID, new CurrencySystem.Plan())
                .add(StateMachineSystem.ID, new StateMachineSystem.Plan(new Registry<>(Map.of()), new Registry<>(Map.of())))
                .add(ShopSystem.ID, shopPlan).build();
        Definition definition = new Definition(1, Id.of("test:shop_game"), "fingerprint", true, 1, 1, Set.of(),
                List.of(new Definition.SystemSpec(CurrencySystem.ID, currencyConfig), new Definition.SystemSpec(StateMachineSystem.ID, stateConfig),
                        new Definition.SystemSpec(ShopSystem.ID, shopConfig)), Map.of("arena", new Node(Map.of("id", "arena"), "arena")));
        UUID actor = UUID.randomUUID(); Participant player = new Participant(actor, Participant.Kind.PLAYER, "one"); ThreadGuard thread = new ThreadGuard();
        GenericSession session = new GenericSession(UUID.randomUUID(), definition, "arena", List.of(player), new ArenaRuntime(), thread); session.start(catalog.factories());
        CurrencyAccess currency = session.services().require(CurrencySystem.ACCESS); ShopAccess shop = session.services().require(ShopAccess.ACCESS);
        shop.preparePurchase(actor, item).apply(); Checks.equal(400L, currency.balance(actor, coins)); Checks.equal(1, shop.sold(item));
        ShopAccess.Grant grant = shop.grants(actor).getFirst(); Checks.equal(3L, grant.remainingTicks()); Checks.equal(Id.of("test:attack_speed"), grant.effect());
        Checks.rejects(IllegalStateException.class, () -> shop.preparePurchase(actor, item));

        Map<Id, GenericSession.SystemState> saved = session.snapshot(); session.close();
        GenericSession restored = new GenericSession(UUID.randomUUID(), definition, "arena", List.of(player), new ArenaRuntime(), thread); restored.restore(catalog.factories(), saved);
        ShopAccess recovered = restored.services().require(ShopAccess.ACCESS); Checks.equal(3L, recovered.grants(actor).getFirst().remainingTicks());
        restored.tick(100); restored.tick(103); Checks.equal(List.of(), recovered.grants(actor)); Checks.equal(1, recovered.sold(item));
        restored.close();
        System.out.println("ShopChecks passed");
    }
}
