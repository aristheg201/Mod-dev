package vn.svframe.svarcade.verification;

import java.util.*;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.runtime.*;
import vn.svframe.svarcade.systems.currency.*;
import static vn.svframe.svarcade.verification.Checks.*;

public final class CurrencyChecks {
    private CurrencyChecks() { }
    public static void main(String[] ignored) {
        Id coins = Id.of("test:coins"); UUID a = UUID.randomUUID(), b = UUID.randomUUID(), spectator = UUID.randomUUID();
        List<Participant> people = List.of(new Participant(a, Participant.Kind.PLAYER, "team"), new Participant(b, Participant.Kind.BOT, "team"),
                new Participant(spectator, Participant.Kind.SPECTATOR, "team"));
        CurrencySystem.Config config = new CurrencySystem.Config(CurrencySystem.Ownership.PARTICIPANT, Map.of(coins, new CurrencySystem.Unit(100, 1000)));
        CurrencySystem ledger = new CurrencySystem(config, people, new ThreadGuard()); ledger.start();
        equal(100L, ledger.balance(a, coins)); equal(100L, ledger.balance(b, coins));
        rejects(IllegalArgumentException.class, () -> ledger.balance(spectator, coins));
        CurrencyAccess.Change transfer = ledger.prepare(List.of(new CurrencyAccess.Delta(a, coins, -30), new CurrencyAccess.Delta(b, coins, 30)));
        equal(100L, ledger.balance(a, coins)); equal(0L, ledger.revision()); transfer.apply();
        equal(70L, ledger.balance(a, coins)); equal(130L, ledger.balance(b, coins)); equal(1L, ledger.revision());
        rejects(IllegalStateException.class, transfer::apply); transfer.rollback(); equal(100L, ledger.balance(a, coins)); equal(0L, ledger.revision());
        rejects(IllegalStateException.class, transfer::apply); rejects(IllegalStateException.class, transfer::rollback);
        rejects(IllegalArgumentException.class, () -> ledger.prepare(List.of(new CurrencyAccess.Delta(a, coins, -101))));
        rejects(ArithmeticException.class, () -> ledger.prepare(List.of(new CurrencyAccess.Delta(a, coins, Long.MAX_VALUE))));
        equal(100L, ledger.balance(a, coins));
        CurrencyAccess.Change old = ledger.prepare(List.of(new CurrencyAccess.Delta(a, coins, -10)));
        CurrencyAccess.Change fresh = ledger.prepare(List.of(new CurrencyAccess.Delta(b, coins, -1))); fresh.apply();
        rejects(IllegalStateException.class, old::apply);
        Map<String, Object> saved = ledger.snapshot(); CurrencySystem restored = new CurrencySystem(config, people, new ThreadGuard()); restored.restore(1, saved);
        equal(saved, restored.snapshot()); equal(99L, restored.balance(b, coins));
        CurrencySystem invalid = new CurrencySystem(config, people, new ThreadGuard());
        rejects(ConfigException.class, () -> invalid.restore(1, Map.of("revision", 0, "balances", Map.of()))); invalid.restore(1, saved);
        CurrencySystem shared = new CurrencySystem(new CurrencySystem.Config(CurrencySystem.Ownership.TEAM, config.units()), people, new ThreadGuard()); shared.start();
        shared.prepare(List.of(new CurrencyAccess.Delta(a, coins, -50))).apply(); equal(50L, shared.balance(b, coins));
        shared.prepare(List.of(new CurrencyAccess.Delta(a, coins, -30), new CurrencyAccess.Delta(b, coins, 30))).apply(); equal(50L, shared.balance(a, coins));
        Random random = new Random(81021);
        for (int i = 0; i < 2000; i++) {
            UUID from = random.nextBoolean() ? a : b, to = from.equals(a) ? b : a;
            long amount = random.nextInt(8); long available = ledger.balance(from, coins);
            if (amount > available) continue;
            CurrencyAccess.Change change = ledger.prepare(List.of(new CurrencyAccess.Delta(from, coins, -amount), new CurrencyAccess.Delta(to, coins, amount)));
            change.apply(); if (random.nextBoolean()) change.rollback();
            equal(199L, ledger.balance(a, coins) + ledger.balance(b, coins));
        }
        Node authored = new Node(Map.of("ownership", "PARTICIPANT", "currencies", Map.of(coins.toString(), Map.of("initial", 100, "maximum", 1000))), "currency");
        CurrencySystem.Plan plan = new CurrencySystem.Plan(); plan.validate(authored);
        Definition definition = new Definition(1, Id.of("test:ledger"), "v1", true, 1, 2, Set.of(),
                List.of(new Definition.SystemSpec(CurrencySystem.ID, authored)), Map.of("arena", new Node(Map.of(), "arena")));
        ArenaRuntime arenas = new ArenaRuntime(); GenericSession session = new GenericSession(UUID.randomUUID(), definition, "arena", people, arenas, new ThreadGuard());
        session.start(new Registry<>(Map.of(CurrencySystem.ID, plan))); CurrencyAccess access = session.services().require(CurrencySystem.ACCESS);
        access.prepare(List.of(new CurrencyAccess.Delta(b, coins, -1))).apply(); equal(99L, access.balance(b, coins));
        equal(1, session.snapshot().size()); session.close(); equal(0, arenas.snapshot().size());
        ledger.close(); restored.close(); invalid.close(); shared.close();
        rejects(IllegalStateException.class, ledger::start); rejects(IllegalStateException.class, () -> ledger.prepare(List.of(new CurrencyAccess.Delta(a, coins, 1))));
        System.out.println("CurrencyChecks: PASS (same human/bot funds, team ownership, atomic transfer/rollback, overspend/overflow, stale/replay rejection, recovery)");
    }
}
