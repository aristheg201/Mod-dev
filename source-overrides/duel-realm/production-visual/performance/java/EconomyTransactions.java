package vn.svarcade.tcg.performance;
import java.util.function.*;
/** Serializes a receipt's debit + durable CardStore commit. Production callers stay on the required economy thread. */
public final class EconomyTransactions {
 private static final Object[] LOCKS=new Object[64];static {java.util.Arrays.setAll(LOCKS,i->new Object());}
 public static <T>T pull(String owner,String receipt,BooleanSupplier replay,Runnable charge,Supplier<T> commit,Runnable refund){synchronized(LOCKS[(owner.hashCode()&Integer.MAX_VALUE)%LOCKS.length]){boolean existing=replay.getAsBoolean();if(!existing)charge.run();try{return commit.get();}catch(RuntimeException e){if(!existing)refund.run();throw e;}}}
 private EconomyTransactions(){}
}
