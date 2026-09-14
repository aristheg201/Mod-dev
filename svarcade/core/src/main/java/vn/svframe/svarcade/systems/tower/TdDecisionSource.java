package vn.svframe.svarcade.systems.tower;

import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.*;
import vn.svframe.svarcade.bot.*;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.runtime.*;
import vn.svframe.svarcade.security.IntentGate;
import vn.svframe.svarcade.systems.currency.*;
import vn.svframe.svarcade.systems.deployable.*;
import vn.svframe.svarcade.systems.loadout.*;
import vn.svframe.svarcade.systems.targeting.*;
import vn.svframe.svarcade.systems.upgrade.*;

/** Enumerates legal deployable decisions on the owner thread, then gives workers only immutable candidates. */
public final class TdDecisionSource implements SessionSystem, BotDecisionSource {
    public static final Id ID = Id.of("svarcade:td_bot_source");
    public static final Id STRATEGY = Id.of("svarcade:td_policy");

    private record Actions(Id deploy, Id move, Id recall, Id upgrade, Id target) {
        Set<Id> ids() { return Set.of(deploy, move, recall, upgrade, target); }
        static Actions parse(Node n) {
            n.only("deploy","move","recall","upgrade","target");
            Actions actions=new Actions(Id.of(n.string("deploy")),Id.of(n.string("move")),Id.of(n.string("recall")),Id.of(n.string("upgrade")),Id.of(n.string("target")));
            if(actions.ids().size()!=5) throw n.error("deploy","TD bot action ids must be distinct"); return actions;
        }
    }
    private record Config(Actions actions, Id currency, Id profile, Set<String> allowedStates) {
        Config { allowedStates=Set.copyOf(allowedStates); }
        static Config parse(Node n) {
            n.only("actions","currency","profile","allowed_states"); Set<String> states=n.has("allowed_states")?n.strings("allowed_states"):Set.of();
            for(String state:states) if(!state.matches("[A-Za-z0-9_-]{1,80}")) throw n.error("allowed_states","Invalid state");
            return new Config(Actions.parse(n.node("actions")),Id.of(n.string("currency")),Id.of(n.string("profile")),states);
        }
    }
    private enum Kind { UPGRADE, DEPLOY, TARGET, MOVE, RECALL }
    private record Tuning(long reserve, int maxTowers, int maxCandidates, double mistakeChance, List<DeployableAccess.Point> placements,
                          List<Id> upgrades, List<TargetingAccess.Mode> modes, List<Kind> priority) {
        Tuning { placements=List.copyOf(placements); upgrades=List.copyOf(upgrades); modes=List.copyOf(modes); priority=List.copyOf(priority); }
        static Tuning parse(Node n) {
            n.only("reserve_currency","max_towers","max_candidates","mistake_chance","placements","upgrade_priority","target_modes","priority");
            List<DeployableAccess.Point> placements=new ArrayList<>();
            for(Object raw:n.list("placements")) {
                if(!(raw instanceof List<?> p)||p.size()!=3||p.stream().anyMatch(v->!(v instanceof Number))) throw n.error("placements","Expected xyz triples");
                placements.add(new DeployableAccess.Point(((Number)p.get(0)).doubleValue(),((Number)p.get(1)).doubleValue(),((Number)p.get(2)).doubleValue()));
            }
            if(placements.isEmpty()||placements.size()>128) throw n.error("placements","Expected 1..128 placements");
            List<Id> upgrades=n.strings("upgrade_priority").stream().map(Id::of).toList();
            List<TargetingAccess.Mode> modes=new ArrayList<>(); for(String raw:n.strings("target_modes")) try{modes.add(TargetingAccess.Mode.valueOf(raw));}catch(IllegalArgumentException e){throw n.error("target_modes","Unknown mode");}
            List<Kind> priority=new ArrayList<>(); for(String raw:n.strings("priority")) try{priority.add(Kind.valueOf(raw));}catch(IllegalArgumentException e){throw n.error("priority","Unknown policy kind");}
            if(new HashSet<>(priority).size()!=priority.size()||priority.isEmpty()) throw n.error("priority","Priority must be non-empty and unique");
            return new Tuning(n.integer("reserve_currency",0,Long.MAX_VALUE),(int)n.integer("max_towers",1,256),(int)n.integer("max_candidates",1,4096),
                    Numbers.decimal(n,"mistake_chance",0,1),placements,upgrades,modes,priority);
        }
    }
    private record Candidate(Kind kind, BotRuntime.Decision decision) { }

    public static final class Plan implements SystemSchema,SystemFactory {
        private final Function<GenericSession,BiFunction<UUID,Long,IntentGate.Facts>> facts;
        public Plan(Function<GenericSession,BiFunction<UUID,Long,IntentGate.Facts>> facts){this.facts=Objects.requireNonNull(facts);}
        @Override public void validate(Node config){Config.parse(config);}
        @Override public Set<Id> dependencies(Node config){
            Set<Id> result=new LinkedHashSet<>(Set.of(CurrencySystem.ID,LoadoutSystem.ID,DeployableSystem.ID,UpgradeSystem.ID,TowerSystem.ID)); if(!Config.parse(config).allowedStates().isEmpty()) result.add(StateMachineSystem.ID); return Set.copyOf(result);
        }
        @Override public Set<SessionServices.Key<?>> requires(Node config){
            Set<SessionServices.Key<?>> result=new LinkedHashSet<>(Set.of(CurrencySystem.ACCESS,LoadoutAccess.ACCESS,DeployableAccess.ACCESS,UpgradeAccess.ACCESS,TowerAccess.ACCESS)); if(!Config.parse(config).allowedStates().isEmpty()) result.add(StateMachineAccess.ACCESS); return Set.copyOf(result);
        }
        @Override public Set<SessionServices.Key<?>> provides(){return Set.of(BotDecisionSource.ACCESS);}
        @Override public SessionSystem create(GenericSession session,Node config){
            Config parsed=Config.parse(config); TdDecisionSource source=new TdDecisionSource(parsed,session,facts.apply(session)); session.services().provide(BotDecisionSource.ACCESS,source); return source;
        }
    }

    private final Config config; private final GenericSession session; private final ThreadGuard thread; private final CurrencyAccess currency;
    private final LoadoutAccess loadouts; private final DeployableAccess deployables; private final UpgradeAccess upgrades; private final TowerAccess towers;
    private final StateMachineAccess fsm; private final BiFunction<UUID,Long,IntentGate.Facts> facts; private boolean active,closed;
    private TdDecisionSource(Config config,GenericSession session,BiFunction<UUID,Long,IntentGate.Facts> facts){
        this.config=config;this.session=session;thread=session.thread();currency=session.services().require(CurrencySystem.ACCESS);loadouts=session.services().require(LoadoutAccess.ACCESS);
        deployables=session.services().require(DeployableAccess.ACCESS);upgrades=session.services().require(UpgradeAccess.ACCESS);towers=session.services().require(TowerAccess.ACCESS);
        fsm=config.allowedStates().isEmpty()?null:session.services().require(StateMachineAccess.ACCESS);this.facts=Objects.requireNonNull(facts);
        if(!deployables.profiles().containsKey(config.profile())) throw new ConfigException("TD bot profile references unknown deployable profile");
    }
    private void requireActive(){thread.check();if(!active||closed)throw new IllegalStateException("TD bot source inactive");}
    @Override public void start(){thread.check();if(active||closed)throw new IllegalStateException("TD bot source already initialized");active=true;}
    @Override public Set<Id> requiredActions(){requireActive();return config.actions().ids();}
    @Override public CompiledProfile compile(BotProfile profile){
        requireActive();if(!profile.strategy().equals(STRATEGY))throw new ConfigException("Unsupported TD bot strategy: "+profile.strategy());Tuning tuning=Tuning.parse(profile.parameters());
        return (actor,seed)->{
            requireActive();if(!eligible(actor))throw new IllegalStateException("TD bot not eligible");List<Candidate> frozen=List.copyOf(legal(actor,tuning));
            return (context,budget)->choose(frozen,tuning,seed,budget);
        };
    }
    private BotRuntime.Decision choose(List<Candidate> candidates,Tuning tuning,long seed,ThinkBudget budget){
        budget.check();if(candidates.isEmpty())throw new CancellationException("No legal TD bot action");
        long mixed=mix(seed);double roll=(mixed>>>11)*0x1.0p-53;
        if(roll<tuning.mistakeChance()){budget.visit();return candidates.get((int)Math.floorMod(mixed,candidates.size())).decision();}
        for(Kind kind:tuning.priority())for(Candidate candidate:candidates){budget.visit();if(candidate.kind()==kind)return candidate.decision();}
        budget.visit();return candidates.getFirst().decision();
    }
    private List<Candidate> legal(UUID actor,Tuning tuning){
        List<Candidate> out=new ArrayList<>();long balance=currency.balance(actor,config.currency());List<DeployableAccess.Deployment> allOwned=deployables.owned(actor);
        List<DeployableAccess.Deployment> owned=allOwned.stream().limit(tuning.maxTowers()).toList();DeployableAccess.Profile profile=deployables.profiles().get(config.profile());Set<String> deployedSources=new HashSet<>();allOwned.forEach(d->deployedSources.add(d.sourceId()));
        if(allOwned.size()<tuning.maxTowers()&&balance-profile.deployCost()>=tuning.reserve()){
            for(LoadoutAccess.Snapshot snapshot:loadouts.owned(actor)){if(full(out,tuning))break;if(!deployedSources.contains(snapshot.sourceId())){
                for(DeployableAccess.Point point:tuning.placements())try{
                    deployables.prepareDeploy(actor,snapshot.sourceId(),config.profile(),point);out.add(new Candidate(Kind.DEPLOY,new BotRuntime.Decision(config.actions().deploy(),Map.of("source",snapshot.sourceId(),"profile",config.profile().toString(),"x",point.x(),"y",point.y(),"z",point.z()))));break;
                }catch(IllegalArgumentException|IllegalStateException ignored){ }
            }}
        }
        for(DeployableAccess.Deployment deployment:owned){if(full(out,tuning))break;
            for(Id upgrade:tuning.upgrades())try{
                UpgradeAccess.Definition definition=upgrades.definitions().get(upgrade);if(definition==null)continue;int level=upgrades.level(deployment.id(),upgrade);if(level>=definition.maxLevel()||balance-definition.costs().get(level)<tuning.reserve())continue;
                upgrades.preparePurchase(actor,deployment.id(),upgrade);out.add(new Candidate(Kind.UPGRADE,new BotRuntime.Decision(config.actions().upgrade(),Map.of("deployment",deployment.id(),"upgrade",upgrade.toString()))));break;
            }catch(IllegalArgumentException|IllegalStateException ignored){ }
            if(full(out,tuning))break;TowerAccess.View tower=towers.tower(deployment.id()).orElse(null);if(tower!=null)for(TargetingAccess.Mode mode:tuning.modes())if(mode!=tower.mode())try{
                towers.prepareTargetMode(actor,deployment.id(),mode);out.add(new Candidate(Kind.TARGET,new BotRuntime.Decision(config.actions().target(),Map.of("deployment",deployment.id(),"mode",mode.name()))));break;
            }catch(IllegalArgumentException|IllegalStateException ignored){ }
            if(full(out,tuning))break;if(balance-profile.moveCost()>=tuning.reserve())for(DeployableAccess.Point point:tuning.placements())if(!point.equals(deployment.position()))try{
                deployables.prepareMove(actor,deployment.id(),point);out.add(new Candidate(Kind.MOVE,new BotRuntime.Decision(config.actions().move(),Map.of("deployment",deployment.id(),"x",point.x(),"y",point.y(),"z",point.z()))));break;
            }catch(IllegalArgumentException|IllegalStateException ignored){ }
            if(full(out,tuning))break;try{deployables.prepareRecall(actor,deployment.id());out.add(new Candidate(Kind.RECALL,new BotRuntime.Decision(config.actions().recall(),Map.of("deployment",deployment.id()))));}catch(IllegalArgumentException|IllegalStateException ignored){ }
        }
        return out;
    }
    private static boolean full(List<Candidate> values,Tuning tuning){return values.size()>=tuning.maxCandidates();}
    @Override public boolean eligible(UUID actor){requireActive();Participant participant=session.participants().get(actor);return participant!=null&&participant.kind()==Participant.Kind.BOT&&(fsm==null||config.allowedStates().contains(fsm.state()))&&!loadouts.owned(actor).isEmpty();}
    @Override public IntentGate.Facts facts(UUID actor,long tick){requireActive();IntentGate.Facts result=Objects.requireNonNull(facts.apply(actor,tick));if(!result.actor().equals(actor)||result.tick()!=tick)throw new IllegalStateException("Mismatched TD bot facts");return result;}
    @Override public void tick(long tick){requireActive();}
    @Override public int stateSchema(){return 1;}
    @Override public Map<String,Object> snapshot(){requireActive();return Map.of();}
    @Override public void restore(int schema,Map<String,Object> state){if(schema!=1||!state.isEmpty())throw new ConfigException("Invalid TD bot source state");start();}
    @Override public void close(){thread.check();active=false;closed=true;}
    private static long mix(long z){z=(z^(z>>>30))*0xbf58476d1ce4e5b9L;z=(z^(z>>>27))*0x94d049bb133111ebL;return z^(z>>>31);}
}
