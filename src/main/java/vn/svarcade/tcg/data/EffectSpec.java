package vn.svarcade.tcg.data;

import java.util.*;

/** JSON-authored compositions; all values are interpreted by the authoritative duel. */
public record EffectSpec(List<String> triggers, List<Condition> conditions, List<Cost> costs,
                         Target targets, List<Operation> operations, boolean oncePerDuel,
                         boolean optional, String textKey, Presentation vfx, List<Stage> stages,String limitScope,List<Condition> resolutionConditions) {
    public EffectSpec(List<String> triggers,List<Condition> conditions,List<Cost> costs,Target targets,List<Operation> operations,boolean oncePerDuel,boolean optional,String textKey,Presentation vfx,List<Stage> stages,String limitScope) {
        this(triggers,conditions,costs,targets,operations,oncePerDuel,optional,textKey,vfx,stages,limitScope,List.of());
    }
    public EffectSpec(List<String> triggers,List<Condition> conditions,List<Cost> costs,Target targets,List<Operation> operations,boolean oncePerDuel,boolean optional,String textKey,Presentation vfx) {
        this(triggers,conditions,costs,targets,operations,oncePerDuel,optional,textKey,vfx,List.of(),null);
    }
    public record Stage(String id,Catalog.Effect effect,List<String> sourceZones,boolean listenAny) {}
    public record Condition(String type, String target, String value, int amount, List<Condition> children) {}
    public record Cost(String type,int amount,String target,Filter filter,String counter) {
        public Cost(String type,int amount,String target,Filter filter){this(type,amount,target,filter,null);}
    }
    public record Target(String selector, Filter filter, int min, int max) {}
    public record Filter(String category, List<String> types, List<String> tags, int minLevel, int maxLevel,
                         int minPower, int maxPower, String position, String zone, String controller, Boolean faceUp) {}
    public record Operation(String type, int amount, String target, String zone, String duration,
                            Map<String,String> flags, List<Operation> children, Condition condition,
                            List<Operation> otherwise, Filter filter) {
        public Operation(String type,int amount,String target,String zone,String duration,Map<String,String> flags,List<Operation> children,Condition condition,List<Operation> otherwise) {
            this(type,amount,target,zone,duration,flags,children,condition,otherwise,null);
        }
    }
    public record Animation(String semantic, double windup, double release, double impact, double recovery) {}
    public record Presentation(String mode, String profile, int duration, Animation animation,
                               String particle,String fallback,String shape,String sound,List<VisualStage> stages) {
        public Presentation(String mode,String profile,int duration,Animation animation,String particle,String fallback,String shape,String sound) {this(mode,profile,duration,animation,particle,fallback,shape,sound,List.of());}
    }
    public record VisualStage(String semantic,String shape,String profile,double at) {}
    public static <T> List<T> list(List<T> value) { return value == null ? List.of() : value; }
    public static String value(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
    public static final Set<String> OPERATIONS = Set.of(
        "DAMAGE_LP","HEAL_LP","MODIFY_POWER","SET_POWER","SWAP_POWER","DRAW","SEARCH","REVEAL","SHUFFLE",
        "DISCARD","MILL","DESTROY","SEND_GRAVE","BANISH","RETURN_HAND","RETURN_DECK","SPECIAL_SUMMON","REVIVE",
        "SUMMON_FROM_HAND","SUMMON_FROM_GRAVE","SUMMON_FROM_BANISHED","CHANGE_POSITION","FLIP_FACE_UP","SET_FACE_DOWN",
        "NEGATE_EFFECT","NEGATE_ACTIVATION","NEGATE_ATTACK","PREVENT_DESTROY","PREVENT_TARGET","PREVENT_DAMAGE",
        "REDIRECT_TARGET","REDIRECT_ATTACK","CONTROL_CHANGE","COPY_EFFECT","EXTRA_ATTACK","CANNOT_ATTACK",
        "CANNOT_ACTIVATE","CANNOT_SUMMON","PIERCE","REFLECT_DAMAGE","ADD_COUNTER","REMOVE_COUNTER","GRANT_EFFECT",
        "REMOVE_EFFECT","LOOK_TOP_DECK","REORDER_DECK","MOVE_ZONE","APPLY_STATUS","REMOVE_STATUS","REMEMBER","DELAY","INSPECT_SET","REPLACE_DESTROY","IF");
    public static final Set<String> TRIGGERS = Set.of("ON_ACTIVATE","ON_SUMMON","ON_SPECIAL_SUMMON","ON_FLIP",
        "ON_ATTACK_DECLARE","ON_ATTACKED","ON_DAMAGE","ON_DESTROY","ON_DESTROYED","ON_SEND_GRAVE","ON_BANISH",
        "ON_RETURN","ON_DRAW","ON_DISCARD","ON_CHAIN","ON_CHAINED","ON_CHAIN_RESOLVE","ON_NEGATE",
        "ON_TURN_START","ON_TURN_END","ON_PHASE_START","ON_PHASE_END","ON_POSITION_CHANGE","ON_SET","WOULD_DESTROY","CONTINUOUS");
    private static final Set<String> SELECTORS=Set.of("TARGET","SELF","SOURCE","ALLY_MONSTER","ENEMY_MONSTER","ANY_MONSTER","ALLY_CARD","ENEMY_CARD","ANY_FIELD_CARD","ATTACKER","DEFENDER","CHAIN_SOURCE","CHAIN_TARGET","HAND","GRAVEYARD","BANISHED","DECK","EXTRA_DECK","TOP_DECK","BOTTOM_DECK","RANDOM_HAND","ALL_ALLIES","ALL_ENEMIES","ALL_FIELD","EVENT_CARD","EVENT_TARGET","PAID_COSTS","DESTROYED_THIS_RESOLUTION","BANISHED_THIS_RESOLUTION","REMEMBERED","PREVIOUS_CHAIN_SOURCE","PREVIOUS_CHAIN_TARGET");
    private static final Set<String> CONDITIONS=Set.of("AND","OR","NOT","TURN_OWNER","EVENT_CONTROLLER","PHASE","LP_AT_MOST","LP_AT_LEAST","CHAIN_AT_LEAST","COUNT_AT_LEAST","COUNT_AT_MOST","COUNTER_AT_LEAST","SOURCE_ZONE","TARGET_ZONE","CATEGORY","TYPE","TAG","LEVEL_AT_LEAST","POWER_AT_LEAST","POSITION","FACE_UP","FACE_DOWN","DESTROYED_BY_BATTLE","DESTROYED_BY_EFFECT","COUNTER_AT_MOST","SEEN_BY_CONTROLLER","EVENT_TYPE","SURVIVED","NOT_SELF");
    private static final Set<String> ZONES=Set.of("DECK","HAND","FIELD","SUPPORT","STADIUM","DISCARD","BANISHED","EXTRA");
    private static void selector(String value){if(value!=null&&!SELECTORS.contains(value))throw new IllegalArgumentException("Unknown effect selector "+value);}
    private static void filter(Filter f){if(f==null)return;if(f.minLevel()<0||f.maxLevel()>12||f.minPower()<0||f.maxPower()>100000||f.zone()!=null&&!ZONES.contains(f.zone())||f.position()!=null&&!Set.of("ATTACK","DEFENSE","FACE_DOWN_DEFENSE").contains(f.position()))throw new IllegalArgumentException("Invalid effect filter");}
    private static void condition(Condition c,int depth){if(c==null)return;if(depth>8||!CONDITIONS.contains(c.type())||list(c.children()).size()>16)throw new IllegalArgumentException("Invalid effect condition");selector(c.target());for(var child:list(c.children()))condition(child,depth+1);}
    public void validate() {
        if (list(triggers).isEmpty() || !TRIGGERS.containsAll(triggers)) throw new IllegalArgumentException("Invalid effect trigger");
        if (list(operations).isEmpty()&&list(stages).isEmpty()) throw new IllegalArgumentException("Empty effect composition");
        validateOperations(list(operations),0);for(var c:list(conditions))condition(c,0);for(var c:list(resolutionConditions))condition(c,0);
        if(targets!=null){selector(targets.selector());filter(targets.filter());if(targets.min()<0||targets.max()>20||targets.max()<targets.min())throw new IllegalArgumentException("Invalid effect target bounds");}
        for (Cost cost : list(costs)) if (cost.amount() < 0 || cost.amount()>100000 || !Set.of("LP_COST","DISCARD","TRIBUTE","BANISH_SELF",
            "BANISH_GRAVE","SEND_GRAVE","REVEAL_HAND","RETURN_SELF","RETURN_OTHER","LOCK_AFTER_USE","COUNTER_COST").contains(cost.type()))
            throw new IllegalArgumentException("Invalid effect cost");
        if(list(stages).size()>8)throw new IllegalArgumentException("Too many effect stages");
        Set<String> ids=new HashSet<>();for(var stage:list(stages)) {
            if(stage.id()==null||!stage.id().matches("[a-z0-9_]+")||!ids.add(stage.id())||stage.effect()==null||stage.effect().spec()==null)throw new IllegalArgumentException("Invalid effect stage");
            if(!list(stage.effect().spec().stages()).isEmpty())throw new IllegalArgumentException("Nested effect stages are not supported");
            stage.effect().spec().validate();if(!ZONES.containsAll(list(stage.sourceZones())))throw new IllegalArgumentException("Invalid stage zone");
        }
        if(limitScope!=null&&!Set.of("EFFECT","CARD_NAME","CARD").contains(limitScope))throw new IllegalArgumentException("Invalid effect limit scope");
        for(var cost:list(costs))if(cost.type().equals("COUNTER_COST")&&(cost.counter()==null||!cost.counter().matches("[a-z0-9_:-]+")||cost.amount()<1))throw new IllegalArgumentException("Invalid counter cost");
        if(vfx!=null){if(vfx.duration()<100||vfx.duration()>3000)throw new IllegalArgumentException("Invalid VFX lifetime");
            for(var stage:list(vfx.stages()))if(stage.at()<0||stage.at()>1)throw new IllegalArgumentException("Invalid VFX timing");}

    }
    private static void validateOperations(List<Operation> operations,int depth) {
        if (depth > 8 || operations.size() > 32) throw new IllegalArgumentException("Effect composition exceeds bounds");
        for (Operation op : operations) {
            selector(op.target());filter(op.filter());condition(op.condition(),0);
            if(op.target()==null&&!Set.of("DAMAGE_LP","HEAL_LP","DRAW","MILL","SHUFFLE","LOOK_TOP_DECK","REORDER_DECK","DELAY","IF","NEGATE_EFFECT","NEGATE_ACTIVATION","NEGATE_ATTACK").contains(op.type()))throw new IllegalArgumentException("Missing target selector for "+op.type());
            if(op.flags()!=null&&op.flags().containsKey("counter")&&!op.flags().get("counter").matches("[a-z0-9_:-]+"))throw new IllegalArgumentException("Invalid counter identifier");
            if(Math.abs((long)op.amount())>100000||op.zone()!=null&&!ZONES.contains(op.zone()))throw new IllegalArgumentException("Invalid effect operation bounds");
            if(op.duration()!=null&&!Set.of("TURN_END","NEXT_TURN_END","PERMANENT").contains(op.duration())&&!op.duration().matches("TURNS:(?:[1-9]|1[0-9]|20)"))throw new IllegalArgumentException("Invalid effect duration");
            if(op.type().equals("DELAY")&&(!TRIGGERS.contains(op.flags()==null?"":op.flags().getOrDefault("trigger",""))||list(op.children()).isEmpty()))throw new IllegalArgumentException("Invalid delayed trigger");
            if(op.flags()!=null&&op.flags().containsKey("count")&&(Integer.parseInt(op.flags().get("count"))<1||Integer.parseInt(op.flags().get("count"))>20))throw new IllegalArgumentException("Invalid operation count");
            if (!OPERATIONS.contains(op.type())) throw new IllegalArgumentException("Unknown effect operation " + op.type());
            validateOperations(list(op.children()),depth+1);validateOperations(list(op.otherwise()),depth+1);
        }
    }
}
