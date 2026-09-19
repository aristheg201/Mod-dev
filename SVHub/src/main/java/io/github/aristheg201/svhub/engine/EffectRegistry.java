package io.github.aristheg201.svhub.engine;

import java.util.*;

/** Operations are registered capabilities. Definitions never dispatch on content IDs. */
public final class EffectRegistry {
    @FunctionalInterface interface Operation { void apply(BattleUnit source,BattleUnit target,EffectDefinition node,int depth); }
    private final BattleRuntime world;
    private final Map<String,List<EffectDefinition>> graphs;
    private final Map<String,Operation> operations=new LinkedHashMap<>();
    private final TriggerConditions conditions=new TriggerConditions();
    private final Map<String,StatusMerger> statusPolicies=new LinkedHashMap<>();
    @FunctionalInterface interface StatusMerger { BattleUnit.StatusState merge(BattleUnit.StatusState previous,BattleUnit.StatusState next,int maximum); }

    public EffectRegistry(BattleRuntime world,Map<String,List<EffectDefinition>> graphs) {
        this.world=world;Map<String,List<EffectDefinition>> copy=new LinkedHashMap<>();graphs.forEach((id,nodes)->copy.put(id,List.copyOf(nodes)));this.graphs=Map.copyOf(copy);
        register("emit_event",(s,t,n,d)->world.emit(BattleEvent.valueOf(n.text("event","")),s,s,t,0,d+1));
        register("sequence",(s,t,n,d)->world.execute(s,t,n.children(),d+1));
        alias("parallel","sequence");
        register("ref",(s,t,n,d)->world.execute(s,t,this.graphs.get(n.text("id","")),d+1));
        register("damage",(s,t,n,d)->targets(s,t,n).forEach(u->DamagePipeline.damage(world,s,u,amount(s,u,n),DamagePipeline.Type.valueOf(n.text("type","MAGIC").toUpperCase(Locale.ROOT)),n.value("crit",0)>0&&world.random().nextDouble()<s.stat(Stat.CRIT_CHANCE),n.value("execute_below",0),d)));
        register("true_damage",(s,t,n,d)->targets(s,t,n).forEach(u->DamagePipeline.damage(world,s,u,amount(s,u,n),DamagePipeline.Type.TRUE,false,n.value("execute_below",0),d)));
        register("heal",(s,t,n,d)->targets(s,t,n).forEach(u->DamagePipeline.heal(world,s,u,amount(s,u,n),d)));
        register("shield",(s,t,n,d)->targets(s,t,n).forEach(u->DamagePipeline.shield(world,s,u,amount(s,u,n),d)));
        register("execute",(s,t,n,d)->targets(s,t,n).stream().filter(u->u.healthFraction()<=n.value("threshold",1)).forEach(u->DamagePipeline.damage(world,s,u,u.hp+u.shield,DamagePipeline.Type.TRUE,false,1,d)));
        register("revive",(s,t,n,d)->targets(s,t,n).stream().filter(u->!u.alive()).forEach(u->{u.hp=Math.max(1,u.stat(Stat.MAX_HP)*n.value("hp_fraction",0.5));u.deadAt=-1;world.emit(BattleEvent.ON_REVIVE,u,s,u,u.hp,d+1);world.cue("revive",u,null,"",0);}));
        register("modify_stat",this::modifyStat);
        register("modify_resource",(s,t,n,d)->targets(s,t,n).forEach(u->{String id=n.text("resource","mana");if(id.equals("mana"))DamagePipeline.mana(world,u,amount(s,u,n),d);else world.resource(u.owner,id,amount(s,u,n));}));
        register("add_status",this::addStatus);
        register("remove_status",(s,t,n,d)->targets(s,t,n).forEach(u->{if(u.statuses.remove(n.text("id",""))!=null)world.emit(BattleEvent.ON_STATUS_REMOVED,u,s,u,0,d+1);}));
        register("cleanse",(s,t,n,d)->targets(s,t,n).forEach(u->{for(var status:List.copyOf(u.statuses.values())){u.statuses.remove(status.id());world.emit(BattleEvent.ON_STATUS_REMOVED,u,s,u,0,d+1);}}));
        alias("dispel","cleanse");
        register("move",this::move);alias("dash","move");alias("teleport","move");alias("knockback","move");alias("pull","move");
        register("swap_position",(s,t,n,d)->targets(s,t,n).stream().findFirst().ifPresent(u->{int cell=s.cell;s.cell=u.cell;u.cell=cell;world.emit(BattleEvent.ON_MOVE,s,s,u,0,d+1);world.emit(BattleEvent.ON_MOVE,u,s,s,0,d+1);}));
        register("summon",this::summon);alias("copy_unit","summon");
        register("despawn",(s,t,n,d)->targets(s,t,n).forEach(world::despawn));
        register("transform",(s,t,n,d)->targets(s,t,n).forEach(u->world.transform(u,n.text("definition",""),n.value("keep_health",1)>0,d)));
        register("copy_stat",this::copyStat);alias("steal_stat","copy_stat");alias("transfer_stat","copy_stat");
        register("gain_stack",(s,t,n,d)->targets(s,t,n).forEach(u->u.stacks.compute(n.text("id",""),(id,old)->Math.min((int)n.value("max",1000000),(old==null?0:old)+(int)n.value("amount",1)))));
        register("consume_stack",(s,t,n,d)->targets(s,t,n).forEach(u->u.stacks.compute(n.text("id",""),(id,old)->Math.max(0,(old==null?0:old)-(int)n.value("amount",1)))));
        register("delayed_effect",(s,t,n,d)->world.schedule((long)n.value("delay_ms",0),s,t,n.children(),d+1));
        alias("delay","delayed_effect");
        register("repeat_effect",(s,t,n,d)->{int count=(int)n.value("count",1);long interval=(long)n.value("interval_ms",0);for(int i=0;i<count;i++){if(interval==0)world.execute(s,t,n.children(),d+1);else world.schedule(i*interval,s,t,n.children(),d+1);}});
        alias("repeat","repeat_effect");
        register("random_effect",(s,t,n,d)->{double total=0;for(int i=0;i<n.children().size();i++)total+=n.value("weight_"+i,1);if(total<=0)return;double roll=world.random().nextDouble()*total;for(int i=0;i<n.children().size();i++){roll-=n.value("weight_"+i,1);if(roll<0){world.execute(s,t,List.of(n.children().get(i)),d+1);return;}}});
        alias("random_choice","random_effect");
        register("conditional_effect",(s,t,n,d)->{var condition=new TriggerDefinition.Condition(n.text("condition","hp_below"),n.text("key",""),n.value("threshold",0));var event=new BattleRuntime.Event(BattleEvent.ON_CAST,s.id,s.id,t==null?null:t.id,n.value("amount",0),d);world.execute(s,t,conditions.matches(world,s,event,List.of(condition))?n.children():n.otherwise(),d+1);});
        alias("branch","conditional_effect");alias("condition","conditional_effect");
        register("targeted_effect",(s,t,n,d)->targets(s,t,n).forEach(u->world.execute(s,u,n.children(),d+1)));
        alias("area_effect","targeted_effect");register("chain_effect",this::chain);register("bounce_effect",this::chain);
        register("spawn_projectile",(s,t,n,d)->targets(s,t,n).forEach(u->{long travel=(long)(world.board().distance(s.cell,u.cell)*1000/Math.max(0.1,n.value("speed",5)));world.cue("projectile",s,u,n.text("particle",""),travel);world.schedule(travel,s,u,n.children(),d+1);}));
        register("spawn_zone",this::zone);alias("spawn_hazard","spawn_zone");alias("apply_aura","spawn_zone");
        register("spawn_object",this::summon);
        register("modify_shop",(s,t,n,d)->world.resource(s.owner,"shop:"+n.text("key",""),n.value("amount",0)));
        register("modify_cost",(s,t,n,d)->world.resource(s.owner,"cost:"+n.text("key",""),n.value("amount",0)));
        register("modify_trait_count",(s,t,n,d)->world.resource(s.owner,"trait:"+n.text("id",""),n.value("amount",0)));
        register("modify_board_capacity",(s,t,n,d)->world.resource(s.owner,"board_capacity",n.value("amount",0)));
        register("grant_item",(s,t,n,d)->targets(s,t,n).forEach(u->{String id=n.text("id","");if(u.items.size()<(int)n.value("capacity",3)&&(!n.text("unique","false").equals("true")||!u.items.contains(id))){u.items.add(id);world.emit(BattleEvent.ON_ITEM_EQUIPPED,u,s,u,0,d+1);}}));
        register("remove_item",(s,t,n,d)->targets(s,t,n).forEach(u->{if(u.items.remove(n.text("id","")))world.emit(BattleEvent.ON_ITEM_REMOVED,u,s,u,0,d+1);}));
        register("generate_loot",(s,t,n,d)->world.execute(s,t,n.children(),d+1));
        register("animation",(s,t,n,d)->world.cue("animation",s,t,n.text("semantic",""),n.value("duration_ms",0)));
        register("particle",(s,t,n,d)->world.cue("particle",s,t,n.text("id",""),n.value("count",1)));
        register("sound",(s,t,n,d)->world.cue("sound",s,t,n.text("id",""),n.value("volume",1)));
        statusPolicies.put("refresh",(a,b,m)->b);
        statusPolicies.put("replace",(a,b,m)->b);
        statusPolicies.put("ignore_weaker",(a,b,m)->a.intensity()>b.intensity()?a:b);
        statusPolicies.put("stack_duration",(a,b,m)->new BattleUnit.StatusState(b.id(),b.source(),a.expiresAt()+b.expiresAt()-world.now(),a.stacks(),b.intensity(),a.nextTickAt(),b.intervalMs(),b.periodic()));
        statusPolicies.put("stack_intensity",(a,b,m)->new BattleUnit.StatusState(b.id(),b.source(),Math.max(a.expiresAt(),b.expiresAt()),Math.min(m,a.stacks()+1),b.intensity()*Math.min(m,a.stacks()+1),a.nextTickAt(),b.intervalMs(),b.periodic()));
    }
    private void register(String id,Operation operation){if(operations.putIfAbsent(id,operation)!=null)throw new IllegalArgumentException("Duplicate effect "+id);}
    private void alias(String id,String original){register(id,Objects.requireNonNull(operations.get(original)));}
    public void apply(BattleUnit source,BattleUnit target,EffectDefinition node,int depth){Operation operation=operations.get(node.op());if(operation==null)throw new IllegalArgumentException("Unknown effect "+node.op());operation.apply(source,target,node,depth);}
    private List<BattleUnit> targets(BattleUnit source,BattleUnit target,EffectDefinition node){return world.select(source,target,node);}
    private double amount(BattleUnit source,BattleUnit target,EffectDefinition node){double result=node.value("amount",0);for(var stat:Stat.values())result+=source.stat(stat)*node.value("scale_"+stat.name().toLowerCase(Locale.ROOT),0);result+=(source.stat(Stat.MAX_HP)-source.hp)*node.value("scale_missing_hp",0)+source.hp*node.value("scale_current_hp",0)+source.mana*node.value("scale_mana",0)+target.hp*node.value("scale_target_hp",0)+world.board().distance(source.cell,target.cell)*node.value("scale_distance",0)+source.stacks.getOrDefault(node.text("stack",""),0)*node.value("scale_stacks",0);return Math.max(0,result);}
    private void modifyStat(BattleUnit s,BattleUnit t,EffectDefinition n,int depth) {
        Stat stat=Stat.valueOf(n.text("stat","").toUpperCase(Locale.ROOT));
        for(var u:targets(s,t,n)) {
            long duration=(long)n.value("duration_ms",0);
            String id=n.text("modifier_id","modifier:"+world.nextSerial());
            u.modifiers.put(id,new BattleUnit.Modifier(stat,n.value("amount",0),n.value("percent",0),duration>0?world.now()+duration:0));
        }
    }
    private void copyStat(BattleUnit s,BattleUnit t,EffectDefinition n,int d){Stat stat=Stat.valueOf(n.text("stat","").toUpperCase(Locale.ROOT));for(var u:targets(s,t,n)){double delta=u.stat(stat)*n.value("fraction",1);s.stats.merge(stat,delta,Double::sum);if(!n.op().equals("copy_stat"))u.stats.merge(stat,-delta,Double::sum);}}
    private void addStatus(BattleUnit s,BattleUnit t,EffectDefinition n,int depth){String id=n.text("id","");long interval=(long)n.value("interval_ms",0);var policy=statusPolicies.get(n.text("policy","refresh"));if(policy==null)throw new IllegalArgumentException("Unknown status policy");for(var u:targets(s,t,n)){var next=new BattleUnit.StatusState(id,s.id,world.now()+(long)n.value("duration_ms",1000),1,n.value("intensity",1),world.now()+interval,interval,n.children());var old=u.statuses.get(id);u.statuses.put(id,old==null?next:policy.merge(old,next,(int)n.value("max_stacks",1)));world.emit(BattleEvent.ON_STATUS_APPLIED,u,s,u,0,depth+1);}}
    private void move(BattleUnit s,BattleUnit t,EffectDefinition n,int depth){for(var u:targets(s,t,n)){int destination=(int)n.value("cell",t==null?s.cell:t.cell);if(n.op().equals("pull"))destination=s.cell;
        if(n.op().equals("knockback")&&t!=null){int from=t.cell;var occupied=world.occupied();destination=world.board().neighbors(from).stream().filter(c->!occupied.contains(c)).max(Comparator.comparingInt(c->world.board().distance(s.cell,c))).orElse(from);if(world.random().nextDouble()<u.stat(Stat.KNOCKBACK_RESISTANCE))continue;}
        int next=n.op().equals("teleport")?destination:world.board().nextStep(u.cell,destination,(int)n.value("stop_range",1),world.occupied());
        if(world.board().valid(next)&&(!world.occupied().contains(next)||next==u.cell)){u.cell=next;world.emit(n.op().equals("dash")?BattleEvent.ON_DASH:BattleEvent.ON_MOVE,u,s,t,0,depth+1);}}
    }
    private void summon(BattleUnit s,BattleUnit t,EffectDefinition n,int depth){boolean clone=n.op().equals("copy_unit");String definition=n.text("definition",s.definitionId);int count=(int)n.value("count",1);for(int i=0;i<count;i++){var occupied=world.occupied();int origin=t==null?s.cell:t.cell;int cell=java.util.stream.IntStream.range(0,world.board().columns()*world.board().rows()).filter(c->world.board().valid(c)&&!occupied.contains(c)).boxed().min(Comparator.comparingInt((Integer c)->world.board().distance(origin,c)).thenComparingInt(Integer::intValue)).orElse(-1);if(cell<0)return;world.summon(s,definition,cell,n.value("inheritance",1),clone);}}
    private void chain(BattleUnit source, BattleUnit target, EffectDefinition node, int depth) {
        Set<String> visited = new HashSet<>();
        BattleUnit current = targets(source, target, node).stream().findFirst().orElse(null);
        int hits = (int) node.value("count", 3);
        for (int hit = 0; hit < hits && current != null; hit++) {
            visited.add(current.id);
            world.execute(source, current, node.children(), depth + 1);
            int origin = current.cell;
            String previous = current.id;
            boolean revisit = node.op().equals("bounce_effect") && node.value("allow_revisit", 0) > 0;
            current = world.units().stream()
                .filter(u -> u.alive() && u.team != source.team && !u.id.equals(previous))
                .filter(u -> revisit || !visited.contains(u.id))
                .filter(u -> world.board().distance(origin, u.cell) <= node.value("bounce_range", 2))
                .sorted(Comparator.comparingInt((BattleUnit u) -> world.board().distance(origin, u.cell)).thenComparing(u -> u.id))
                .findFirst().orElse(null);
        }
    }
    private void zone(BattleUnit s,BattleUnit t,EffectDefinition n,int depth){long duration=(long)n.value("duration_ms",1000),interval=Math.max(50,(long)n.value("interval_ms",1000));world.cue(n.op(),s,t,n.text("particle",""),duration);for(long delay=0;delay<duration;delay+=interval)world.schedule(delay,s,t,n.children(),depth+1);}
    public void validate(List<EffectDefinition> nodes,String source,Set<String> refs,int depth){if(depth>32)throw new IllegalArgumentException(source+": effect nesting > 32");for(int i=0;i<nodes.size();i++){var n=nodes.get(i);String field=source+"["+i+"]";if(!operations.containsKey(n.op()))throw new IllegalArgumentException(field+".op: unknown "+n.op());if(!world.validSelector(n.selector()))throw new IllegalArgumentException(field+".selector: unknown "+n.selector());if(n.value("count",1)<0||n.value("count",1)>128||n.value("limit",512)<0||n.value("limit",512)>512)throw new IllegalArgumentException(field+": count/limit out of bounds");if(n.value("duration_ms",0)<0||n.value("duration_ms",0)>600000||n.value("delay_ms",0)<0||n.value("delay_ms",0)>600000)throw new IllegalArgumentException(field+": invalid duration/delay");for(var entry:n.values().entrySet())if(entry.getKey().startsWith("weight_")&&entry.getValue()<0)throw new IllegalArgumentException(field+": negative weight");
        if(n.op().equals("emit_event"))BattleEvent.valueOf(n.text("event",""));
        if(n.op().equals("ref")){String id=n.text("id","");if(!graphs.containsKey(id))throw new IllegalArgumentException(field+": unknown effect reference "+id);if(!refs.add(id))throw new IllegalArgumentException(field+": circular effect reference "+id);validate(graphs.get(id),field+" -> "+id,refs,depth+1);refs.remove(id);}
        if(n.op().equals("modify_stat")||n.op().equals("copy_stat")||n.op().equals("steal_stat")||n.op().equals("transfer_stat"))Stat.valueOf(n.text("stat","").toUpperCase(Locale.ROOT));
        if(n.op().equals("add_status")&&!statusPolicies.containsKey(n.text("policy","refresh")))throw new IllegalArgumentException(field+": invalid status policy");
        validate(n.children(),field+".children",refs,depth+1);validate(n.otherwise(),field+".otherwise",refs,depth+1);
    }}
}
