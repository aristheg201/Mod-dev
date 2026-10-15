package dev.svextract.performance;

import java.util.Arrays;

/** Fixed-capacity sample rings. Tick recording does not allocate. Sorting happens only on reports/admission. */
public final class Telemetry {
    private final long[] ticks;
    private final long[] cadence;
    private int cursor, size;
    private long stalls;
    private long packets, packetBytes, aiNanos, pathRequests, blockChanges;
    private long actorShots;
    private long hostileShots,allyShots,companionShots,actorDamageEvents;
    private long pointBlankRequests,pointBlankNanos,pointBlankMaxNanos;
    public Telemetry(int capacity) { ticks=new long[capacity];cadence=new long[capacity]; }
    public void tick(long nanos) { tick(nanos,50_000_000L); }
    public void tick(long nanos,long intervalNanos) { ticks[cursor]=nanos;cadence[cursor]=intervalNanos;cursor=(cursor+1)%ticks.length;size=Math.min(size+1,ticks.length);if(nanos>100_000_000L)stalls++; }
    public void packet(int estimatedBytes) { packets++; packetBytes+=estimatedBytes; }
    public void ai(long nanos,int paths) { aiNanos+=nanos; pathRequests+=paths; }
    public void blocks(int count) { blockChanges+=count; }
    public void actorShot(){actorShots++;}
    public void actorShot(boolean hostile,boolean companion,boolean damaged){
        actorShots++;if(hostile)hostileShots++;else if(companion)companionShots++;else allyShots++;
        if(damaged)actorDamageEvents++;
    }
    public void pointBlankRequest(long nanos){pointBlankRequests++;pointBlankNanos+=nanos;pointBlankMaxNanos=Math.max(pointBlankMaxNanos,nanos);}
    public java.util.Map<String,Object> combatSnapshot(){return java.util.Map.of(
        "hostileShots",hostileShots,"allyShots",allyShots,"companionShots",companionShots,"actorDamageEvents",actorDamageEvents,
        "pointBlankPlayerFireRequests",pointBlankRequests,"pointBlankPlayerFireTotalMs",pointBlankNanos/1e6,
        "pointBlankPlayerFireMaxMs",pointBlankMaxNanos/1e6);}
    public record Stats(int samples,double tps,double p50Ms,double p95Ms,double p99Ms,long stalls,
                        long payloads,long payloadBytesEstimated,long aiNanos,long pathRequests,long blockChanges,long actorShots,long usedHeapBytes) {}
    public Stats snapshot() {
        long[] sorted=Arrays.copyOf(ticks,size); Arrays.sort(sorted);
        double averageInterval=Arrays.stream(Arrays.copyOf(cadence,size)).average().orElse(50_000_000L);
        Runtime r=Runtime.getRuntime();
        return new Stats(size,Math.min(20,1_000_000_000.0/Math.max(1,averageInterval)),
                percentile(sorted,.50),percentile(sorted,.95),percentile(sorted,.99),stalls,packets,packetBytes,
                aiNanos,pathRequests,blockChanges,actorShots,r.totalMemory()-r.freeMemory());
    }
    private static double percentile(long[] a,double p) { return a.length==0?0:a[(int)Math.ceil(a.length*p)-1]/1_000_000.0; }
}
