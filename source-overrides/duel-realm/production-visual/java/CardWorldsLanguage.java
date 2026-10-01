package vn.svarcade.tcg.client.component;

import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.text.Text;
import vn.svarcade.tcg.data.Catalog;
import vn.svarcade.tcg.data.EffectSpec;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

/** Minecraft language selection plus a migration bridge for legacy composed UI messages. */
public final class CardWorldsLanguage {
    private static final Map<String,String> ALIASES=load();
    private static final List<String> TERMS=ALIASES.keySet().stream().sorted(Comparator.comparingInt(String::length).reversed()).toList();
    private static final List<String> KEY_PREFIXES=ALIASES.values().stream().distinct().sorted(Comparator.comparingInt(String::length).reversed()).toList();
    private static final Map<String,String> NAMES=new HashMap<>();
    private static Map<String,String> load() {
        try(var in=CardWorldsLanguage.class.getResourceAsStream("/data/svarcade_tcg/ui_translation_aliases.json")) {
            return new Gson().fromJson(new InputStreamReader(Objects.requireNonNull(in),StandardCharsets.UTF_8),new TypeToken<Map<String,String>>(){}.getType());
        }catch(Exception e){return Map.of();}
    }
    public static String language(){return MinecraftClient.getInstance().getLanguageManager().getLanguage();}
    public static String t(String key,Object... args){return Text.translatable(key,args).getString();}
    public static String translate(String raw) {
        if(raw==null)return "";
        if(I18n.hasTranslation(raw))return t(raw);
        String exact=ALIASES.get(raw);if(exact!=null)return t(exact);
        for(String key:KEY_PREFIXES)if(raw.length()>key.length()&&raw.startsWith(key)&&I18n.hasTranslation(key))
            return t(key)+raw.substring(key.length());
        if(!language().equals("vi_vn"))return raw;
        // Resolve non-overlapping longest legacy fragments once, preserving player-entered values.
        StringBuilder result=new StringBuilder();int offset=0;
        while(offset<raw.length()) {
            String found=null;
            for(String term:TERMS) {
                if(term.length()<2||!raw.startsWith(term,offset))continue;
                boolean left=offset==0||!Character.isLetter(term.charAt(0))||!Character.isLetter(raw.charAt(offset-1));
                int end=offset+term.length();boolean right=end==raw.length()||!Character.isLetter(term.charAt(term.length()-1))||!Character.isLetter(raw.charAt(end));
                if(left&&right){found=term;break;}
            }
            if(found!=null){result.append(t(ALIASES.get(found)));offset+=found.length();}
            else result.append(raw.charAt(offset++));
        }
        return result.toString();
    }
    public static String name(Catalog.Card card) {
        String key="card.svarcade_tcg."+card.id()+".name";if(I18n.hasTranslation(key))return t(key);
        if(!card.category().equals("pokemon"))return translate(card.name());
        return NAMES.computeIfAbsent(language()+"|"+card.id(),ignored->{
            try {var props=PokemonProperties.Companion.parse(card.species());props.setAspects(new HashSet<>(card.aspects()));return props.create(null).getDisplayName(false).getString();}
            catch(RuntimeException e){return card.name();}
        });
    }
    private static String target(String selector){return t("cardworlds.target."+EffectSpec.value(selector,"SELF").toLowerCase(Locale.ROOT));}
    private static String condition(EffectSpec.Condition condition) {
        if(condition==null)return "";
        if(!EffectSpec.list(condition.children()).isEmpty())return t("cardworlds.condition."+condition.type().toLowerCase(Locale.ROOT))+": "+String.join("; ",condition.children().stream().map(CardWorldsLanguage::condition).toList());
        boolean numerical=condition.type().contains("AT_LEAST")||condition.type().contains("AT_MOST");
        String value=numerical?Integer.toString(condition.amount()):translate(EffectSpec.value(condition.value(),""));
        return t("cardworlds.condition."+condition.type().toLowerCase(Locale.ROOT),value,target(condition.target()));
    }
    private static String operation(EffectSpec.Operation op) {
        String result=t("cardworlds.operation."+op.type().toLowerCase(Locale.ROOT),op.amount(),target(op.target()));
        if(op.flags()!=null) {
            for(var flag:op.flags().entrySet())if(Set.of("status","counter","position","order","placement","controller","recipient","count").contains(flag.getKey())) {
                String value=flag.getValue();String key="cardworlds.effect."+value.toLowerCase(Locale.ROOT);
                if(I18n.hasTranslation(key))value=t(key);else if(I18n.hasTranslation("cardworlds.status."+value.toLowerCase(Locale.ROOT)))value=t("cardworlds.status."+value.toLowerCase(Locale.ROOT));else value=translate(value);
                result+=" ["+value+"]";
            }
        }
        if(op.filter()!=null)result+=" ["+filter(op.filter())+"]";
        if(op.zone()!=null)result+=" ["+translate(op.zone())+"]";
        if(op.duration()!=null) {
            String duration=op.duration().toLowerCase(Locale.ROOT);String key="cardworlds.effect."+duration;
            result+=" ("+(I18n.hasTranslation(key)?t(key):op.duration())+")";
        }
        if(op.condition()!=null)result=condition(op.condition())+": "+result;
        for(var child:EffectSpec.list(op.children()))result+="; "+operation(child);
        if(!EffectSpec.list(op.otherwise()).isEmpty())result+="; "+t("cardworlds.effect.else")+": "+String.join("; ",op.otherwise().stream().map(CardWorldsLanguage::operation).toList());
        return result;
    }
    private static String filter(EffectSpec.Filter f) {
        List<String> values=new ArrayList<>();
        if(f.category()!=null)values.add(translate(f.category()));
        if(f.minLevel()>0)values.add(t("cardworlds.effect.level_at_least",f.minLevel()));
        if(f.maxLevel()>0)values.add(t("cardworlds.effect.level_at_most",f.maxLevel()));
        if(f.minPower()>0)values.add(t("cardworlds.effect.power_at_least",f.minPower()));
        if(f.maxPower()>0)values.add(t("cardworlds.effect.power_at_most",f.maxPower()));
        EffectSpec.list(f.types()).forEach(v->values.add(t("cardworlds.type."+v)));
        values.addAll(EffectSpec.list(f.tags()));
        if(f.position()!=null)values.add(translate(f.position()));if(f.zone()!=null)values.add(translate(f.zone()));
        if(f.controller()!=null)values.add(translate(f.controller()));if(f.faceUp()!=null)values.add(t("cardworlds.condition."+(f.faceUp()?"face_up":"face_down")));
        return String.join(", ",values);
    }
    public static String effect(Catalog.Card card) {
        Catalog.Effect effect=card.effect();List<String> lines=new ArrayList<>();
        if(effect!=null&&effect.spec()!=null) {
            var spec=effect.spec();lines.add(t("cardworlds.effect.trigger")+": "+String.join(", ",spec.triggers().stream().map(v->t("cardworlds.trigger."+v.toLowerCase(Locale.ROOT))).toList())+" · "+t("cardworlds.effect.speed",effect.speed()));
            List<String> costs=new ArrayList<>();if(effect.lifeCost()>0)costs.add(t("cardworlds.cost.lp_cost",effect.lifeCost()));
            for(var c:EffectSpec.list(spec.costs()))costs.add(t("cardworlds.cost."+c.type().toLowerCase(Locale.ROOT),c.amount()));
            lines.add(t("cardworlds.effect.cost")+": "+(costs.isEmpty()?t("cardworlds.effect.none"):String.join("; ",costs)));
            if(!EffectSpec.list(spec.conditions()).isEmpty())lines.add(t("cardworlds.effect.condition")+": "+String.join("; ",spec.conditions().stream().map(CardWorldsLanguage::condition).toList()));
            if(spec.targets()!=null) {
                var target=spec.targets();String value=target(target.selector());
                if(target.filter()!=null)value+=" · "+filter(target.filter());
                lines.add(t("cardworlds.effect.target")+": "+value);
            }
            lines.add(t("cardworlds.effect.effect")+": "+String.join("; ",spec.operations().stream().map(CardWorldsLanguage::operation).toList()));
            if(effect.oncePerTurn()||spec.oncePerDuel())lines.add(t("cardworlds.effect.limit")+": "+(effect.oncePerTurn()?t("cardworlds.effect.once_per_turn"):"")+(spec.oncePerDuel()?" · "+t("cardworlds.effect.once_per_duel"):""));
        } else if(effect!=null) {
            String op=switch(effect.operation()){case "damage"->"damage_lp";case "heal"->"heal_lp";case "boost"->"modify_power";case "return"->"return_hand";case "shield"->"prevent_destroy";default->effect.operation();};
            lines.add(t("cardworlds.trigger.on_activate")+" · "+t("cardworlds.effect.speed",effect.speed()));
            if(effect.lifeCost()>0)lines.add(t("cardworlds.cost.lp_cost",effect.lifeCost()));
            String target=target(switch(effect.target()){case "enemy","ally","grave"->"TARGET";case "chain"->"CHAIN_SOURCE";default->"SELF";});
            lines.add(t(effect.operation().equals("shield")?"cardworlds.effect.legacy_shield":"cardworlds.operation."+op,effect.amount(),target)+(effect.operation().equals("boost")?" ("+t("cardworlds.effect.turn_end")+")":""));if(effect.oncePerTurn())lines.add(t("cardworlds.effect.once_per_turn"));
        }
        if(card.triggers()!=null)for(var trigger:card.triggers())lines.add(t("cardworlds.trigger."+switch(trigger.cause()){case "PLAY","TRIBUTE_SUMMON","EVOLVE"->"on_summon";case "EXTRA_SUMMON","SPECIAL_SUMMON","REVIVE","SUMMON_STAGE"->"on_special_summon";case "DESTROY","BATTLE"->"on_destroyed";case "DRAW"->"on_draw";case "BANISH"->"on_banish";case "RETURN"->"on_return";case "DISCARD"->"on_discard";default->"on_activate";})+" ["+translate(trigger.zone())+", "+translate(trigger.relation())+"] → "+t("cardworlds.operation."+switch(trigger.effect().operation()){case "damage"->"damage_lp";case "heal"->"heal_lp";default->trigger.effect().operation();},trigger.effect().amount(),target("SELF")));
        if(card.modifiers()!=null)for(var modifier:card.modifiers())lines.add(t("cardworlds.trigger.continuous")+": "+t("cardworlds.operation.modify_power",modifier.power(),target("ALL_ALLIES"))+" ["+t("cardworlds.type."+modifier.affectedType())+"]");
        return lines.isEmpty()?t("cardworlds.effect.none"):String.join("\n",lines);
    }
    private CardWorldsLanguage() {}
}
