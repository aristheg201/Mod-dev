package vn.svarcade.tcg.performance;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import vn.svarcade.tcg.data.*;
import vn.svarcade.tcg.fabric.HeadlessCatalog;
import static org.junit.jupiter.api.Assertions.*;
class PresentationPerformanceTest {
 @TempDir Path temp;
 @Test void collectionCacheInvalidatesOnlyChangedInputAndRetainsCatalog()throws Exception{Catalog c=HeadlessCatalog.load();var cache=new CollectionCache();Map<String,Integer> counts=Map.of("charizard",1);Set<String> favorites=new HashSet<>();var filters=List.of("","pokemon","All","All","All");var first=cache.get(c.cards(),counts,favorites,filters,"en_us",Catalog.Card::name);assertTrue(first.size()>=1025);for(int i=0;i<100;i++)assertSame(first,cache.get(c.cards(),counts,favorites,filters,"en_us",Catalog.Card::name));assertEquals(1,cache.rebuilds());var charizard=cache.get(c.cards(),counts,favorites,List.of("Charizard","pokemon","All","All","Owned"),"en_us",Catalog.Card::name);assertEquals(List.of("charizard"),charizard.stream().map(Catalog.Card::id).toList());favorites.add("squirtle");assertEquals(List.of("squirtle"),cache.get(c.cards(),counts,favorites,List.of("","pokemon","All","All","Favorites"),"en_us",Catalog.Card::name).stream().map(Catalog.Card::id).toList());}
 @Test void virtualRangeContainsEveryVisibleCellWithoutCatalogTraversal(){for(int offset:List.of(0,10,169,170,4000))for(int height:List.of(100,250,560)){int first=CollectionCache.first(offset,170,5),last=CollectionCache.last(offset,height,170,5,1600);assertTrue(last-first<=((height+169)/170+2)*5);for(int i=0;i<1600;i++){int y=(i/5)*170-offset;if(y<height&&y+158>0)assertTrue(i>=first&&i<last);}}}
 @Test void chunkedHistoryNeverDropsEventsAndRetainsCanonicalOrder()throws Exception{try(var workers=new CardWorldsExecutors(PerformanceConfig.defaults())){var history=new BoundedHistory<String>(String.class,"test");history.configure(workers,temp);for(int i=0;i<14000;i++)history.add("event-"+i);history.flush();assertEquals(14000,history.size());for(int i:List.of(0,4095,4096,8191,8192,13999))assertEquals("event-"+i,history.get(i));assertEquals(List.of("event-13998","event-13999"),history.subList(13998,14000));history.clear();assertEquals(0,history.size());}}
}
