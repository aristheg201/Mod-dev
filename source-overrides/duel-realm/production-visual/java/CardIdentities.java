package vn.svarcade.tcg.data;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Card gameplay is an exact content lookup. Existing presentation remains independent. */
public final class CardIdentities {
    private record PresentationRecipes(List<String> shapes) {}
    private static final PresentationRecipes RECIPES=load();
    private static PresentationRecipes load(){try(var in=CardIdentities.class.getResourceAsStream("/data/svarcade_tcg/identity_recipes.json")){
        return new Gson().fromJson(new InputStreamReader(Objects.requireNonNull(in),StandardCharsets.UTF_8),PresentationRecipes.class);
    }catch(IOException e){throw new IllegalStateException(e);}}
    public static Catalog apply(Catalog catalog){
        Map<String,Catalog.Card> cards=new LinkedHashMap<>(catalog.cards());int index=0;
        for(var card:cards.values().stream().sorted(Comparator.comparing(Catalog.Card::id)).toList()){
            // Provider-authored special cards already carry their own complete mechanics and presentation.
            if(card.id().startsWith("special_")&&card.effect()!=null&&card.effect().spec()!=null)continue;
            if(CardGameplayEffects.definition(card)==null&&card.effect()!=null&&card.effect().spec()!=null&&EffectSpec.list(card.effect().spec().stages()).stream().anyMatch(stage->!stage.id().equals("signature")))continue;
            int value=index++;
            var original=card.effect();var old=original==null?null:original.spec();
            var vfx=old==null?EffectContent.attackPresentation(card):old.vfx();if(vfx==null)vfx=EffectContent.attackPresentation(card);
            String profile=EffectSpec.value(vfx.profile(),EffectContent.attackPresentation(card).profile());
            List<EffectSpec.VisualStage> visuals=new ArrayList<>();int code=value;
            for(int i=0;i<3;i++){String shape=RECIPES.shapes().get(code%RECIPES.shapes().size());code/=RECIPES.shapes().size();visuals.add(new EffectSpec.VisualStage(i==0?"CHARGE":i==1?"CAST_STATUS":"IMPACT",shape,profile,.18+i*.27));}
            var presentation=new EffectSpec.Presentation(vfx.mode(),vfx.profile(),vfx.duration(),vfx.animation(),vfx.particle(),vfx.fallback(),vfx.shape(),vfx.sound(),visuals);
            var effect=CardGameplayEffects.identity(card,presentation);
            cards.put(card.id(),new Catalog.Card(card.id(),card.name(),card.category(),card.species(),card.aspects(),card.type(),card.family(),card.evolvesFrom(),card.extra(),card.level(),card.power(),"@effect",card.set(),card.rarity(),card.sources(),effect,card.triggers(),card.modifiers()));
        }
        return new Catalog(catalog.rules(),Map.copyOf(cards),catalog.banners(),catalog.rewards(),catalog.dealers(),catalog.starters());
    }
    /** Ignore cosmetics and numeric tuning. Costs, conditions, selectors and operation topology matter. */
    private static JsonElement fingerprint(JsonElement value){
        if(value.isJsonObject()){
            var input=value.getAsJsonObject();var out=new JsonObject();
            for(String key:new TreeSet<>(input.keySet())){
                if(Set.of("vfx","textKey","id").contains(key))continue;
                var v=input.get(key);if(v.isJsonNull()||v.isJsonArray()&&v.getAsJsonArray().isEmpty()||v.isJsonObject()&&v.getAsJsonObject().isEmpty())continue;
                if(key.equals("operations")&&v.isJsonArray()){
                    var parts=new TreeMap<String,JsonElement>();for(var part:v.getAsJsonArray()){var normalized=fingerprint(part);parts.put(normalized.toString(),normalized);}
                    var array=new JsonArray();parts.values().forEach(array::add);out.add(key,array);continue;
                }
                if(Set.of("types","tags").contains(key)){var array=new JsonArray();array.add("typed-filter");out.add(key,array);continue;}
                if(key.equals("value")&&input.has("type")&&Set.of("TYPE","TAG").contains(input.get("type").getAsString())){out.addProperty(key,"typed-condition");continue;}
                if(Set.of("amount","lifeCost","minLevel","maxLevel","minPower","maxPower","max","count").contains(key)&&v.isJsonPrimitive()){
                    try{out.addProperty(key,Integer.signum(v.getAsInt()));continue;}catch(NumberFormatException ignored){}
                }
                if(Set.of("counter","memory").contains(key)||key.equals("value")&&input.has("type")&&input.get("type").getAsString().startsWith("COUNTER_")){out.addProperty(key,"named-slot");continue;}
                out.add(key,fingerprint(v));
            }
            return out;
        }
        if(value.isJsonArray()){var a=new JsonArray();for(var v:value.getAsJsonArray())a.add(fingerprint(v));return a;}
        return value.deepCopy();
    }
    public static String mechanics(Catalog.Effect effect,boolean primaryOnly){
        var data=new Gson().toJsonTree(effect);if(primaryOnly&&data.getAsJsonObject().has("spec"))data.getAsJsonObject().getAsJsonObject("spec").remove("stages");
        return fingerprint(data).toString();
    }
    public static void validateUniqueness(Catalog catalog){
        Map<String,String> mechanics=new HashMap<>(),primary=new HashMap<>();int external=0;
        for(var c:catalog.cards().values()){
            if(c.effect()==null||c.effect().spec()==null)throw new IllegalArgumentException(c.id()+": effect is missing");
            // External addon species can supply authored effects without changing this mod's official registry.
            if(CardGameplayEffects.definition(c)==null&&!c.id().startsWith("special_")&&c.category().equals("pokemon")){
                external++;continue;
            }
            String duplicate=mechanics.putIfAbsent(mechanics(c.effect(),false),c.id());
            if(duplicate!=null)throw new IllegalArgumentException(c.id()+": duplicate executable effect graph of "+duplicate);
            duplicate=primary.putIfAbsent(mechanics(c.effect(),true),c.id());
            if(duplicate!=null)throw new IllegalArgumentException(c.id()+": duplicate primary gameplay effect of "+duplicate);
        }
        org.slf4j.LoggerFactory.getLogger("cardworlds-gameplay").info("CARDWORLDS_GAMEPLAY_IDENTITIES catalog={} validated={} external={} duplicatePrimary=0 duplicateMechanics=0 ordinalRecipes=0",catalog.cards().size(),primary.size(),external);
    }
    private CardIdentities(){}
}
