package io.github.aristheg201.svarcade.engine;

import java.util.*;

/** Single-owner deterministic runtime. No world access, clocks, threads, or I/O. */
public final class BattleRuntime {
    public record Event(BattleEvent kind,String subject,String source,String target,double amount,int depth) {}
    public record Scheduled(long due,long serial,String source,String target,List<EffectDefinition> effects,int depth) {
        public Scheduled { effects=List.copyOf(effects); }
    }
    public record TriggerState(long nextAllowed,int seen,int fired,Set<String> targets) {
        public TriggerState { targets=Set.copyOf(targets); }
    }
    public record Cue(long serial,long time,String kind,String source,String target,String asset,double value) {}
    public record Limits(int triggerDepth,int effectsPerStep,int queuedEvents,int scheduledActions,int units) {
        public Limits { if(triggerDepth<1||triggerDepth>64||effectsPerStep<1||queuedEvents<1||scheduledActions<1||units<1)throw new IllegalArgumentException("Invalid runtime limits"); }
    }
    public record Snapshot(int schema,long now,long randomState,long serial,List<BattleUnit.Snapshot> units,
                           List<Event> events,List<Scheduled> scheduled,Map<String,TriggerState> triggers,
                           Map<String,Map<String,Double>> resources,long effectsExecuted,long recursionPrevented) {}
    @FunctionalInterface public interface UnitFactory { BattleUnit create(String definition,String id,String owner,int team,int cell); }

    private final BattleBoard board;
    private final Limits limits;
    private final SeededRandom random;
    private final Map<String,BattleUnit> units=new LinkedHashMap<>();
    private final ArrayDeque<Event> events=new ArrayDeque<>();
    private final PriorityQueue<Scheduled> scheduled=new PriorityQueue<>(Comparator.comparingLong(Scheduled::due).thenComparingLong(Scheduled::serial));
    private final Map<String,TriggerState> triggerStates=new LinkedHashMap<>();
    private final Map<String,Map<String,Double>> resources=new LinkedHashMap<>();
    private final ArrayDeque<Cue> cues=new ArrayDeque<>();
    private final EffectRegistry effects;
    private final UnitFactory factory;
    private final TargetSelectors selectors=new TargetSelectors();
    private final TriggerConditions conditions=new TriggerConditions();
    private long now,serial,effectsExecuted,recursionPrevented,targetQueries;
    private int stepEffects;
    private boolean draining;

    public BattleRuntime(BattleBoard board,Limits limits,long seed,UnitFactory factory,Map<String,List<EffectDefinition>> graphs) {
        this.board=board;this.limits=limits;this.random=new SeededRandom(seed);this.factory=factory;
        this.effects=new EffectRegistry(this,graphs);
    }
    public BattleBoard board(){return board;} public SeededRandom random(){return random;} public long now(){return now;}
    public long nextSerial(){return ++serial;}
    public Collection<BattleUnit> units(){return Collections.unmodifiableCollection(units.values());}
    public BattleUnit unit(String id){return id==null?null:units.get(id);}
    public long effectsExecuted(){return effectsExecuted;} public long recursionPrevented(){return recursionPrevented;} public long targetQueries(){return targetQueries;}
    public void add(BattleUnit unit){if(units.size()>=limits.units||!board.valid(unit.cell)||units.putIfAbsent(unit.id,unit)!=null)throw new IllegalArgumentException("Invalid/duplicate/capacity unit "+unit.id);}
    public Set<Integer> occupied(){Set<Integer> out=new HashSet<>();for(var u:units.values())if(u.alive())out.add(u.cell);return out;}
    public double resource(String owner,String id){return resources.getOrDefault(owner,Map.of()).getOrDefault(id,0.0);}
    public void resource(String owner,String id,double amount){if(!Double.isFinite(amount))throw new IllegalArgumentException("Non-finite resource");var map=resources.computeIfAbsent(owner,k->new LinkedHashMap<>());map.put(id,Math.max(0,Math.min(1000000000,resource(owner,id)+amount)));}
    public List<BattleUnit> select(BattleUnit source,BattleUnit target,EffectDefinition node){targetQueries++;return selectors.select(this,source,target,node);}
    public void validate(List<EffectDefinition> graph,String source){effects.validate(graph,source,new LinkedHashSet<>(),0);}
    public boolean validSelector(String id){return selectors.contains(id);}
    public void validate(TriggerDefinition trigger,String source){for(var c:trigger.conditions())if(!conditions.contains(c.kind()))throw new IllegalArgumentException(source+": unknown condition "+c.kind());validate(trigger.effects(),source);}

    public void execute(BattleUnit source,BattleUnit target,List<EffectDefinition> graph,int depth) {
        if(depth>limits.triggerDepth){recursionPrevented++;return;}
        for(var node:graph){if(++stepEffects>limits.effectsPerStep){recursionPrevented++;return;}effectsExecuted++;effects.apply(source,target,node,depth);}
    }
    public void emit(BattleEvent kind,BattleUnit subject,BattleUnit source,BattleUnit target,double amount,int depth) {
        if(subject==null)return;
        if(depth>limits.triggerDepth||events.size()>=limits.queuedEvents){recursionPrevented++;return;}
        events.addLast(new Event(kind,subject.id,source==null?null:source.id,target==null?null:target.id,amount,depth));
    }
    public void drain() {
        if(draining)return;draining=true;int processed=0;
        try {while(!events.isEmpty()&&processed++<limits.queuedEvents){Event e=events.removeFirst();BattleUnit subject=unit(e.subject());if(subject==null)continue;
            var triggers=subject.triggers.stream().filter(t->t.event()==e.kind()).sorted(Comparator.comparingInt(TriggerDefinition::priority).thenComparing(TriggerDefinition::id)).toList();
            for(var t:triggers){String key=subject.id+"/"+t.id();TriggerState state=triggerStates.getOrDefault(key,new TriggerState(0,0,0,Set.of()));int seen=state.seen()+1;
                triggerStates.put(key,new TriggerState(state.nextAllowed(),seen,state.fired(),state.targets()));
                String targetKey=e.target()==null?"":e.target();
                if(now<state.nextAllowed()||t.oncePerCombat()&&state.fired()>0||t.oncePerTarget()&&state.targets().contains(targetKey)||seen%t.every()!=0)continue;
                if(!conditions.matches(this,subject,e,t.conditions())||random.nextDouble()>=t.chance())continue;
                Set<String> targets=new LinkedHashSet<>(state.targets());if(t.oncePerTarget())targets.add(targetKey);
                triggerStates.put(key,new TriggerState(now+t.cooldownMs(),seen,state.fired()+1,targets));
                execute(subject,unit(e.target()),t.effects(),e.depth()+1);
            }
        }} finally {draining=false;}
    }
    public void schedule(long delay,BattleUnit source,BattleUnit target,List<EffectDefinition> graph,int depth) {
        if(delay<0||delay>3600000)throw new IllegalArgumentException("Invalid delay");
        if(scheduled.size()>=limits.scheduledActions){recursionPrevented++;return;}
        scheduled.add(new Scheduled(now+delay,++serial,source.id,target==null?null:target.id,graph,depth));
    }
    public void advance(long deltaMillis) {
        if(deltaMillis<0||deltaMillis>1000)throw new IllegalArgumentException("Invalid simulation delta");
        now+=deltaMillis;stepEffects=0;
        int processed=0;
        while(!scheduled.isEmpty()&&scheduled.peek().due()<=now&&processed++<limits.scheduledActions){var task=scheduled.poll();var source=unit(task.source());if(source!=null)execute(source,unit(task.target()),task.effects(),task.depth());drain();}
        for(var unit:List.copyOf(units.values())){
            unit.modifiers.values().removeIf(m->m.expiresAt()>0&&m.expiresAt()<=now);
            for(var status:List.copyOf(unit.statuses.values())){
                if(status.expiresAt()<=now){unit.statuses.remove(status.id());emit(BattleEvent.ON_STATUS_REMOVED,unit,unit(unit.summoner),unit,0,0);continue;}
                if(status.intervalMs()>0&&status.nextTickAt()<=now){var source=unit(status.source());if(source!=null)execute(source,unit,status.periodic(),0);
                    unit.statuses.put(status.id(),new BattleUnit.StatusState(status.id(),status.source(),status.expiresAt(),status.stacks(),status.intensity(),now+status.intervalMs(),status.intervalMs(),status.periodic()));}
            }
            if(unit.alive())emit(BattleEvent.ON_TICK,unit,unit,unit,deltaMillis,0);
        }
        drain();
    }
    public BattleUnit summon(BattleUnit parent,String definition,int cell,double inheritance,boolean copy) {
        if(units.size()>=limits.units||!board.valid(cell)||occupied().contains(cell))return null;
        BattleUnit child=factory.create(definition,"summon:"+(++serial),parent.owner,parent.team,cell);
        child.summoner=parent.id;
        if(copy){for(var stat:Stat.values())child.stats.put(stat,parent.stat(stat)*(Set.of(Stat.STAR,Stat.COST,Stat.RANGE,Stat.MOVE_SPEED).contains(stat)?1:inheritance));child.hp=child.stat(Stat.MAX_HP);child.mana=parent.mana*inheritance;child.traits.clear();child.traits.addAll(parent.traits);}
        add(child);emit(BattleEvent.ON_SUMMON,parent,parent,child,1,0);cue("spawn",child,null,"",0);return child;
    }
    public void transform(BattleUnit unit,String definition,boolean keepHealth,int depth) {
        var next=factory.create(definition,unit.id,unit.owner,unit.team,unit.cell);double ratio=unit.healthFraction();
        unit.definitionId=definition;unit.stats.clear();unit.stats.putAll(next.stats);unit.traits.clear();unit.traits.addAll(next.traits);unit.triggers.clear();unit.triggers.addAll(next.triggers);
        unit.hp=keepHealth?unit.stat(Stat.MAX_HP)*ratio:unit.stat(Stat.MAX_HP);unit.mana=Math.min(unit.mana,unit.stat(Stat.MAX_MANA));
        emit(BattleEvent.ON_TRANSFORM,unit,unit,unit,0,depth+1);cue("transform",unit,null,"",0);
    }
    public void despawn(BattleUnit target){units.remove(target.id);cue("despawn",target,null,"",0);}
    public void cue(String kind,BattleUnit source,BattleUnit target,String asset,double value){cues.addLast(new Cue(++serial,now,kind,source.id,target==null?null:target.id,asset,value));while(cues.size()>256)cues.removeFirst();}
    public List<Cue> takeCues(){var result=List.copyOf(cues);cues.clear();return result;}
    public Snapshot snapshot(){Map<String,Map<String,Double>> resourcesCopy=new LinkedHashMap<>();resources.forEach((k,v)->resourcesCopy.put(k,Map.copyOf(v)));return new Snapshot(1,now,random.state(),serial,units.values().stream().map(BattleUnit::snapshot).toList(),List.copyOf(events),scheduled.stream().sorted(scheduled.comparator()).toList(),Map.copyOf(triggerStates),Map.copyOf(resourcesCopy),effectsExecuted,recursionPrevented);}
    public static BattleRuntime restore(BattleBoard board,Limits limits,UnitFactory factory,Map<String,List<EffectDefinition>> graphs,Snapshot s){
        if(s.schema()!=1)throw new IllegalArgumentException("Unsupported battle schema");
        var world=new BattleRuntime(board,limits,s.randomState(),factory,graphs);world.now=s.now();world.serial=s.serial();
        for(var u:s.units())world.add(BattleUnit.restore(u));
        if(s.events().size()>limits.queuedEvents||s.scheduled().size()>limits.scheduledActions)throw new IllegalArgumentException("Recovery queues exceed bounds");
        world.events.addAll(s.events());world.scheduled.addAll(s.scheduled());world.triggerStates.putAll(s.triggers());s.resources().forEach((k,v)->world.resources.put(k,new LinkedHashMap<>(v)));
        world.effectsExecuted=s.effectsExecuted();world.recursionPrevented=s.recursionPrevented();return world;
    }
}
