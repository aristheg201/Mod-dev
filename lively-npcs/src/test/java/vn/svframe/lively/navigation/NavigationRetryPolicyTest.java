package vn.svframe.lively.navigation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class NavigationRetryPolicyTest {
    @Test
    void retryDelayBacksOffAndCaps() {
        assertEquals(20L, NavigationRetryPolicy.delayTicks(1));
        assertEquals(40L, NavigationRetryPolicy.delayTicks(2));
        assertEquals(80L, NavigationRetryPolicy.delayTicks(3));
        assertEquals(600L, NavigationRetryPolicy.delayTicks(20));
    }

    @Test
    void staticTravelAbandonsButTrackingKeepsRetrying() {
        assertFalse(NavigationRetryPolicy.abandon(WorldNavigationService.Mode.GOTO, 5));
        assertTrue(NavigationRetryPolicy.abandon(WorldNavigationService.Mode.GOTO, 6));
        assertTrue(NavigationRetryPolicy.abandon(WorldNavigationService.Mode.SCHEDULE, 6));
        assertFalse(NavigationRetryPolicy.abandon(WorldNavigationService.Mode.FOLLOW, 100));
        assertFalse(NavigationRetryPolicy.abandon(WorldNavigationService.Mode.ESCORT, 100));
    }
}
