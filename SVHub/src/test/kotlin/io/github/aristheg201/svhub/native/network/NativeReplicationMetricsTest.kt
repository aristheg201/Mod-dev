package io.github.aristheg201.svhub.native.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NativeReplicationMetricsTest {
    @Test fun unchangedSnapshotsAreSuppressedAndRatesAreMeasured() {
        var now = 0L
        val tracker = NativeReplicationTracker { now }
        assertTrue(tracker.shouldSend("first"))
        assertFalse(tracker.shouldSend("first"))
        assertTrue(tracker.shouldSend("second"))
        now = 1_000L

        val metrics = tracker.metrics()
        assertEquals(2, metrics.sentPackets)
        assertEquals(1, metrics.suppressedPackets)
        assertEquals(11, metrics.sentBytes)
        assertEquals(2, metrics.packetsPerSecond)
        assertEquals(11, metrics.bytesPerSecond)
    }

    @Test fun presentationMessageParticipatesInReplicationIdentity() {
        val tracker = NativeReplicationTracker { 0L }
        assertTrue(tracker.shouldSend("state\u0000message-a"))
        assertTrue(tracker.shouldSend("state\u0000message-b"))
        assertFalse(tracker.shouldSend("state\u0000message-b"))
    }
}
