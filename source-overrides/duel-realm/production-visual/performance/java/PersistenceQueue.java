package vn.svarcade.tcg.performance;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
/** Bounded, ordered non-gameplay persistence. Latest preferences replace obsolete flushes; shutdown drains. */
public final class PersistenceQueue<K,V> implements AutoCloseable {
 private final CardWorldsExecutors workers;private final BiConsumer<K,V> write;private final LinkedHashMap<K,V> dirty=new LinkedHashMap<>();private CompletableFuture<Void> active;private K activeKey;private V activeValue;
 public PersistenceQueue(CardWorldsExecutors workers,BiConsumer<K,V> write){this.workers=workers;this.write=write;}
 public void put(K key,V immutableValue){if(!dirty.containsKey(key)&&dirty.size()==16)flush();dirty.put(key,immutableValue);}
 public void poll(){if(active!=null){if(!active.isDone())return;active.join();if(dirty.get(activeKey)==activeValue)dirty.remove(activeKey);active=null;}if(dirty.isEmpty())return;var e=dirty.entrySet().iterator().next();activeKey=e.getKey();activeValue=e.getValue();K key=activeKey;V value=activeValue;active=workers.submit(CardWorldsExecutors.Kind.IO,new CardWorldsExecutors.Context("preferences",key.toString(),0),()->{write.accept(key,value);return null;});}
 public void flush(){while(active!=null||!dirty.isEmpty()){poll();if(active!=null)active.join();}}
 @Override public void close(){flush();}
 public int pending(){return dirty.size();}
}
