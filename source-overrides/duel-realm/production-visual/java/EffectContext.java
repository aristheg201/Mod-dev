package vn.svarcade.tcg.duel;

import java.util.*;

/** Private server resolution memory, never included in public snapshots. */
public final class EffectContext {
    public record Selection(String token,int generation) {}
    public final String source;
    public final int controller;
    public final int chainLink;
    public String eventCard="",eventTarget="";
    public final Map<String,List<Selection>> selectedCards=new HashMap<>();
    public final List<Selection> paidCosts=new ArrayList<>();
    public final List<Selection> destroyedThisResolution=new ArrayList<>();
    public final List<Selection> banishedThisResolution=new ArrayList<>();
    public final Map<String,String> flags=new HashMap<>();
    public EffectContext(String source,int controller,int chainLink){this.source=source;this.controller=controller;this.chainLink=chainLink;}
    public void remember(String name,List<Selection> cards){selectedCards.put(name,List.copyOf(cards));}
}
