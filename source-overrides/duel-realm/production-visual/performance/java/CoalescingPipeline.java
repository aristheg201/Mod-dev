package vn.svarcade.tcg.performance;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
/** Owner-thread map, one running + one replaceable derived input per key. No completion callbacks touch game state. */
public final class CoalescingPipeline<K,I,O> {
 private final CardWorldsExecutors workers;private final CardWorldsExecutors.Kind kind;
 private final Function<I,O> compute;private final BinaryOperator<I> merge;private final Map<K,Slot<I,O>> slots=new LinkedHashMap<>();
 private static final class Slot<I,O>{long latest,active;int updates;I input;CompletableFuture<O> future;}
 public CoalescingPipeline(CardWorldsExecutors workers,CardWorldsExecutors.Kind kind,Function<I,O> compute,BinaryOperator<I> merge){this.workers=workers;this.kind=kind;this.compute=compute;this.merge=merge;}
 public void offer(K key,long revision,I input,BiConsumer<K,O> commit){var old=slots.get(key);if(old!=null&&old.updates>=64)flush(key,commit,(k,error)->{throw new IllegalStateException("Derived work failed under backpressure key="+k,error);});offer(key,revision,input);}
 public void flush(K key,BiConsumer<K,O> commit,BiConsumer<K,Throwable> error){var s=slots.remove(key);if(s==null)return;if(s.future!=null)s.future.cancel(false);try{commit.accept(key,compute.apply(s.input));}catch(Throwable ex){error.accept(key,ex);}}
 public void offer(K key,long revision,I input){Slot<I,O> s=slots.computeIfAbsent(key,k->new Slot<>());if(s.input!=null)input=merge.apply(s.input,input);s.input=input;s.latest=revision;s.updates++;}
 public void poll(BiConsumer<K,O> commit,BiConsumer<K,Throwable> error){for(var it=slots.entrySet().iterator();it.hasNext();){var e=it.next();var s=e.getValue();if(s.future!=null&&s.future.isDone()){
   try{O result=s.future.join();if(s.active==s.latest){commit.accept(e.getKey(),result);it.remove();continue;}}
   catch(CancellationException ignored){}
   catch(CompletionException ex){error.accept(e.getKey(),ex.getCause()); // Correct synchronous fallback uses the newest immutable input.
    try{commit.accept(e.getKey(),compute.apply(s.input));it.remove();continue;}catch(Throwable failure){error.accept(e.getKey(),failure);it.remove();continue;}}
   s.future=null;
  }
  if(s.future==null){s.active=s.latest;I captured=s.input;s.future=workers.submit(kind,new CardWorldsExecutors.Context("derived",e.getKey().toString(),s.active),()->compute.apply(captured));}
 }}
 public void cancel(K key){var s=slots.remove(key);if(s!=null&&s.future!=null)s.future.cancel(false);}
 public void clear(){for(var key:List.copyOf(slots.keySet()))cancel(key);}
 public int owners(){return slots.size();}
}
