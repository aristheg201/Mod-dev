package vn.svframe.mmocorefabric.runtime.gameplay;
import java.util.*;
public final class ProfessionRuntime {
 public record Definition(String id,int maxLevel,String curve,Set<String> sources){public Definition{id=norm(id);sources=sources.stream().map(ProfessionRuntime::norm).collect(java.util.stream.Collectors.toUnmodifiableSet());}}
 public record State(int level,double exp){public State{if(level<1||exp<0||!Double.isFinite(exp))throw new IllegalArgumentException();}}
 private final Map<String,Definition> defs=new LinkedHashMap<>(); private final Map<UUID,Map<String,State>> states=new HashMap<>();
 public synchronized void register(Definition d){if(defs.putIfAbsent(d.id(),d)!=null)throw new IllegalStateException("duplicate profession");} public synchronized void clearDefinitions(){defs.clear();}
 public synchronized State state(UUID player,String id){Definition d=require(id);return states.computeIfAbsent(player,x->new HashMap<>()).getOrDefault(d.id(),new State(1,0));}
 public synchronized int grant(UUID player,String id,String source,double amount,java.util.function.IntToDoubleFunction required){Definition d=require(id);if(!d.sources().isEmpty()&&!d.sources().contains(norm(source)))return 0;if(amount<0||!Double.isFinite(amount))throw new IllegalArgumentException();State old=state(player,id);int lvl=old.level();double exp=old.exp()+amount;int gained=0;while(lvl<d.maxLevel()){double need=required.applyAsDouble(lvl);if(need<=0||exp<need)break;exp-=need;lvl++;gained++;}states.get(player).put(d.id(),new State(lvl,exp));return gained;}
 public synchronized Map<String,State> snapshot(UUID player){return Map.copyOf(states.getOrDefault(player,Map.of()));} public synchronized void restore(UUID player,Map<String,State> snapshot){Map<String,State> copy=new HashMap<>();snapshot.forEach((k,v)->copy.put(norm(k),v));if(copy.isEmpty())states.remove(player);else states.put(player,copy);} public synchronized void forget(UUID player){states.remove(player);} public synchronized Map<String,Definition> definitions(){return Map.copyOf(defs);}
 private Definition require(String id){Definition d=defs.get(norm(id));if(d==null)throw new IllegalArgumentException("unknown profession "+id);return d;}private static String norm(String s){return Objects.requireNonNull(s).trim().toLowerCase(Locale.ROOT);}
}
