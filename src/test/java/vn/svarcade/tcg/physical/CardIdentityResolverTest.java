package vn.svarcade.tcg.physical;

import org.junit.jupiter.api.Test;
import vn.svarcade.tcg.data.*;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CardIdentityResolverTest {
    Catalog catalog(Map<String,Catalog.Card> cards)throws Exception{var c=Catalog.load(Path.of("missing.json"));return new Catalog(c.rules(),cards,Map.of(),Map.of(),Map.of(),Map.of());}
    Catalog.Card card(String id,String species,String...aspects){return new Catalog.Card(id,id,"pokemon",species,List.of(aspects),"normal","","",false,4,1200,"","Test","Common",List.of("PACK"),null,List.of(),List.of());}
    @Test void ordinaryBaseIgnoresCosmeticGender(){var c=card("lucario","lucario");assertEquals("lucario",assertDoesNotThrow(()->new CardIdentityResolver(catalog(Map.of(c.id(),c)))).resolve("cobblemon:lucario",List.of("male")).orElseThrow().id());}
    @Test void regionalFormMostSpecificWinsAndBaseFallback()throws Exception{
        var base=card("vulpix","vulpix");var alola=card("vulpix_alola","vulpix","alolan");var variant=card("vulpix_alola_snow","vulpix","alolan","snow");
        var r=new CardIdentityResolver(catalog(Map.of(base.id(),base,alola.id(),alola,variant.id(),variant)));
        assertEquals(List.of("alolan"),r.relevantAspects("vulpix",List.of("alolan","female")));assertEquals(alola,r.resolve("vulpix",List.of("alolan","female")).orElseThrow());assertEquals(variant,r.resolve("vulpix",List.of("snow","alolan")).orElseThrow());assertEquals(base,r.resolve("vulpix",List.of("female")).orElseThrow());
    }
    @Test void ambiguityRejectedEvenWhenIterationOrderChanges()throws Exception{
        var a=card("a","lucario","mega");var b=card("b","lucario","batman");var r=new CardIdentityResolver(catalog(Map.of(a.id(),a,b.id(),b)));
        assertThrows(IllegalArgumentException.class,()->r.resolve("lucario",List.of("batman","mega")));
    }
    @Test void providerRequiredAspectsOverrideOrdinarySpecies()throws Exception{
        var d=SpecialAspectCards.definitions().stream().filter(c->c.species().equals("cobblemon:lucario")).findFirst().orElseThrow();
        var special=card(d.id(),d.species(),d.requiredAspects().toArray(String[]::new));var base=card("lucario","lucario");var r=new CardIdentityResolver(catalog(Map.of(base.id(),base,special.id(),special)));
        var aspects=new ArrayList<>(d.requiredAspects());aspects.add("male");assertEquals(special,r.resolve("lucario",aspects).orElseThrow());
    }
    @Test void batmanAspectDoesNotFallBackToOrdinaryLucario()throws Exception{
        var base=card("lucario","lucario");var special=card("special_lucario_batman","lucario","batman");
        assertEquals(special,new CardIdentityResolver(catalog(Map.of(base.id(),base,special.id(),special))).resolve("lucario",List.of("batman","male")).orElseThrow());
    }
    @Test void addonResolvesOnlyWhenRegisteredAndAbsenceIsAcceptable()throws Exception{
        var addon=card("fakemon__example__dragon","example:dragon","armored");var r=new CardIdentityResolver(catalog(Map.of(addon.id(),addon)));
        assertEquals(addon,r.resolve("example:dragon",List.of("armored")).orElseThrow());assertTrue(r.resolve("other:dragon",List.of("armored")).isEmpty());
        assertTrue(new CardIdentityResolver(catalog(Map.of())).resolve("example:dragon",List.of()).isEmpty());
    }
    @Test void registeredSpeciesBaseIsCanonicalForThemePrints()throws Exception{
        var base=card("arceus","arceus");var theme=card("arceus_judgement","arceus");
        assertEquals(base,new CardIdentityResolver(catalog(Map.of(base.id(),base,theme.id(),theme))).resolve("arceus",List.of("male")).orElseThrow());
    }
    @Test void declaredProviderIdentityReplacesOnlyItsGeneratedAlias()throws Exception{
        var d=SpecialAspectCards.definitions().stream().filter(c->c.id().equals("special_lucario_mega")).findFirst().orElseThrow();
        var declared=card(d.id(),d.species(),d.requiredAspects().toArray(String[]::new));
        var alias=card(CardIdentityResolver.registryCardId(d.species(),d.requiredAspects()),d.species(),d.requiredAspects().toArray(String[]::new));
        assertEquals(declared,new CardIdentityResolver(catalog(Map.of(declared.id(),declared,alias.id(),alias))).resolve(d.species(),d.requiredAspects()).orElseThrow());
        var competing=card("competing","lucario","mega");
        assertThrows(IllegalArgumentException.class,()->new CardIdentityResolver(catalog(Map.of(declared.id(),declared,competing.id(),competing))).resolve(d.species(),d.requiredAspects()));
    }
    @Test void malformedAspectsRejectedAndRegistryNamingIsCanonical(){
        assertThrows(IllegalArgumentException.class,()->CardIdentityResolver.validateAspects(List.of("../bad aspect")));
        assertEquals("fakemon__example__dragon__form__armored",CardIdentityResolver.registryCardId("example:dragon",List.of("armored")));
        assertEquals("pikachu",CardIdentityResolver.registryCardId("pikachu",List.of()));
        assertEquals(List.of("character-!","character-?","pa'u-style"),CardIdentityResolver.validateAspects(List.of("pa'u-style","character-?","character-!")));
    }
}
