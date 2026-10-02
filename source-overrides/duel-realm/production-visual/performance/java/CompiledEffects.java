package vn.svarcade.tcg.performance;
import vn.svarcade.tcg.data.*;
import vn.svarcade.tcg.duel.Duel;
import java.util.*;
/** Parse static numeric flags, selectors, zone/position tokens and durations once per immutable content generation. */
public final class CompiledEffects {
 public record Operation(int count,int maxCounter,boolean reveal,Duel.Zone zone,Duel.BattlePosition position){}
 private final Map<EffectSpec.Operation,Operation> operations=new IdentityHashMap<>();private final Map<String,Integer> durations=new HashMap<>();private final Set<EffectSpec> seen=Collections.newSetFromMap(new IdentityHashMap<>());
 public CompiledEffects(Map<String,Catalog.Card> definitions){for(var card:definitions.values()){visit(card.effect());if(card.triggers()!=null)for(var t:card.triggers())visit(t.effect());}}
 private void visit(Catalog.Effect effect){if(effect==null||effect.spec()==null||!seen.add(effect.spec()))return;var spec=effect.spec();visitOps(spec.operations());for(var stage:spec.stages())visit(stage.effect());}
 private void visitOps(List<EffectSpec.Operation> ops){for(var op:ops){operations.put(op,parse(op));if(op.duration()!=null)durations.putIfAbsent(op.duration(),offset(op.duration()));visitOps(EffectSpec.list(op.children()));visitOps(EffectSpec.list(op.otherwise()));}}
 private static String flag(EffectSpec.Operation op,String key,String fallback){return op.flags()==null?fallback:op.flags().getOrDefault(key,fallback);}
 private static Operation parse(EffectSpec.Operation op){return new Operation(Integer.parseInt(flag(op,"count","1")),Integer.parseInt(flag(op,"max","99")),Boolean.parseBoolean(flag(op,"reveal","true")),op.zone()==null?null:Duel.Zone.valueOf(op.zone()),op.flags()==null||!op.flags().containsKey("position")?null:Duel.BattlePosition.valueOf(op.flags().get("position")));}
 private static int offset(String duration){if(duration!=null&&duration.startsWith("TURNS:"))return (int)Math.clamp(Integer.parseInt(duration.substring(6))-1,0,20);return "NEXT_TURN_END".equals(duration)?1:0;}
 public Operation operation(EffectSpec.Operation op){Operation prepared=operations.get(op);return prepared==null?parse(op):prepared;}
 public int durationOffset(String duration){return duration==null?0:durations.containsKey(duration)?durations.get(duration):offset(duration);}
 public int size(){return operations.size();}
}
