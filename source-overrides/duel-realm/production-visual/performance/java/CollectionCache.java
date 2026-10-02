package vn.svarcade.tcg.performance;
import vn.svarcade.tcg.data.Catalog;
import java.util.*;
import java.util.function.Function;
/** One filtered list per screen; repeated frames reuse it without catalog scans or sort allocations. */
public final class CollectionCache {
 private Map<String,Catalog.Card> definitions;private Map<String,Integer> counts;private Set<String> favorites=Set.of();private List<String> key=List.of();private String language="";private List<Catalog.Card> result=List.of();private long rebuilds;
 public List<Catalog.Card> get(Map<String,Catalog.Card> defs,Map<String,Integer> owned,Set<String> fav,List<String> filters,String lang,Function<Catalog.Card,String> name){if(definitions==defs&&counts==owned&&favorites.equals(fav)&&key.equals(filters)&&language.equals(lang))return result;
  long n=CardWorldsPerfStats.start();try{definitions=defs;counts=owned;favorites=Set.copyOf(fav);key=List.copyOf(filters);language=lang;String search=filters.get(0).toLowerCase(Locale.ROOT),category=filters.get(1),rarity=filters.get(2),set=filters.get(3),ownership=filters.get(4);List<Catalog.Card> cards=new ArrayList<>();for(var c:defs.values()){int count=owned.getOrDefault(c.id(),0);if(!name.apply(c).toLowerCase(Locale.ROOT).contains(search)||!category.equals("all")&&!c.category().equals(category)||!rarity.equals("All")&&!c.rarity().equals(rarity)||!set.equals("All")&&!c.set().equals(set))continue;if(ownership.equals("All")||ownership.equals("Owned")&&count>0||ownership.equals("Missing")&&count==0||ownership.equals("Favorites")&&fav.contains(c.id()))cards.add(c);}cards.sort(Comparator.comparing((Catalog.Card c)->owned.getOrDefault(c.id(),0)==0).thenComparing(Catalog.Card::name));result=List.copyOf(cards);rebuilds++;return result;}finally{CardWorldsPerfStats.finish(CardWorldsPerfStats.Path.COLLECTION_FILTER,n);}}
 public long rebuilds(){return rebuilds;}
 public static int first(int offset,int stride,int columns){return Math.max(0,offset/stride)*columns;}
 public static int last(int offset,int height,int stride,int columns,int size){return Math.min(size,((offset+height+stride-1)/stride+1)*columns);}
}
