package vn.svframe.svarcade.systems.wave;

import java.util.*;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.runtime.*;
import vn.svframe.svarcade.systems.currency.*;
import vn.svframe.svarcade.systems.enemy.*;
import vn.svframe.svarcade.systems.objective.*;

/** Durable bridge from enemy/wave outcomes into match currency and generic objective state. */
public final class OutcomeCoordinatorSystem implements SessionSystem {
    public static final Id ID = Id.of("svarcade:outcome_coordinator");
    public enum Distribution { EACH, SPLIT }

    public record Config(Id currency, Id livesCounter, Id killsCounter, long leakLives,
                         Id victoryReason, Id defeatReason, Distribution distribution,
                         int maxOutcomesPerTick, int historyCapacity) {
        public Config {
            Objects.requireNonNull(currency); Objects.requireNonNull(livesCounter); Objects.requireNonNull(killsCounter);
            Objects.requireNonNull(victoryReason); Objects.requireNonNull(defeatReason); Objects.requireNonNull(distribution);
            if (leakLives < 1 || leakLives > 1_000_000 || maxOutcomesPerTick < 1 || maxOutcomesPerTick > 4096
                    || historyCapacity < 1 || historyCapacity > 1_000_000) throw new ConfigException("Outcome coordinator limits");
        }
        public static Config parse(Node n) {
            n.only("currency","lives_counter","kills_counter","leak_lives","victory_reason","defeat_reason","reward_distribution","max_outcomes_per_tick","history_capacity");
            Distribution distribution;
            try { distribution = Distribution.valueOf(n.string("reward_distribution")); }
            catch (IllegalArgumentException e) { throw n.error("reward_distribution","Unknown reward distribution"); }
            return new Config(Id.of(n.string("currency")),Id.of(n.string("lives_counter")),Id.of(n.string("kills_counter")),
                    n.integer("leak_lives",1,1_000_000),Id.of(n.string("victory_reason")),Id.of(n.string("defeat_reason")),distribution,
                    (int)n.integer("max_outcomes_per_tick",1,4096),(int)n.integer("history_capacity",1,1_000_000));
        }
    }

    public static final class Plan implements SystemSchema,SystemFactory {
        @Override public void validate(Node config){Config.parse(config);}
        @Override public Set<Id> dependencies(){return Set.of(EnemySystem.ID,WaveSystem.ID,CurrencySystem.ID,ObjectiveSystem.ID);}
        @Override public Set<SessionServices.Key<?>> requires(){return Set.of(EnemyAccess.ACCESS,WaveAccess.ACCESS,CurrencySystem.ACCESS,ObjectiveSystem.ACCESS);}
        @Override public SessionSystem create(GenericSession session,Node config){return new OutcomeCoordinatorSystem(Config.parse(config),session);}
    }

    private final Config config; private final GenericSession session; private final ThreadGuard thread; private final EnemyAccess enemies;
    private final WaveAccess waves; private final CurrencyAccess currency; private final ObjectiveAccess objectives;
    private final NavigableSet<Long> processedSequences=new TreeSet<>(); private final NavigableSet<Integer> rewardedWaves=new TreeSet<>();
    private long revision; private boolean active,closed,victoryCommitted;

    private OutcomeCoordinatorSystem(Config config,GenericSession session){
        this.config=config;this.session=session;thread=session.thread();enemies=session.services().require(EnemyAccess.ACCESS);waves=session.services().require(WaveAccess.ACCESS);
        currency=session.services().require(CurrencySystem.ACCESS);objectives=session.services().require(ObjectiveSystem.ACCESS);
        objectives.value(config.livesCounter());objectives.value(config.killsCounter());
        if(!objectives.reasons().contains(config.victoryReason())||!objectives.reasons().contains(config.defeatReason())) throw new ConfigException("Outcome reason not registered");
        for(Participant participant:session.participants().values())if(participant.kind()!=Participant.Kind.SPECTATOR)currency.balance(participant.id(),config.currency());
    }
    private void requireActive(){thread.check();if(!active||closed)throw new IllegalStateException("Outcome coordinator inactive");}
    @Override public void start(){thread.check();if(active||closed)throw new IllegalStateException("Outcome coordinator already initialized");active=true;}

    @Override public void tick(long tick){
        requireActive();if(objectives.result().isPresent())return;boolean changed=false;
        for(EnemyAccess.Outcome outcome:enemies.drainOutcomes(config.maxOutcomesPerTick())){
            if(processedSequences.contains(outcome.sequence()))continue;
            if(processedSequences.size()>=config.historyCapacity())throw new IllegalStateException("Processed outcome history capacity");
            Map<Id,Long> deltas=new LinkedHashMap<>();Optional<ObjectiveAccess.Result> finish=Optional.empty();
            if(outcome.cause()==EnemyAccess.Cause.DEATH){deltas.put(config.killsCounter(),1L);applyReward(outcome.reward());}
            else {
                long lives=objectives.value(config.livesCounter());long loss=Math.min(lives,config.leakLives());deltas.put(config.livesCounter(),-loss);
                if(lives-loss<=0)finish=Optional.of(new ObjectiveAccess.Result(config.defeatReason(),Set.of(),false));
            }
            StateChange objective=objectives.prepare(deltas,finish);objective.apply();processedSequences.add(outcome.sequence());revision=Math.incrementExact(revision);changed=true;
        }
        Optional<WaveAccess.Clear> clear=waves.clearEvent();
        if(clear.isPresent()&&!rewardedWaves.contains(clear.get().waveIndex())){
            if(rewardedWaves.size()>=config.historyCapacity())throw new IllegalStateException("Wave reward history capacity");
            applyReward(clear.get().reward());rewardedWaves.add(clear.get().waveIndex());revision=Math.incrementExact(revision);changed=true;
        }
        if(waves.complete()&&!victoryCommitted&&objectives.result().isEmpty()){
            Set<String>winners=new LinkedHashSet<>();for(Participant participant:session.participants().values())if(participant.kind()!=Participant.Kind.SPECTATOR)winners.add(participant.team());
            objectives.prepare(Map.of(),Optional.of(new ObjectiveAccess.Result(config.victoryReason(),winners,false))).apply();victoryCommitted=true;revision=Math.incrementExact(revision);changed=true;
        }
        if(changed)session.markDirty();
    }

    private void applyReward(Map<String,Object> reward){
        if(reward.isEmpty())return;Node n=new Node(reward,"outcome-reward");n.only("currency","amount");Id id=Id.of(n.string("currency"));
        if(!id.equals(config.currency()))throw new ConfigException("Outcome reward uses unsupported match currency");long amount=n.integer("amount",0,Long.MAX_VALUE);if(amount==0)return;
        List<UUID> actors=session.participants().values().stream().filter(p->p.kind()!=Participant.Kind.SPECTATOR).map(Participant::id).sorted().toList();if(actors.isEmpty())return;
        List<CurrencyAccess.Delta>deltas=new ArrayList<>();
        if(config.distribution()==Distribution.EACH){for(UUID actor:actors)deltas.add(new CurrencyAccess.Delta(actor,id,amount));}
        else {long base=amount/actors.size(),remainder=amount%actors.size();for(int i=0;i<actors.size();i++)deltas.add(new CurrencyAccess.Delta(actors.get(i),id,base+(i<remainder?1:0)));}
        currency.prepare(deltas).apply();
    }

    @Override public int stateSchema(){return 1;}
    @Override public Map<String,Object> snapshot(){requireActive();return Values.map(Map.of("revision",revision,"processed",processedSequences.stream().toList(),"rewarded_waves",rewardedWaves.stream().toList(),"victory_committed",victoryCommitted));}
    @Override public void restore(int schema,Map<String,Object> saved){
        thread.check();if(active||closed||schema!=1)throw new IllegalArgumentException("Invalid outcome coordinator restore");Node n=new Node(saved,"outcome-coordinator-state");n.only("revision","processed","rewarded_waves","victory_committed");
        if(n.list("processed").size()>config.historyCapacity()||n.list("rewarded_waves").size()>config.historyCapacity())throw new ConfigException("Outcome history capacity exceeded");
        for(Object raw:n.list("processed")){if(!(raw instanceof Integer||raw instanceof Long))throw new ConfigException("Invalid processed sequence");long value=((Number)raw).longValue();if(value<1||!processedSequences.add(value))throw new ConfigException("Duplicate processed sequence");}
        for(Object raw:n.list("rewarded_waves")){if(!(raw instanceof Integer||raw instanceof Long))throw new ConfigException("Invalid rewarded wave");int value=Math.toIntExact(((Number)raw).longValue());if(value<0||!rewardedWaves.add(value))throw new ConfigException("Duplicate rewarded wave");}
        revision=n.integer("revision",0,Long.MAX_VALUE-1);victoryCommitted=n.bool("victory_committed",false);if(victoryCommitted&&objectives.result().isEmpty())throw new ConfigException("Victory marker without objective result");active=true;
    }
    @Override public void close(){thread.check();active=false;closed=true;processedSequences.clear();rewardedWaves.clear();}
}
