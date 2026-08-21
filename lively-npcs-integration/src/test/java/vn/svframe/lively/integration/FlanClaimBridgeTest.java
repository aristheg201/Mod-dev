package vn.svframe.lively.integration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class FlanClaimBridgeTest {
    @Test
    void mapsNpcSemanticActionsToFlanBuiltinPermissions() {
        assertEquals("flan:open_container", FlanClaimBridge.permission("storage").toString());
        assertEquals("flan:door", FlanClaimBridge.permission("entrance").toString());
        assertEquals("flan:bed", FlanClaimBridge.permission("sleep").toString());
        assertEquals("flan:trading", FlanClaimBridge.permission("trade").toString());
        assertEquals("flan:animal_interact", FlanClaimBridge.permission("pokemon_interact").toString());
        assertEquals("flan:interact_block", FlanClaimBridge.permission("unknown-semantic-action").toString());
    }
}
