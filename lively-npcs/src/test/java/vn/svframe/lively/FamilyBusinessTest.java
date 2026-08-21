package vn.svframe.lively;

import org.junit.jupiter.api.Test;
import vn.svframe.lively.actor.ActorId;
import vn.svframe.lively.economy.EconomyEngine;
import vn.svframe.lively.social.FamilyEngine;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class FamilyBusinessTest {
    @Test
    void spouseHouseholdAndParentChildRelationsAreTypedAndStable() {
        FamilyEngine family = new FamilyEngine();
        ActorId a = npc(1); ActorId b = npc(2); ActorId child = npc(3); ActorId sibling = npc(4);
        FamilyEngine.Household household = family.ensureSpouseHousehold(a, b, "home_01");
        family.linkParentChild(a, child, 1D, Map.of());
        family.linkParentChild(a, sibling, 1D, Map.of());

        assertEquals(Set.of(a, b), household.members());
        assertTrue(family.kinshipsOf(a).stream().anyMatch(k -> k.to().equals(b) && k.type() == FamilyEngine.KinshipType.SPOUSE));
        assertTrue(family.kinshipsOf(child).stream().anyMatch(k -> k.to().equals(a) && k.type() == FamilyEngine.KinshipType.CHILD));
        assertTrue(family.kinshipsOf(child).stream().anyMatch(k -> k.to().equals(sibling) && k.type() == FamilyEngine.KinshipType.SIBLING));

        FamilyEngine restored = new FamilyEngine();
        restored.restore(family.snapshot());
        assertEquals(household.id(), restored.householdOf(a).orElseThrow().id());
        assertEquals(2, restored.childrenOf(a).size());
    }

    @Test
    void businessPayrollCannotOverdrawAndSellUsesBuyback() {
        EconomyEngine economy = new EconomyEngine();
        ActorId owner = npc(10); ActorId employeeA = npc(11); ActorId employeeB = npc(12); ActorId seller = npc(13);
        EconomyEngine.Business business = economy.createBusiness(owner, "Bakery", "market", Map.of("wage", "40"));
        economy.ensureWallet(owner, 150L);
        economy.ensureWallet(employeeA, 0L); economy.ensureWallet(employeeB, 0L); economy.ensureWallet(seller, 0L);
        economy.assignEmployee(business.id(), employeeA); economy.assignEmployee(business.id(), employeeB);

        assertEquals(2, economy.payroll(business.id(), 40L));
        assertEquals(70L, economy.snapshot().wallets().get(owner).balance());
        assertEquals(40L, economy.snapshot().wallets().get(employeeA).balance());
        assertEquals(40L, economy.snapshot().wallets().get(employeeB).balance());

        economy.setStock(business.id(), "minecraft:bread", 10, 20, 10, 0.5D, 0.5D);
        assertTrue(economy.sell(business.id(), seller, "minecraft:bread", 1, 0.5D).isPresent());
        assertTrue(economy.snapshot().wallets().get(seller).balance() > 0L);
        assertTrue(economy.snapshot().stocks().get(new EconomyEngine.StockKey(business.id(), "minecraft:bread")).quantity() >= 11L);
    }

    private static ActorId npc(long id) { return new ActorId(new UUID(0L, id), ActorId.Kind.NPC); }
}
