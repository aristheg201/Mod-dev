package vn.svarcade.tcg.performance;
import vn.svarcade.tcg.data.Catalog;
import java.util.*;
/** Four immutable generations maximum. A reload swaps definitions; running duels retain their old generation. */
public final class CatalogIndex {
 private static final LinkedHashMap<Map<String,Catalog.Card>,CatalogIndex> CACHE=new LinkedHashMap<>();
 public final CompiledEffects effects;public final Map<String,Catalog.Card> byName;public final List<String> rarities,sets;
 private CatalogIndex(Map<String,Catalog.Card> definitions){effects=new CompiledEffects(definitions);Map<String,Catalog.Card> names=new LinkedHashMap<>();TreeSet<String> rarity=new TreeSet<>(),set=new TreeSet<>();for(var c:definitions.values()){names.putIfAbsent(c.name(),c);rarity.add(c.rarity());set.add(c.set());}byName=Collections.unmodifiableMap(names);rarities=options(rarity);sets=options(set);}
 private static List<String> options(Set<String> values){List<String> result=new ArrayList<>(List.of("All"));result.addAll(values);return List.copyOf(result);}
 public static synchronized CatalogIndex of(Map<String,Catalog.Card> definitions){for(var e:CACHE.entrySet())if(e.getKey()==definitions)return e.getValue();var value=new CatalogIndex(definitions);if(CACHE.size()==4)CACHE.remove(CACHE.keySet().iterator().next());CACHE.put(definitions,value);return value;}
 public static synchronized int size(){return CACHE.size();}
 public static synchronized void clear(){CACHE.clear();}
}
