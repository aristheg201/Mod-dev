package vn.svframe.lively.integration;

import org.junit.jupiter.api.Test;
import vn.svframe.lively.api.WaypointBridge;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class SvfWaypointsBridgeTest {
    @Test
    void discoversTypedDynamicNavigationAndClearSurface() {
        WaypointBridge bridge = SvfWaypointsBridge.create();
        assertTrue(bridge.available());
    }
}
