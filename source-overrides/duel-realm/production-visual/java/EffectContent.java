package vn.svarcade.tcg.data;

import com.google.gson.Gson;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Authored support cards and generic registry-monster identities share one effect pipeline. */
public final class EffectContent {
    public record Rule(List<String> types,int minLevel,int maxLevel,Boolean form, Catalog.Effect effect) {}
    public record Content(List<Rule> monsterRules,Map<String,Catalog.Card> cards,Map<String,Catalog.Banner> banners) {}
    private static final Content CONTENT = load();
    private static final Map<String,EffectSpec.Presentation> ACTIONS=loadActions();
    private static Map<String,EffectSpec.Presentation> loadActions() {
        try(var in=EffectContent.class.getResourceAsStream("/data/svarcade_tcg/action_defaults.json")){
            return new Gson().fromJson(new InputStreamReader(Objects.requireNonNull(in),StandardCharsets.UTF_8),new com.google.gson.reflect.TypeToken<Map<String,EffectSpec.Presentation>>(){}.getType());
        }catch(Exception e){return Map.of();}
    }
    private static String actionKey(String type) {
        String key=type==null?"normal":type.trim().toLowerCase(java.util.Locale.ROOT);
        int colon=key.lastIndexOf(':');
        if(colon>=0&&colon+1<key.length())key=key.substring(colon+1);
        return ACTIONS.containsKey(key)?key:"normal";
    }
    public static EffectSpec.Presentation attackPresentation(Catalog.Card card){return ACTIONS.get(actionKey(card.type()));}
    public static EffectSpec.Presentation presentation(Catalog.Card card){
        return card.effect()!=null&&card.effect().spec()!=null&&card.effect().spec().vfx()!=null?card.effect().spec().vfx():ACTIONS.get(actionKey(card.type()));
    }
    private static Content load() {
        try (var in=EffectContent.class.getResourceAsStream("/data/svarcade_tcg/deep_effects.json")) {
            if(in==null)return new Content(List.of(),Map.of(),Map.of());
            return new Gson().fromJson(new InputStreamReader(in,StandardCharsets.UTF_8),Content.class);
        } catch(IOException e) { throw new IllegalStateException("Cannot load executable card content",e); }
    }
    public static Catalog apply(Catalog base) {
        Map<String,Catalog.Card> cards=new LinkedHashMap<>(base.cards());
        cards.putAll(CONTENT.cards());
        for (Catalog.Card card : List.copyOf(cards.values())) {
            if(!card.category().equals("pokemon") || card.effect()!=null)continue;
            for (Rule rule : CONTENT.monsterRules()) {
                if(!rule.types().isEmpty()&&!rule.types().contains(card.type()))continue;
                if(card.level()<rule.minLevel()||rule.maxLevel()>0&&card.level()>rule.maxLevel())continue;
                if(rule.form()!=null && rule.form()!=!card.aspects().isEmpty())continue;
                cards.put(card.id(),new Catalog.Card(card.id(),card.name(),card.category(),card.species(),card.aspects(),card.type(),
                    card.family(),card.evolvesFrom(),card.extra(),card.level(),card.power(),"@effect",card.set(),card.rarity(),
                    card.sources(),rule.effect(),card.triggers(),card.modifiers()));
                break;
            }
        }
        Map<String,Catalog.Banner> banners=new LinkedHashMap<>(base.banners());if(CONTENT.banners()!=null)banners.putAll(CONTENT.banners());
        return new Catalog(base.rules(),Collections.unmodifiableMap(cards),Collections.unmodifiableMap(banners),base.rewards(),base.dealers(),base.starters());
    }
    private EffectContent() {}
}
