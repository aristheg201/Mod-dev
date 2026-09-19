package io.github.aristheg201.svhub.engine;

import java.util.*;

public final class TriggerConditions {
    @FunctionalInterface interface Check { boolean test(BattleRuntime w,BattleUnit owner,BattleRuntime.Event event,TriggerDefinition.Condition c); }
    private final Map<String,Check> checks=new LinkedHashMap<>();
    public TriggerConditions() {
        checks.put("hp_below",(w,u,e,c)->u.healthFraction()<=c.value());
        checks.put("hp_above",(w,u,e,c)->u.healthFraction()>=c.value());
        checks.put("mana_below",(w,u,e,c)->u.mana<=c.value());
        checks.put("mana_above",(w,u,e,c)->u.mana>=c.value());
        checks.put("threshold",(w,u,e,c)->e.amount()>=c.value());
        checks.put("stack_count",(w,u,e,c)->u.stacks.getOrDefault(c.key(),0)>=c.value());
        checks.put("trait",(w,u,e,c)->u.traits.contains(c.key()));
        checks.put("item",(w,u,e,c)->u.items.contains(c.key()));
        checks.put("tag",(w,u,e,c)->u.tags.contains(c.key()));
        checks.put("status",(w,u,e,c)->u.hasStatus(c.key()));
        checks.put("distance",(w,u,e,c)->w.unit(e.target())!=null&&w.board().distance(u.cell,w.unit(e.target()).cell)<=c.value());
        checks.put("adjacency",(w,u,e,c)->w.unit(e.target())!=null&&w.board().distance(u.cell,w.unit(e.target()).cell)==1);
        checks.put("ally",(w,u,e,c)->w.unit(e.target())!=null&&w.unit(e.target()).team==u.team);
        checks.put("enemy",(w,u,e,c)->w.unit(e.target())!=null&&w.unit(e.target()).team!=u.team);
        checks.put("stage",(w,u,e,c)->w.resource(u.owner,"stage")>=c.value());
        checks.put("resource",(w,u,e,c)->w.resource(u.owner,c.key())>=c.value());
    }
    public boolean contains(String id){return checks.containsKey(id);}
    public boolean matches(BattleRuntime w,BattleUnit owner,BattleRuntime.Event event,List<TriggerDefinition.Condition> conditions){
        for(var condition:conditions){var check=checks.get(condition.kind());if(check==null)throw new IllegalArgumentException("Unknown condition "+condition.kind());if(!check.test(w,owner,event,condition))return false;}return true;
    }
}
