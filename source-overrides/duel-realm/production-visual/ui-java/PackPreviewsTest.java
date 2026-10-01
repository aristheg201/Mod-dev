package vn.svarcade.tcg.data;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class PackPreviewsTest {
 Catalog bannerCatalog(Map<String,Catalog.Banner> banners) throws Exception {
  var base=Catalog.load(Path.of("no-preview-test-override.json"));return new Catalog(base.rules(),base.cards(),banners,base.rewards(),base.dealers(),base.starters());
 }
 Catalog.Banner banner(String...ids){return new Catalog.Banner("Example","example","PACK",100,5,80,60,3,0,Long.MAX_VALUE,Arrays.stream(ids).map(id->new Catalog.Weighted(id,1,true,true)).toList());}
 @Test void differentPoolsUseTheirOwnPokemonAndSharedPoolsAvoidRepeatedMascots() throws Exception {
  var c=bannerCatalog(Map.of("a",banner("charizard","blastoise"),"b",banner("charizard","blastoise"),"c",banner("gengar")));
  var before=new com.google.gson.Gson().toJson(c.banners());var previews=PackPreviews.select(c);
  assertNotEquals(previews.get("a"),previews.get("b"));assertEquals("gengar",previews.get("c"));
  for(var e:previews.entrySet())assertTrue(c.banners().get(e.getKey()).pool().stream().anyMatch(w->w.card().equals(e.getValue())));
  assertEquals(before,new com.google.gson.Gson().toJson(c.banners()));
 }
 @Test void iterationOrderDoesNotChangeArtAndSupportOnlyPacksGetARealMascot() throws Exception {
  var first=new LinkedHashMap<String,Catalog.Banner>();first.put("b",banner("gengar"));first.put("a",banner("charizard"));first.put("support",banner("research"));
  var second=new TreeMap<>(first);var c=bannerCatalog(first);var result=PackPreviews.select(c);
  assertEquals(result,PackPreviews.select(bannerCatalog(second)));assertEquals("pokemon",c.card(result.get("support")).category());
 }
 @Test void aspectPreviewRetainsTheActualCatalogCard() throws Exception {
  var c=bannerCatalog(Map.of("mega",banner("mega_charizard")));String id=PackPreviews.select(c).get("mega");
  assertEquals("mega_charizard",id);assertFalse(c.card(id).aspects().isEmpty());
 }
 @Test void authoredMascotIsValidatedAgainstRealPackContents() throws Exception {
  var c=bannerCatalog(Map.of("a",banner("charizard","blastoise"),"b",banner("gengar")));
  var result=PackPreviews.select(c,Map.of("a","blastoise","b","missing_provider_card"));
  assertEquals("blastoise",result.get("a"));assertEquals("gengar",result.get("b"));
  assertEquals("gengar",PackPreviews.select(c,Map.of("b","charizard")).get("b"));
 }
}
