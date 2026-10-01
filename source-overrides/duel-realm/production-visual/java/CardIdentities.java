package vn.svarcade.tcg.data;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Data supplies the vocabulary. Stable catalog order chooses distinct executable compositions. */
public final class CardIdentities {
    public record Binding(String self,List<String> sourceZones,boolean family,boolean types,int speed,List<EffectSpec.Condition> conditions) {}
    public record Recipes(List<EffectSpec.Operation> setup,List<EffectSpec.Operation> payoff,List<String> shapes,Map<String,Binding> bindings) {}
    private static final Recipes RECIPES=load();
    private static Recipes load(){try(var in=CardIdentities.class.getResourceAsStream("/data/svarcade_tcg/identity_recipes.json")){
        return new Gson().fromJson(new InputStreamReader(Objects.requireNonNull(in),StandardCharsets.UTF_8),Recipes.class);
    }catch(IOException e){throw new IllegalStateException(e);}}
    private static EffectSpec.Condition bindCondition(EffectSpec.Condition c,Binding binding){if(c==null)return null;
        return new EffectSpec.Condition(c.type(),"SELF".equals(c.target())?binding.self():c.target(),c.value(),c.amount(),EffectSpec.list(c.children()).stream().map(child->bindCondition(child,binding)).toList());
    }
    private static EffectSpec.Operation typed(EffectSpec.Operation op,Catalog.Card card){
        Binding binding=Objects.requireNonNull(RECIPES.bindings().get(card.category()),"Unconfigured card category "+card.category());
        var f=op.filter();if(f!=null)f=new EffectSpec.Filter(f.category(),EffectSpec.list(f.types()).contains("$type")?(binding.types()?List.of(card.type()):List.of()):f.types(),EffectSpec.list(f.tags()).contains("$family")?(binding.family()?List.of(card.family()):List.of()):f.tags(),f.minLevel(),f.maxLevel(),f.minPower(),f.maxPower(),f.position(),f.zone(),f.controller(),f.faceUp());
        return new EffectSpec.Operation(op.type(),op.amount(),"SELF".equals(op.target())?binding.self():op.target(),op.zone(),op.duration(),op.flags(),EffectSpec.list(op.children()).stream().map(child->typed(child,card)).toList(),bindCondition(op.condition(),binding),EffectSpec.list(op.otherwise()).stream().map(child->typed(child,card)).toList(),f);
    }
    public static Catalog apply(Catalog catalog){
        Map<String,Catalog.Card> cards=new LinkedHashMap<>(catalog.cards());int index=0;
        for(var card:cards.values().stream().sorted(Comparator.comparing(Catalog.Card::id)).toList()){
            if(card.effect()==null)throw new IllegalStateException("No executable identity for "+card.id());
            if(card.effect().spec()!=null&&EffectSpec.list(card.effect().spec().stages()).stream().anyMatch(stage->!stage.id().equals("signature")))continue;
            int value=index++,a=value%RECIPES.setup().size(),b=value/RECIPES.setup().size()%RECIPES.payoff().size(),c=value/(RECIPES.setup().size()*RECIPES.payoff().size());
            if(c>=RECIPES.setup().size())throw new IllegalStateException("Identity recipe budget exceeded; author more recipes");
            var first=typed(RECIPES.setup().get(a),card);var second=typed(RECIPES.payoff().get(b),card);var third=typed(RECIPES.setup().get(c),card);
            var original=card.effect();var old=original.spec();
            var vfx=old==null?EffectContent.attackPresentation(card):old.vfx();if(vfx==null)vfx=EffectContent.attackPresentation(card);
            List<EffectSpec.VisualStage> visuals=new ArrayList<>();int code=value;
            for(int i=0;i<3;i++){String shape=RECIPES.shapes().get(code%RECIPES.shapes().size());code/=RECIPES.shapes().size();visuals.add(new EffectSpec.VisualStage(i==0?"CHARGE":i==1?"CAST_STATUS":"IMPACT",shape,vfx.profile(),.18+i*.27));}
            var presentation=new EffectSpec.Presentation(vfx.mode(),vfx.profile(),vfx.duration(),vfx.animation(),vfx.particle(),vfx.fallback(),vfx.shape(),vfx.sound(),visuals);
            var cost=new EffectSpec.Cost("LP_COST",300+Math.min(card.level(),8)*50,"SELF",null);
            var binding=RECIPES.bindings().get(card.category());var conditions=new ArrayList<>(EffectSpec.list(binding.conditions()));conditions.add(new EffectSpec.Condition("OR",null,null,0,binding.sourceZones().stream().map(zone->new EffectSpec.Condition("SOURCE_ZONE","SELF",zone,0,List.<EffectSpec.Condition>of())).toList()));
            var spec=new EffectSpec(List.of("ON_ACTIVATE"),List.copyOf(conditions),List.of(cost),null,List.of(first,second,third),false,false,null,presentation,List.of(),"CARD_NAME");
            int speed=binding.speed()==0?original.speed():binding.speed();var identity=new Catalog.Effect("composite",0,speed,0,"none",speed==1?List.of("MAIN1","MAIN2"):List.of("DRAW","STANDBY","MAIN1","BATTLE","MAIN2","END"),true,spec);
            var stage=new EffectSpec.Stage("signature",identity,RECIPES.bindings().get(card.category()).sourceZones(),false);
            var root=new EffectSpec(old==null?List.of("ON_ACTIVATE"):old.triggers(),old==null?List.of():old.conditions(),old==null?List.of():old.costs(),old==null?null:old.targets(),old==null?List.of():old.operations(),old!=null&&old.oncePerDuel(),old!=null&&old.optional(),old==null?null:old.textKey(),presentation,List.of(stage),old==null?null:old.limitScope());
            var effect=new Catalog.Effect(original.operation(),original.amount(),original.speed(),original.lifeCost(),original.target(),original.phases(),original.oncePerTurn(),root);
            cards.put(card.id(),new Catalog.Card(card.id(),card.name(),card.category(),card.species(),card.aspects(),card.type(),card.family(),card.evolvesFrom(),card.extra(),card.level(),card.power(),card.text(),card.set(),card.rarity(),card.sources(),effect,card.triggers(),card.modifiers()));
        }
        return new Catalog(catalog.rules(),Map.copyOf(cards),catalog.banners(),catalog.rewards(),catalog.dealers(),catalog.starters());
    }
    private static JsonElement fingerprint(JsonElement value){
        if(value.isJsonObject()){var object=value.getAsJsonObject();object.remove("vfx");object.remove("textKey");
            for(String key:new ArrayList<>(object.keySet())){if(key.equals("amount")){int n=object.get(key).getAsInt();object.addProperty(key,Integer.signum(n));}else fingerprint(object.get(key));}
        }else if(value.isJsonArray())value.getAsJsonArray().forEach(CardIdentities::fingerprint);
        return value;
    }
    public static void validateUniqueness(Catalog catalog){
        Set<String> mechanics=new HashSet<>(),visuals=new HashSet<>();Gson json=new Gson();
        for(var c:catalog.cards().values()){
            if(c.effect()==null||c.effect().spec()==null)throw new IllegalArgumentException(c.id()+": effect is missing");
            var spec=c.effect().spec();
            if(!mechanics.add(fingerprint(json.toJsonTree(List.of(c.effect().operation(),EffectSpec.list(spec.operations()),EffectSpec.list(spec.stages())))).toString()))throw new IllegalArgumentException(c.id()+": duplicate executable effect graph");
            if(!visuals.add(json.toJson(spec.vfx())))throw new IllegalArgumentException(c.id()+": duplicate VFX choreography");
        }
    }
    private CardIdentities(){}
}
