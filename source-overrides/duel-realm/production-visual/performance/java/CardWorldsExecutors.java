package vn.svarcade.tcg.performance;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
/** Owned, bounded pools. Derived work falls back correctly on the submitting thread under pressure. */
public final class CardWorldsExecutors implements AutoCloseable {
 public enum Kind { CPU,IO,SERIALIZATION }
 public record Context(String type,String duelId,long revision){}
 private final ThreadPoolExecutor[] pools=new ThreadPoolExecutor[3];private final AtomicBoolean closed=new AtomicBoolean();
 private final AtomicLongArray rejected=new AtomicLongArray(3),highWater=new AtomicLongArray(3);
 public CardWorldsExecutors(PerformanceConfig c){int[] sizes={c.cpuWorkers(),c.ioConcurrency(),c.serializationWorkers()},caps={c.maxQueuedCpuTasks(),c.maxQueuedIoTasks(),c.maxQueuedSerializationTasks()};for(Kind k:Kind.values()){int i=k.ordinal();AtomicInteger seq=new AtomicInteger();pools[i]=new ThreadPoolExecutor(sizes[i],sizes[i],0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(caps[i]),r->{Thread t=new Thread(r,"cardworlds-"+k.name().toLowerCase()+"-"+seq.incrementAndGet());t.setDaemon(true);return t;},(r,p)->{rejected.incrementAndGet(i);if(p.isShutdown())throw new RejectedExecutionException("Card Worlds workers stopped");r.run();});}}
 public <T> CompletableFuture<T> submit(Kind kind,Context context,Supplier<T> work){CompletableFuture<T> result=new CompletableFuture<>();if(closed.get()){result.completeExceptionally(new RejectedExecutionException("Card Worlds workers stopped: "+context));return result;}
  try{pools[kind.ordinal()].execute(()->{if(result.isCancelled())return;try{result.complete(work.get());}catch(Throwable e){result.completeExceptionally(new TaskFailure(context,e));}});highWater.accumulateAndGet(kind.ordinal(),pools[kind.ordinal()].getQueue().size(),Math::max);}catch(RuntimeException e){result.completeExceptionally(new TaskFailure(context,e));}return result;
 }
 public static final class TaskFailure extends RuntimeException {public final Context context;TaskFailure(Context c,Throwable e){super("Card Worlds task failed type="+c.type()+" duel="+c.duelId()+" revision="+c.revision(),e);context=c;}}
 public int pending(Kind k){return pools[k.ordinal()].getQueue().size();}public long maxDepth(Kind k){return highWater.get(k.ordinal());}public long backpressure(Kind k){return rejected.get(k.ordinal());}
 public String summary(){return "CPU="+pending(Kind.CPU)+" IO="+pending(Kind.IO)+" snapshots="+pending(Kind.SERIALIZATION)+" backpressure="+(rejected.get(0)+rejected.get(1)+rejected.get(2));}
 @Override public void close(){if(!closed.compareAndSet(false,true))return;for(var p:pools)p.shutdown();boolean interrupted=false;for(var p:pools)try{if(!p.awaitTermination(10,TimeUnit.SECONDS))throw new IllegalStateException("Card Worlds workers did not drain: "+p);}catch(InterruptedException e){interrupted=true;throw new IllegalStateException("Interrupted flushing Card Worlds workers",e);}finally{if(interrupted)Thread.currentThread().interrupt();}}
}
