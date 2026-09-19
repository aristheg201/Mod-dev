package io.github.aristheg201.svhub.native

import kotlin.test.*

class TftLifecyclePolicyTest {
    @Test fun tftStartsAtTwoHumansAndFillsExactlyEightParticipants() {
        assertNull(TftLifecyclePolicy.botFillCount(1))
        for (humans in 2..8) assertEquals(8, humans + TftLifecyclePolicy.botFillCount(humans)!!)
        assertEquals(6, TftLifecyclePolicy.botFillCount(2))
        assertEquals(5, TftLifecyclePolicy.botFillCount(3))
        assertEquals(1, TftLifecyclePolicy.botFillCount(7))
        assertEquals(0, TftLifecyclePolicy.botFillCount(8))
    }

    @Test fun closingTftViewNeverResignsButOtherGamesKeepLegacyPolicy() {
        assertFalse(TftLifecyclePolicy.closeResigns("tft"))
        assertTrue(TftLifecyclePolicy.closeResigns("chess"))
        assertTrue(TftLifecyclePolicy.closeResigns(null))
    }

    @Test fun realTftDisconnectUsesTakeoverInsteadOfForfeit() {
        assertTrue(TftLifecyclePolicy.disconnectExpiresToBot("tft"))
        assertFalse(TftLifecyclePolicy.disconnectExpiresToBot("uno"))
    }
}
