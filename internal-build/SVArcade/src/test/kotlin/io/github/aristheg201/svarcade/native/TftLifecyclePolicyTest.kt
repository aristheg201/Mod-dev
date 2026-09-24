package io.github.aristheg201.svarcade.native

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

    @Test fun matchmakingCollectsHumansWithoutBlockingAndStartsAtDeadlineOrCapacity() {
        val config = TftLifecyclePolicy.MatchmakingConfig(collectionWindowMs = 5_000)
        assertEquals(TftLifecyclePolicy.CollectionDecision.WAITING_FOR_MINIMUM, TftLifecyclePolicy.decision(1, null, 1_000, config))
        assertEquals(TftLifecyclePolicy.CollectionDecision.COLLECTING, TftLifecyclePolicy.decision(2, null, 1_000, config))
        assertEquals(TftLifecyclePolicy.CollectionDecision.COLLECTING, TftLifecyclePolicy.decision(7, 6_000, 5_999, config))
        assertEquals(TftLifecyclePolicy.CollectionDecision.START, TftLifecyclePolicy.decision(2, 6_000, 6_000, config))
        assertEquals(TftLifecyclePolicy.CollectionDecision.START, TftLifecyclePolicy.decision(8, null, 1_000, config))
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
