package vn.svarcade.tcg.data;

import org.slf4j.LoggerFactory;
import java.util.*;

/** Focused runtime guard for the current effect-cost rebalance. */
public final class EffectEconomyQa {
    public static void verify(Catalog catalog) {
        int pokemonLp=0,totalLp=0;
        Set<String> kinds=new TreeSet<>();
        for (Catalog.Card card:catalog.cards().values()) {
            if(card.effect()==null||card.effect().spec()==null)continue;
            for(var cost:EffectSpec.list(card.effect().spec().costs())) {
                kinds.add(cost.type());
                if(cost.type().equals("LP_COST")) {
                    totalLp++;
                    if(card.category().equals("pokemon"))pokemonLp++;
                }
            }
        }
        Catalog.Card signature=catalog.cards().get("mind_theft");
        boolean signatureLp=signature!=null&&signature.effect()!=null&&signature.effect().spec()!=null
            &&EffectSpec.list(signature.effect().spec().costs()).stream().anyMatch(c->c.type().equals("LP_COST")&&c.amount()==800);
        Catalog.Card darkCurrent=catalog.cards().get("dark_current");
        boolean darkCurrentLp=darkCurrent!=null&&darkCurrent.effect()!=null&&darkCurrent.effect().spec()!=null
            &&EffectSpec.list(darkCurrent.effect().spec().costs()).stream().anyMatch(c->c.type().equals("LP_COST"));
        if(pokemonLp!=0)throw new IllegalStateException("Monster LP tax regression: "+pokemonLp);
        if(!signatureLp)throw new IllegalStateException("Mind Theft signature LP cost missing.");
        if(darkCurrentLp)throw new IllegalStateException("Dark Current still uses blanket LP cost.");
        if(kinds.size()<6)throw new IllegalStateException("Effect cost diversity regressed: "+kinds);
        LoggerFactory.getLogger("cardworlds-qa").info(
            "CARDWORLDS_QA_EFFECT_COSTS pokemon_lp={} total_lp={} kinds={} signature=mind_theft:800 dark_current_lp=false",
            pokemonLp,totalLp,String.join(",",kinds));
    }
    private EffectEconomyQa(){}
}
