package io.github.aristheg201.svarcade.engine;

import java.util.*;
import java.util.function.*;
import java.util.stream.*;

/** Selectors compose spatial queries, filters and stable ordering. */
public final class TargetSelectors {
    @FunctionalInterface public interface Selector { List<BattleUnit> select(BattleRuntime world, BattleUnit source, BattleUnit target, EffectDefinition effect); }
    private final Map<String,Selector> selectors = new LinkedHashMap<>();
    public TargetSelectors() {
        register("self", (w,s,t,e)->List.of(s));
        register("current_target", (w,s,t,e)->t==null?List.of():List.of(t));
        register("nearest_enemy", ranked(false,(w,s)->Comparator.comparingInt((BattleUnit u)->w.board().distance(s.cell,u.cell))));
        register("farthest_enemy", ranked(false,(w,s)->Comparator.comparingInt((BattleUnit u)->w.board().distance(s.cell,u.cell)).reversed()));
        register("lowest_hp_enemy", ranked(false,(w,s)->Comparator.comparingDouble(u->u.hp)));
        register("highest_hp_enemy", ranked(false,(w,s)->Comparator.comparingDouble((BattleUnit u)->u.hp).reversed()));
        register("lowest_percent_hp", ranked(false,(w,s)->Comparator.comparingDouble(BattleUnit::healthFraction)));
        register("lowest_hp_ally", ranked(true,(w,s)->Comparator.comparingDouble(BattleUnit::healthFraction)));
        register("highest_attack", ranked(false,(w,s)->Comparator.comparingDouble((BattleUnit u)->u.stat(Stat.ATTACK)).reversed()));
        register("highest_ap", ranked(false,(w,s)->Comparator.comparingDouble((BattleUnit u)->u.stat(Stat.AP)).reversed()));
        register("highest_mana", ranked(false,(w,s)->Comparator.comparingDouble((BattleUnit u)->u.mana).reversed()));
        register("random_enemy", random(false));register("random_ally",random(true));
        register("all_enemies",(w,s,t,e)->candidates(w,s,e,false));
        register("all_allies",(w,s,t,e)->candidates(w,s,e,true));
        register("adjacent_units",spatial((w,s,t,u,e)->w.board().distance(s.cell,u.cell)==1));
        register("units_in_radius",spatial((w,s,t,u,e)->w.board().distance(center(s,t,e),u.cell)<=e.value("radius",1)));
        register("circle",selectors.get("units_in_radius"));
        register("hex_ring",spatial((w,s,t,u,e)->w.board().distance(center(s,t,e),u.cell)==e.value("radius",1)));
        register("same_column",spatial((w,s,t,u,e)->u.cell%w.board().columns()==s.cell%w.board().columns()));
        register("front_row",spatial((w,s,t,u,e)->u.cell/w.board().columns()==(u.team==0?w.board().rows()/2:w.board().rows()/2-1)));
        register("back_row",spatial((w,s,t,u,e)->u.cell/w.board().columns()==(u.team==0?w.board().rows()-1:0)));
        register("same_trait",spatial((w,s,t,u,e)->u.traits.stream().anyMatch(s.traits::contains)));
        register("summoner",(w,s,t,e)->Optional.ofNullable(w.unit(s.summoner)).map(List::of).orElseGet(List::of));
        register("summons",spatial((w,s,t,u,e)->s.id.equals(u.summoner)));
        register("marked_target",spatial((w,s,t,u,e)->u.hasStatus(e.text("mark","mark"))));
        register("rectangle",spatial((w,s,t,u,e)->Math.abs(u.cell%w.board().columns()-center(s,t,e)%w.board().columns())<=e.value("width",1)/2
            &&Math.abs(u.cell/w.board().columns()-center(s,t,e)/w.board().columns())<=e.value("height",1)/2));
        register("line",spatial((w,s,t,u,e)->inDirection(w,s,t,u,e,false)));
        register("cone",spatial((w,s,t,u,e)->inDirection(w,s,t,u,e,true)));
    }
    public void register(String id,Selector selector) { if(selectors.putIfAbsent(id,selector)!=null)throw new IllegalArgumentException("Duplicate selector "+id); }
    public boolean contains(String id) { return selectors.containsKey(id); }
    public List<BattleUnit> select(BattleRuntime w,BattleUnit s,BattleUnit t,EffectDefinition e) {
        Selector selector=selectors.get(e.selector());if(selector==null)throw new IllegalArgumentException("Unknown selector "+e.selector());
        return selector.select(w,s,t,e).stream().filter(u->filter(w,s,u,e)).limit((long)e.value("limit",512)).toList();
    }
    private static int center(BattleUnit s,BattleUnit t,EffectDefinition e){return e.text("center","source").equals("target")&&t!=null?t.cell:s.cell;}
    private interface Spatial { boolean test(BattleRuntime w,BattleUnit s,BattleUnit t,BattleUnit u,EffectDefinition e); }
    private static Selector spatial(Spatial f){return (w,s,t,e)->w.units().stream().filter(u->filter(w,s,u,e)&&f.test(w,s,t,u,e)).sorted(Comparator.comparing(u->u.id)).toList();}
    private static List<BattleUnit> candidates(BattleRuntime w,BattleUnit s,EffectDefinition e,boolean ally){return w.units().stream().filter(u->(u.team==s.team)==ally&&filter(w,s,u,e)).sorted(Comparator.comparing(u->u.id)).toList();}
    private static Selector ranked(boolean ally,BiFunction<BattleRuntime,BattleUnit,Comparator<BattleUnit>> order){return (w,s,t,e)->candidates(w,s,e,ally).stream().sorted(order.apply(w,s).thenComparing(u->u.id)).limit((long)e.value("limit",1)).toList();}
    private static Selector random(boolean ally){return (w,s,t,e)->{List<BattleUnit> pool=new ArrayList<>(candidates(w,s,e,ally));List<BattleUnit> result=new ArrayList<>();int count=(int)e.value("limit",1);while(!pool.isEmpty()&&result.size()<count)result.add(pool.remove(w.random().nextInt(pool.size())));return result;};}
    private static boolean filter(BattleRuntime w,BattleUnit s,BattleUnit u,EffectDefinition e) {
        String alive=e.text("alive","true");
        if(!alive.equals("any")&&u.alive()!=Boolean.parseBoolean(alive))return false;
        if(u.hasStatus("untargetable")&&!e.text("include_untargetable","false").equals("true"))return false;
        String relation=e.text("relation","any");if(relation.equals("ally")&&u.team!=s.team||relation.equals("enemy")&&u.team==s.team)return false;
        if(e.strings().containsKey("trait")&&!u.traits.contains(e.text("trait","")))return false;
        if(e.strings().containsKey("tag")&&!u.tags.contains(e.text("tag","")))return false;
        if(e.strings().containsKey("status")&&!u.hasStatus(e.text("status","")))return false;
        if(e.strings().containsKey("summon")&&(u.summoner!=null)!=Boolean.parseBoolean(e.text("summon","false")))return false;
        return w.board().distance(s.cell,u.cell)<=e.value("range",4096)&&u.hp>=e.value("min_hp",0)&&u.hp<=e.value("max_hp",Double.MAX_VALUE)
            &&u.mana>=e.value("min_mana",0)&&u.mana<=e.value("max_mana",Double.MAX_VALUE)
            &&u.stat(Stat.STAR)>=e.value("min_star",0)&&u.stat(Stat.STAR)<=e.value("max_star",3)
            &&u.stat(Stat.COST)>=e.value("min_cost",0)&&u.stat(Stat.COST)<=e.value("max_cost",Double.MAX_VALUE);
    }
    private static boolean inDirection(BattleRuntime w,BattleUnit s,BattleUnit t,BattleUnit u,EffectDefinition e,boolean cone) {
        if(t==null)return false;int cols=w.board().columns();double dx=t.cell%cols-s.cell%cols,dy=t.cell/cols-s.cell/cols;
        double ux=u.cell%cols-s.cell%cols,uy=u.cell/cols-s.cell/cols;double length=Math.hypot(dx,dy);
        if(length==0)return u==s;double forward=(ux*dx+uy*dy)/length,side=Math.abs(ux*dy-uy*dx)/length;
        return forward>=0&&forward<=e.value("length",6)&&side<=(cone?forward*Math.tan(Math.toRadians(e.value("angle",60)/2)):e.value("width",1)/2);
    }
}
