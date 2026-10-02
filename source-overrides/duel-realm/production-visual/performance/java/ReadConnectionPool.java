package vn.svarcade.tcg.performance;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
/** Bounded SQLite read leases. WAL readers never hold the authoritative writer's Java monitor. */
public final class ReadConnectionPool<T extends AutoCloseable> implements AutoCloseable {
 private final Semaphore leases=new Semaphore(8,true);private final ArrayDeque<T> available=new ArrayDeque<>();private final List<T> all=new ArrayList<>();private final Supplier<T> factory;private boolean closed;
 public ReadConnectionPool(Supplier<T> factory){this.factory=factory;}
 public <R> R read(Function<T,R> read){boolean acquired=false;T value=null;try{leases.acquire();acquired=true;synchronized(this){if(closed)throw new IllegalStateException("Card store readers closed");value=available.poll();if(value==null){value=factory.get();all.add(value);}}return read.apply(value);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("Interrupted waiting for Card Worlds database read lease",e);}finally{if(value!=null)synchronized(this){available.add(value);}if(acquired)leases.release();}}
 @Override public void close(){try{if(!leases.tryAcquire(8,10,TimeUnit.SECONDS))throw new IllegalStateException("Card Worlds database reads did not drain");try{synchronized(this){closed=true;for(T value:all)try{value.close();}catch(Exception e){throw new IllegalStateException("Cannot close Card Worlds reader",e);}all.clear();available.clear();}}finally{leases.release(8);}}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("Interrupted draining Card Worlds database reads",e);}}
}
