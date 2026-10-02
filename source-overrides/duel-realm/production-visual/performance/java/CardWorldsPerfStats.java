package vn.svarcade.tcg.performance;
import java.util.*;
import java.util.concurrent.atomic.*;
/** Fixed memory per hot path. Sampling and periodic reporting are opt-in. */
public final class CardWorldsPerfStats {
 public enum Path { DUEL_TICK,EFFECT_OPERATION,ACTION_VALIDATION,TRIGGER_LOOKUP,TARGET_SELECTION,CHAIN_BUILD,CHAIN_RESOLVE,CONTINUOUS,AI_PLAN,SNAPSHOT_BUILD,SNAPSHOT_SERIALIZE,SNAPSHOT_COMPRESS,DATABASE,CATALOG_LOOKUP,PLACEHOLDER,MATCHMAKING,CARDSTORE,ECONOMY,CLIENT_DECODE,COLLECTION_FILTER,CARD_PREPARE,VFX_UPDATE }
 public record Sample(long count,long totalNanos,long maxNanos,long p50,long p95,long p99) {
  public double averageMicros(){return count==0?0:totalNanos/(count*1000.0);}
 }
 private static final int SIZE=4096;
 private static final class Metric {
  final LongAdder count=new LongAdder(),total=new LongAdder();final AtomicLong max=new AtomicLong(),cursor=new AtomicLong();final AtomicLongArray samples=new AtomicLongArray(SIZE);
  void add(long n){count.increment();total.add(n);max.accumulateAndGet(n,Math::max);samples.set((int)(cursor.getAndIncrement()%SIZE),n);}
  Sample read(){int n=(int)Math.min(cursor.get(),SIZE);long[] a=new long[n];for(int i=0;i<n;i++)a[i]=samples.get(i);Arrays.sort(a);return new Sample(count.sum(),total.sum(),max.get(),pct(a,.50),pct(a,.95),pct(a,.99));}
  static long pct(long[] a,double p){return a.length==0?0:a[Math.min(a.length-1,(int)Math.ceil(a.length*p)-1)];}
 }
 private static volatile boolean enabled=Boolean.getBoolean("cardworlds.perf");
 private static volatile Metric[] metrics=create();
 private static Metric[] create(){Metric[] a=new Metric[Path.values().length];Arrays.setAll(a,i->new Metric());return a;}
 public static void enable(boolean value){enabled=value;}public static boolean enabled(){return enabled;}
 public static long start(){return enabled?System.nanoTime():0;}
 public static void finish(Path path,long start){if(start!=0)metrics[path.ordinal()].add(Math.max(0,System.nanoTime()-start));}
 public static void operation(){if(enabled)metrics[Path.EFFECT_OPERATION.ordinal()].add(0);}
 public static Sample sample(Path path){return metrics[path.ordinal()].read();}
 public static void reset(){metrics=create();}
 public static String summary(){var t=sample(Path.DUEL_TICK);return String.format(Locale.ROOT,"tick avg=%.3fms p95=%.3fms p99=%.3fms",t.averageMicros()/1000,t.p95()/1e6,t.p99()/1e6);}
 private CardWorldsPerfStats(){}
}
