package vn.svarcade.tcg.duel;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import vn.svarcade.tcg.data.Catalog;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Yu-Gi-Oh-style Spell/Trap placement/timing metadata layered over catalog effects. */
public final class SpellTrapRules {
    public enum Kind { NORMAL_SPELL, QUICK_PLAY_SPELL, CONTINUOUS_SPELL, FIELD_SPELL, EQUIP_SPELL, NORMAL_TRAP, CONTINUOUS_TRAP, COUNTER_TRAP }
    public record Profile(String card, Kind kind, boolean activateFromHand, boolean persists, int minSetTurns) {}
    private static final Map<String,Profile> EXPLICIT=load();
    private SpellTrapRules(){}
    public static Profile profile(Catalog.Card c){
        if(c==null||c.category().equals("pokemon"))return null;
        Profile p=EXPLICIT.get(c.id()); if(p!=null)return p;
        int speed=c.effect()==null?1:c.effect().speed();
        return switch(c.category()){
            case "stadium" -> new Profile(c.id(),Kind.FIELD_SPELL,true,true,0);
            case "reaction" -> new Profile(c.id(),speed>=3?Kind.COUNTER_TRAP:Kind.NORMAL_TRAP,false,false,1);
            case "trainer" -> new Profile(c.id(),Kind.CONTINUOUS_SPELL,true,true,0);
            case "item" -> new Profile(c.id(),Kind.NORMAL_SPELL,true,false,0);
            default -> new Profile(c.id(),speed>=2?Kind.QUICK_PLAY_SPELL:Kind.NORMAL_SPELL,true,false,0);
        };
    }
    public static boolean persists(Catalog.Card c){Profile p=profile(c);return p!=null&&p.persists();}
    private static Map<String,Profile> load(){
        try(var in=SpellTrapRules.class.getResourceAsStream("/data/svarcade_tcg/spell_trap_profiles.json")){
            if(in==null)return Map.of();
            Map<String,Profile> m=new Gson().fromJson(new InputStreamReader(in,StandardCharsets.UTF_8),new TypeToken<LinkedHashMap<String,Profile>>(){}.getType());
            return m==null?Map.of():Map.copyOf(m);
        }catch(Exception ex){throw new IllegalStateException("Unable to load Spell/Trap profiles",ex);}
    }
}
