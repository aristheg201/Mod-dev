package vn.svarcade.tcg.performance;
import java.util.concurrent.*;
/** One pending pure computation per owner. Completions never mutate world or networking state. */
public final class AsyncDerivedTask<T> {
 private long revision=-1;private CompletableFuture<T> future;
 public boolean pending(){return future!=null;}
 public long revision(){return revision;}
 public void begin(long source,CompletableFuture<T> task){cancel();revision=source;future=task;}
 public T take(long current,boolean live){if(future==null)return null;if(!live||current!=revision){cancel();return null;}if(!future.isDone())return null;var f=future;future=null;return f.join();}
 public void cancel(){if(future!=null)future.cancel(false);future=null;revision=-1;}
}
