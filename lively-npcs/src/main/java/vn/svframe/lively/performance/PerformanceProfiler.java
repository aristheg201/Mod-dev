package vn.svframe.lively.performance;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class PerformanceProfiler {
    public record Metric(long calls,long totalNanos,long maxNanos,long rejected){public double averageMicros(){return calls==0?0D:(totalNanos/1000D)/calls;}}
    private static final class Mutable{final AtomicLong calls=new AtomicLong(),total=new AtomicLong(),max=new AtomicLong(),rejected=new AtomicLong();}
    private final ConcurrentHashMap<String,Mutable> metrics=new ConcurrentHashMap<>();
    public <T> T measure(String key, java.util.function.Supplier<T> work){long s=System.nanoTime();try{return work.get();}finally{record(key,System.nanoTime()-s,false);}}
    public void record(String key,long nanos,boolean rejected){Mutable m=metrics.computeIfAbsent(key,k->new Mutable());m.calls.incrementAndGet();m.total.addAndGet(Math.max(0,nanos));m.max.accumulateAndGet(Math.max(0,nanos),Math::max);if(rejected)m.rejected.incrementAndGet();}
    public void clear(){metrics.clear();}
    public Map<String,Metric> snapshot(){java.util.HashMap<String,Metric> r=new java.util.HashMap<>();metrics.forEach((k,m)->r.put(k,new Metric(m.calls.get(),m.total.get(),m.max.get(),m.rejected.get())));return Map.copyOf(r);}
}
