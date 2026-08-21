package vn.svframe.lively;

import org.junit.jupiter.api.Test;
import vn.svframe.lively.actor.ActorId;
import vn.svframe.lively.economy.BusinessAccessPolicy;
import vn.svframe.lively.economy.EconomyEngine;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class BusinessAccessPolicyTest {
    private static final ActorId OWNER = new ActorId(new UUID(1L, 2L), ActorId.Kind.NPC);

    @Test
    void hiddenMarketDoesNotLeakBeforeTrustThreshold() {
        EconomyEngine.Business market = business(true, Map.of(
                "kind", "black_market", "hidden", "true", "illegal", "true", "access_trust", "0.35"));
        BusinessAccessPolicy.Decision decision = BusinessAccessPolicy.evaluate(market, .34D, .10D);
        assertFalse(decision.visible());
        assertFalse(decision.allowed());
        assertEquals("insufficient_trust", decision.reason());
    }

    @Test
    void trustedPlayerMayDiscoverOpenHiddenMarket() {
        EconomyEngine.Business market = business(true, Map.of(
                "kind", "black_market", "hidden", "true", "illegal", "true", "access_trust", "0.35"));
        BusinessAccessPolicy.Decision decision = BusinessAccessPolicy.evaluate(market, .48D, -.20D);
        assertTrue(decision.visible());
        assertTrue(decision.allowed());
        assertEquals("allowed", decision.reason());
    }

    @Test
    void reputationCanUnlockAccessButClosedMarketStillRefusesTrade() {
        EconomyEngine.Business market = business(false, Map.of(
                "kind", "underworld", "hidden", "true", "access_trust", "0.40"));
        BusinessAccessPolicy.Decision decision = BusinessAccessPolicy.evaluate(market, .05D, .50D);
        assertTrue(decision.visible());
        assertFalse(decision.allowed());
        assertEquals("closed", decision.reason());
    }

    @Test
    void ordinaryBusinessRemainsVisible() {
        EconomyEngine.Business shop = business(true, Map.of("kind", "shop"));
        assertTrue(BusinessAccessPolicy.evaluate(shop, -1D, -1D).allowed());
        assertFalse(BusinessAccessPolicy.hidden(shop));
    }

    private static EconomyEngine.Business business(boolean open, Map<String, String> facts) {
        return new EconomyEngine.Business(new UUID(2L, 3L), OWNER, "Test", "market", open, List.of(), facts, 1L);
    }
}
