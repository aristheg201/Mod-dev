package vn.svarcade.tcg.data;

import com.google.gson.*;
import vn.svarcade.tcg.integration.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.BiPredicate;

/** Optional cards are ordinary catalog citizens only after their real provider requirements pass. */
public final class SpecialAspectCards {
    public record Definition(String id,String species,List<String> requiredAspects,List<String> requiresMods,String displayNameKey,String rarity,List<String> tags,Catalog.Effect effect,List<String> packPools,List<String> deckTags,int power,int level) {}
    private static final List<Definition> DEFINITIONS=load();
    public static List<Definition> definitions(){return DEFINITIONS;}
    private static List<Definition> load(){try(var in=SpecialAspectCards.class.getResourceAsStream("/data/svarcade_tcg/special_aspect_cards.json")){
        if(in==null)return List.of();return List.of(new Gson().fromJson(new InputStreamReader(in,StandardCharsets.UTF_8),Definition[].class));
    }catch(IOException e){throw new IllegalStateException(e);}}
    public static Catalog apply(Catalog base,IntegrationCapabilities capabilities,ProviderAspects aspects,BiPredicate<String,List<String>> speciesAvailable){
        Map<String,Catalog.Card> cards=new LinkedHashMap<>(base.cards());List<Catalog.Weighted> pool=new ArrayList<>();
        for(var d:DEFINITIONS){
            try{d.effect().spec().validate();}catch(RuntimeException e){throw new IllegalArgumentException(d.id()+": effect: "+e.getMessage(),e);}
            if(!capabilities.mods().containsAll(d.requiresMods())||aspects.find(d.species(),d.requiredAspects()).isEmpty()||!speciesAvailable.test(d.species(),d.requiredAspects()))continue;
            String family=String.join(" ",d.tags());
            cards.put(d.id(),new Catalog.Card(d.id(),d.displayNameKey(),"pokemon",ProviderAspects.species(d.species()),d.requiredAspects(),d.tags().stream().filter(t->t.startsWith("type:")).map(t->t.substring(5)).findFirst().orElse("normal"),family,"",false,d.level(),d.power(),"@effect","Provider Aspects",d.rarity(),List.of("PACK"),d.effect(),List.of(),List.of()));
            pool.add(new Catalog.Weighted(d.id(),1,true,true));
        }
        Map<String,Catalog.Banner> banners=new LinkedHashMap<>(base.banners());
        if(!pool.isEmpty())banners.put("special_aspects",new Catalog.Banner("cardworlds.pack.special_aspects","special","PACK",1200,5,80,60,3,0,Long.MAX_VALUE,List.copyOf(pool)));
        return new Catalog(base.rules(),Map.copyOf(cards),Map.copyOf(banners),base.rewards(),base.dealers(),base.starters());
    }
    private SpecialAspectCards(){}
}
