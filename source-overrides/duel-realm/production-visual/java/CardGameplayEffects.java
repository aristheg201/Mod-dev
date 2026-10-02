package vn.svarcade.tcg.data;

import com.google.gson.Gson;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Exact authored/learnset-backed identities. No type, level, ordinal or random recipe selection. */
public final class CardGameplayEffects {
    public record Definition(String name,String authorship,List<String> moves,Catalog.Effect effect) {
        public Definition { moves=moves==null?List.of():List.copyOf(moves); }
    }
    private record Content(int version,Map<String,Definition> definitions) {}
    private static final Map<String,Definition> DEFINITIONS=load();
    private static Map<String,Definition> load(){
        try(var in=CardGameplayEffects.class.getResourceAsStream("/data/svarcade_tcg/pokemon_effects.json")){
            var content=new Gson().fromJson(new InputStreamReader(Objects.requireNonNull(in),StandardCharsets.UTF_8),Content.class);
            if(content.version()!=1)throw new IllegalStateException("Unsupported card gameplay content version");
            for(var entry:content.definitions().entrySet())try{entry.getValue().effect().spec().validate();}
                catch(RuntimeException e){throw new IllegalArgumentException(entry.getKey()+": gameplay definition: "+e.getMessage(),e);}
            return Map.copyOf(content.definitions());
        }catch(IOException e){throw new IllegalStateException("Cannot read card gameplay identities",e);}
    }
    public static String key(Catalog.Card card){
        if(DEFINITIONS.containsKey(card.id()))return card.id();
        String species=card.species().toLowerCase(Locale.ROOT);
        if(species.startsWith("cobblemon:"))species=species.substring("cobblemon:".length());
        return species+(card.aspects().isEmpty()?"":"|"+String.join("|",new TreeSet<>(card.aspects())));
    }
    public static Definition definition(Catalog.Card card){return card.id().startsWith("special_")?null:DEFINITIONS.get(key(card));}
    public static Map<String,Definition> definitions(){return DEFINITIONS;}
    public static String name(Catalog.Card card){var d=definition(card);return d==null?"":d.name();}
    public static Catalog.Effect identity(Catalog.Card card,EffectSpec.Presentation presentation){
        var definition=definition(card);
        var effect=definition==null?card.effect():definition.effect();
        if(effect==null)throw new IllegalArgumentException(card.id()+": missing executable gameplay definition");
        var spec=effect.spec();
        // Legacy support execution remains intact. Its DTO accurately describes the existing operation.
        if(spec==null)spec=legacy(effect);
        var stages=EffectSpec.list(spec.stages()).stream().filter(s->definition==null||!s.id().equals("signature"))
            .map(s->new EffectSpec.Stage(s.id(),withPresentation(s.effect(),presentation),s.sourceZones(),s.listenAny())).toList();
        var result=new EffectSpec(spec.triggers(),spec.conditions(),spec.costs(),spec.targets(),spec.operations(),spec.oncePerDuel(),spec.optional(),spec.textKey(),presentation,stages,spec.limitScope(),spec.resolutionConditions());
        return new Catalog.Effect(effect.operation(),effect.amount(),effect.speed(),effect.lifeCost(),effect.target(),effect.phases(),effect.oncePerTurn(),result);
    }
    private static Catalog.Effect withPresentation(Catalog.Effect effect,EffectSpec.Presentation p){
        var s=effect.spec();return new Catalog.Effect(effect.operation(),effect.amount(),effect.speed(),effect.lifeCost(),effect.target(),effect.phases(),effect.oncePerTurn(),new EffectSpec(s.triggers(),s.conditions(),s.costs(),s.targets(),s.operations(),s.oncePerDuel(),s.optional(),s.textKey(),p,List.of(),s.limitScope(),s.resolutionConditions()));
    }
    private static EffectSpec legacy(Catalog.Effect e){
        String type=switch(e.operation()){
            case "damage"->"DAMAGE_LP";case "heal"->"HEAL_LP";case "draw"->"DRAW";
            case "destroy"->"DESTROY";case "banish"->"BANISH";case "return"->"RETURN_HAND";
            case "revive"->"REVIVE";case "shield"->"PREVENT_DESTROY";case "boost"->"MODIFY_POWER";
            case "negate_effect"->"NEGATE_EFFECT";case "negate_activation"->"NEGATE_ACTIVATION";
            default->throw new IllegalArgumentException("Unmapped legacy operation "+e.operation());};
        String target=switch(e.target()){case "enemy","ally","grave"->"TARGET";case "chain"->"CHAIN_SOURCE";default->"SELF";};
        var operation=new EffectSpec.Operation(type,e.amount(),target,null,e.operation().equals("boost")?"TURN_END":null,e.operation().equals("damage")?Map.of("recipient","ENEMY"):Map.of(),List.of(),null,List.of());
        return new EffectSpec(List.of("ON_ACTIVATE"),List.of(),List.of(),null,List.of(operation),false,false,null,null,List.of(),"CARD_NAME");
    }
    private CardGameplayEffects(){}
}
