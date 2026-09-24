package io.github.aristheg201.svarcade.native.network

/**
 * Retains the last serialized view for one open native screen. This is deliberately
 * independent of Fabric so the replication policy can be regression-tested without
 * starting Minecraft.
 */
class NativeReplicationTracker(private val clock: () -> Long = System::currentTimeMillis) {
    data class Metrics(
        val sentPackets: Long,
        val suppressedPackets: Long,
        val sentBytes: Long,
        val packetsPerSecond: Long,
        val bytesPerSecond: Long
        ,val fullSnapshots:Long=0,val deltaPackets:Long=0,val componentsReplicated:Long=0,val replicationFlushes:Long=0,val noOpFlushes:Long=0,val averageDeltaBytes:Long=0,val maximumDeltaBytes:Long=0
    )

    private var lastPayload: String? = null
    private var sentPackets = 0L
    private var suppressedPackets = 0L
    private var sentBytes = 0L
    private var windowStartedAt = clock()
    private var windowPackets = 0L
    private var windowBytes = 0L
    private var lastPacketsPerSecond = 0L
    private var lastBytesPerSecond = 0L
    private var fullSnapshots=0L;private var deltaPackets=0L;private var componentsReplicated=0L;private var replicationFlushes=0L;private var noOpFlushes=0L;private var deltaBytes=0L;private var maximumDeltaBytes=0L

    @Synchronized fun recordFull(serializedBytes:Long){fullSnapshots++;sentPackets++;sentBytes+=serializedBytes;windowPackets++;windowBytes+=serializedBytes;rollWindow(clock())}
    @Synchronized fun recordDelta(serializedBytes:Long,components:Int){replicationFlushes++;deltaPackets++;componentsReplicated+=components;deltaBytes+=serializedBytes;maximumDeltaBytes=maxOf(maximumDeltaBytes,serializedBytes);sentPackets++;sentBytes+=serializedBytes;windowPackets++;windowBytes+=serializedBytes;rollWindow(clock())}
    @Synchronized fun recordNoOp(){replicationFlushes++;noOpFlushes++;suppressedPackets++;rollWindow(clock())}

    @Synchronized
    fun shouldSend(payload: String): Boolean {
        if (payload == lastPayload) {
            suppressedPackets++
            rollWindow(clock())
            return false
        }
        lastPayload = payload
        val bytes = payload.toByteArray(Charsets.UTF_8).size.toLong()
        sentPackets++
        sentBytes += bytes
        windowPackets++
        windowBytes += bytes
        rollWindow(clock())
        return true
    }

    @Synchronized
    fun metrics(): Metrics {
        rollWindow(clock())
        return Metrics(sentPackets, suppressedPackets, sentBytes, lastPacketsPerSecond, lastBytesPerSecond,fullSnapshots,deltaPackets,componentsReplicated,replicationFlushes,noOpFlushes,if(deltaPackets==0L)0 else deltaBytes/deltaPackets,maximumDeltaBytes)
    }

    private fun rollWindow(now: Long) {
        val elapsed = now - windowStartedAt
        if (elapsed < 1_000L) return
        lastPacketsPerSecond = windowPackets * 1_000L / elapsed.coerceAtLeast(1L)
        lastBytesPerSecond = windowBytes * 1_000L / elapsed.coerceAtLeast(1L)
        windowPackets = 0L
        windowBytes = 0L
        windowStartedAt = now
    }
}
