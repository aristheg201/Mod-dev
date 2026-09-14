package vn.svframe.svarcade.systems.tower;

import java.nio.charset.StandardCharsets;
import java.util.*;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.runtime.*;
import vn.svframe.svarcade.systems.combat.*;
import vn.svframe.svarcade.systems.deployable.*;
import vn.svframe.svarcade.systems.enemy.*;
import vn.svframe.svarcade.systems.path.Vec3;
import vn.svframe.svarcade.systems.targeting.*;
import vn.svframe.svarcade.systems.upgrade.*;

/** Deadline-driven deployable combat with spatially bounded instant/projectile/AOE/chain delivery. */
public final class TowerSystem implements SessionSystem, TowerAccess {
    public static final Id ID = Id.of("svarcade:towers");

    public enum Effect {
        DAMAGE_ADD, DAMAGE_MULTIPLIER_DELTA, RANGE_ADD, RANGE_MULTIPLIER_DELTA,
        COOLDOWN_ADD_TICKS, COOLDOWN_MULTIPLIER_DELTA, CRIT_CHANCE_ADD, CRIT_MULTIPLIER_ADD
    }

    public record Profile(CombatAccess.Attack attack, Set<TargetingAccess.Mode> modes, TargetingAccess.Mode defaultMode,
                          TargetingAccess.Filter filter, long projectileDelayTicks, double aoeRadius) {
        public Profile {
            Objects.requireNonNull(attack); modes = Set.copyOf(modes); Objects.requireNonNull(defaultMode); Objects.requireNonNull(filter);
            if (modes.isEmpty() || !modes.contains(defaultMode) || attack.cooldownTicks() < 1 || projectileDelayTicks < 1 || projectileDelayTicks > 1_000_000
                    || !Double.isFinite(aoeRadius) || aoeRadius < 0 || aoeRadius > 1_000_000) throw new ConfigException("Invalid tower profile");
            if (attack.delivery() == CombatAccess.Delivery.AOE && (attack.aoeCap() < 1 || aoeRadius <= 0)) throw new ConfigException("AOE delivery requires cap and radius");
        }
    }

    public record Config(int maxAttacksPerTick, long idleRetryTicks, int maxProjectiles, int maxProjectileImpactsPerTick,
                         Map<Id, Profile> profiles, Map<Id, Effect> modifierBindings) {
        public Config {
            profiles = Map.copyOf(profiles); modifierBindings = Map.copyOf(modifierBindings);
            if (maxAttacksPerTick < 1 || maxAttacksPerTick > 100_000 || idleRetryTicks < 1 || idleRetryTicks > 1_000_000
                    || maxProjectiles < 1 || maxProjectiles > 1_000_000 || maxProjectileImpactsPerTick < 1 || maxProjectileImpactsPerTick > 100_000
                    || profiles.isEmpty() || profiles.size() > 4096 || modifierBindings.size() > 256) throw new ConfigException("Tower runtime limits");
        }
        public static Config parse(Node n) {
            n.only("max_attacks_per_tick", "idle_retry_ticks", "max_projectiles", "max_projectile_impacts_per_tick", "profiles", "modifier_bindings");
            Map<Id, Profile> profiles = new LinkedHashMap<>(); Node values = n.node("profiles");
            for (String raw : values.values().keySet()) {
                Node p = values.node(raw); p.only("attack", "target_modes", "default_target_mode", "filter", "projectile_delay_ticks", "aoe_radius");
                CombatAccess.Attack attack = attack(p.node("attack")); Set<TargetingAccess.Mode> modes = new LinkedHashSet<>();
                for (String name : p.strings("target_modes")) try { modes.add(TargetingAccess.Mode.valueOf(name)); } catch (IllegalArgumentException e) { throw p.error("target_modes", "Unknown targeting mode"); }
                TargetingAccess.Mode defaultMode; try { defaultMode = TargetingAccess.Mode.valueOf(p.string("default_target_mode")); } catch (IllegalArgumentException e) { throw p.error("default_target_mode", "Unknown targeting mode"); }
                TargetingAccess.Filter filter = p.has("filter") ? filter(p.node("filter")) : TargetingAccess.Filter.unrestricted();
                long projectileDelay = p.has("projectile_delay_ticks") ? p.integer("projectile_delay_ticks", 1, 1_000_000) : 1;
                double aoeRadius = p.has("aoe_radius") ? Numbers.decimal(p, "aoe_radius", 0, 1_000_000) : 0;
                if (profiles.putIfAbsent(Id.of(raw), new Profile(attack, modes, defaultMode, filter, projectileDelay, aoeRadius)) != null) throw new ConfigException("Duplicate tower profile");
            }
            Map<Id, Effect> bindings = new LinkedHashMap<>();
            if (n.has("modifier_bindings")) {
                Node b = n.node("modifier_bindings");
                for (String raw : b.values().keySet()) {
                    Effect effect; try { effect = Effect.valueOf(b.string(raw)); } catch (IllegalArgumentException e) { throw b.error(raw, "Unknown tower modifier effect"); }
                    if (bindings.putIfAbsent(Id.of(raw), effect) != null) throw new ConfigException("Duplicate tower modifier binding");
                }
            }
            return new Config((int) n.integer("max_attacks_per_tick", 1, 100_000), n.integer("idle_retry_ticks", 1, 1_000_000),
                    n.has("max_projectiles") ? (int)n.integer("max_projectiles",1,1_000_000) : 4096,
                    n.has("max_projectile_impacts_per_tick") ? (int)n.integer("max_projectile_impacts_per_tick",1,100_000) : 512, profiles, bindings);
        }
        private static CombatAccess.Attack attack(Node n) {
            n.only("damage_type", "damage", "range", "cooldown_ticks", "crit_chance", "crit_multiplier", "delivery", "aoe_cap", "chain_cap", "statuses");
            CombatAccess.Delivery delivery; try { delivery = CombatAccess.Delivery.valueOf(n.string("delivery")); } catch (IllegalArgumentException e) { throw n.error("delivery", "Unknown delivery type"); }
            List<CombatAccess.StatusAttempt> statuses = new ArrayList<>();
            if (n.has("statuses")) for (Node s : n.nodes("statuses")) {
                s.only("status", "chance", "duration_multiplier", "stacks"); statuses.add(new CombatAccess.StatusAttempt(Id.of(s.string("status")), Numbers.decimal(s, "chance", 0, 1),
                        Numbers.decimal(s, "duration_multiplier", Double.MIN_NORMAL, 100), (int) s.integer("stacks", 1, 1024)));
            }
            return new CombatAccess.Attack(Id.of(n.string("damage_type")), Numbers.decimal(n, "damage", 0, 1_000_000_000), Numbers.decimal(n, "range", 0, 1_000_000),
                    n.integer("cooldown_ticks", 1, 1_000_000), Numbers.decimal(n, "crit_chance", 0, 1), Numbers.decimal(n, "crit_multiplier", 1, 100), delivery,
                    (int) n.integer("aoe_cap", 0, 100_000), (int) n.integer("chain_cap", 0, 100_000), statuses);
        }
        private static TargetingAccess.Filter filter(Node n) { n.only("all", "any", "none"); return new TargetingAccess.Filter(strings(n,"all"),strings(n,"any"),strings(n,"none")); }
        private static Set<String> strings(Node n,String field){if(!n.has(field))return Set.of();Set<String>result=new LinkedHashSet<>();for(String value:n.strings(field)){if(value.isBlank()||value.length()>160||!result.add(value))throw n.error(field,"Invalid or duplicate target tag");}return Set.copyOf(result);}
    }

    public static final class Plan implements SystemSchema,SystemFactory {
        @Override public void validate(Node config){Config.parse(config);}
        @Override public Set<Id> dependencies(){return Set.of(DeployableSystem.ID,UpgradeSystem.ID,TargetingSystem.ID,CombatSystem.ID,EnemySystem.ID);}
        @Override public Set<SessionServices.Key<?>> requires(){return Set.of(DeployableAccess.ACCESS,UpgradeAccess.ACCESS,TargetingSystem.ACCESS,CombatAccess.ACCESS,EnemyAccess.ACCESS);}
        @Override public Set<SessionServices.Key<?>> provides(){return Set.of(ACCESS);}
        @Override public SessionSystem create(GenericSession session,Node config){TowerSystem system=new TowerSystem(Config.parse(config),session);session.services().provide(ACCESS,system);return system;}
    }

    private static final class State { final long deployment; TargetingAccess.Mode mode; long nextDue; UUID lastTarget; State(long deployment,TargetingAccess.Mode mode,long nextDue,UUID lastTarget){this.deployment=deployment;this.mode=mode;this.nextDue=nextDue;this.lastTarget=lastTarget;} }
    private record Projectile(long id,long due,long deployment,UUID target,CombatAccess.Resolution resolution) { }

    private final Config config; private final GenericSession session; private final ThreadGuard thread; private final DeployableAccess deployables; private final UpgradeAccess upgrades;
    private final TargetingAccess targeting; private final CombatAccess combat; private final EnemyAccess enemies; private final NavigableMap<Long,State> states=new TreeMap<>();
    private final NavigableMap<Long,Projectile> projectiles=new TreeMap<>(); private long nextProjectile=1,elapsed,lastTick=-1,revision,attacks,misses; private boolean active,closed;

    private TowerSystem(Config config,GenericSession session){this.config=config;this.session=session;thread=session.thread();deployables=session.services().require(DeployableAccess.ACCESS);upgrades=session.services().require(UpgradeAccess.ACCESS);targeting=session.services().require(TargetingSystem.ACCESS);combat=session.services().require(CombatAccess.ACCESS);enemies=session.services().require(EnemyAccess.ACCESS);for(Id id:config.profiles().keySet())if(!deployables.profiles().containsKey(id))throw new ConfigException("Tower profile has no matching deployable profile: "+id);validateAttacks();}
    private void validateAttacks(){CombatAccess.Target target=new CombatAccess.Target(0,Map.of(),Set.of(),Map.of());for(Profile profile:config.profiles().values())try{combat.resolve(profile.attack(),target,0);}catch(IllegalArgumentException e){throw new ConfigException("Invalid tower combat profile",e);}}
    private void requireActive(){thread.check();if(!active||closed)throw new IllegalStateException("Towers inactive");}
    @Override public void start(){thread.check();if(active||closed)throw new IllegalStateException("Towers already initialized");active=true;sync();}
    @Override public long revision(){requireActive();return revision;} @Override public long attacks(){requireActive();return attacks;} @Override public long misses(){requireActive();return misses;}
    @Override public Optional<View> tower(long deployment){requireActive();State state=states.get(deployment);return state==null?Optional.empty():Optional.of(view(state));}
    @Override public List<View> towers(UUID owner){requireActive();Objects.requireNonNull(owner);List<View>result=new ArrayList<>();for(DeployableAccess.Deployment deployment:deployables.owned(owner)){State state=states.get(deployment.id());if(state!=null)result.add(view(state));}return List.copyOf(result);}
    private View view(State state){return new View(state.deployment,state.mode,state.nextDue,Optional.ofNullable(state.lastTarget));}

    @Override public StateChange prepareTargetMode(UUID actor,long deploymentId,TargetingAccess.Mode mode){
        requireActive();Objects.requireNonNull(mode);DeployableAccess.Deployment deployment=deployables.deployment(deploymentId).orElseThrow(()->new IllegalArgumentException("Unknown deployment"));if(!deployment.owner().equals(actor))throw new IllegalArgumentException("Tower ownership");State state=states.get(deploymentId);Profile profile=state==null?null:config.profiles().get(deployment.profile());if(state==null||profile==null||!profile.modes().contains(mode)||revision==Long.MAX_VALUE)throw new IllegalArgumentException("Target mode unavailable");TargetingAccess.Mode before=state.mode;long expected=revision;
        return new StateChange(){private int used;@Override public void apply(){requireActive();if(used!=0||revision!=expected||states.get(deploymentId)!=state||state.mode!=before)throw new IllegalStateException("Stale target-mode transaction");state.mode=mode;revision++;used=1;}@Override public void rollback(){requireActive();if(used!=1||revision!=expected+1||states.get(deploymentId)!=state||state.mode!=mode)throw new IllegalStateException("Target-mode rollback conflict");state.mode=before;revision=expected;used=2;}};
    }

    @Override public void tick(long tick){
        requireActive();if(tick<0||lastTick>=0&&tick<=lastTick)throw new IllegalArgumentException("Non-increasing tower tick");long delta=lastTick<0?0:tick-lastTick;elapsed=Math.addExact(elapsed,delta);lastTick=tick;boolean changed=sync();
        changed|=impactProjectiles();int budget=config.maxAttacksPerTick();
        for(State state:states.values()){
            if(budget<=0)break;if(state.nextDue>elapsed)continue;budget--;DeployableAccess.Deployment deployment=deployables.deployment(state.deployment).orElse(null);if(deployment==null)continue;Profile profile=config.profiles().get(deployment.profile());if(profile==null)continue;CombatAccess.Attack attack=effective(profile.attack(),upgrades.modifiers(deployment.id()));DeployableAccess.Point point=deployment.position();Vec3 origin=new Vec3(point.x(),point.y(),point.z());UUID requester=requester(deployment.id());Optional<TargetingAccess.Target> selected=targeting.select(requester,new TargetingAccess.Query(origin,attack.range(),state.mode,profile.filter()));
            if(selected.isEmpty()){state.nextDue=Math.addExact(elapsed,config.idleRetryTicks());misses=Math.incrementExact(misses);changed=true;continue;}
            UUID target=selected.get().id();if(enemies.enemy(target).isEmpty()){targeting.remove(target);state.nextDue=Math.addExact(elapsed,config.idleRetryTicks());misses=Math.incrementExact(misses);changed=true;continue;}
            fire(deployment,profile,attack,selected.get(),requester);state.nextDue=Math.addExact(elapsed,attack.cooldownTicks());state.lastTarget=target;attacks=Math.incrementExact(attacks);changed=true;
        }
        if(changed){revision=Math.incrementExact(revision);session.markDirty();}
    }

    private void fire(DeployableAccess.Deployment deployment,Profile profile,CombatAccess.Attack attack,TargetingAccess.Target primary,UUID requester){
        switch(attack.delivery()){
            case INSTANT -> applyTargets(deployment.id(),attack,List.of(primary));
            case PROJECTILE -> {
                if(projectiles.size()>=config.maxProjectiles()||nextProjectile==Long.MAX_VALUE)throw new IllegalStateException("Projectile capacity exhausted");
                CombatAccess.Resolution resolution=combat.resolve(attack,enemies.combatTarget(primary.id()),entropy(deployment.id(),primary.id(),attacks));long id=nextProjectile++;
                projectiles.put(id,new Projectile(id,Math.addExact(elapsed,profile.projectileDelayTicks()),deployment.id(),primary.id(),resolution));
            }
            case CHAIN -> {
                List<TargetingAccess.Target> targets=targeting.selectMany(requester,new TargetingAccess.Query(new Vec3(deployment.position().x(),deployment.position().y(),deployment.position().z()),attack.range(),stateMode(deployment.id()),profile.filter()),attack.chainCap());
                applyTargets(deployment.id(),attack,targets);
            }
            case AOE -> {
                UUID areaRequester=UUID.nameUUIDFromBytes(("svarcade/aoe/"+session.id()+"/"+deployment.id()+"/"+attacks).getBytes(StandardCharsets.UTF_8));
                List<TargetingAccess.Target> targets=targeting.selectMany(areaRequester,new TargetingAccess.Query(primary.position(),profile.aoeRadius(),TargetingAccess.Mode.CLOSEST,profile.filter()),attack.aoeCap());
                applyTargets(deployment.id(),attack,targets);
            }
        }
    }
    private TargetingAccess.Mode stateMode(long deployment){State state=states.get(deployment);if(state==null)throw new IllegalStateException("Tower state missing");return state.mode;}
    private void applyTargets(long deployment,CombatAccess.Attack attack,List<TargetingAccess.Target> targets){
        List<StateChange> changes=new ArrayList<>();int index=0;for(TargetingAccess.Target target:targets){if(enemies.enemy(target.id()).isEmpty())continue;CombatAccess.Resolution resolution=combat.resolve(attack,enemies.combatTarget(target.id()),entropy(deployment,target.id(),attacks+index++));changes.add(enemies.prepareHit(target.id(),resolution));}
        if(changes.isEmpty())return;if(changes.size()==1)changes.getFirst().apply();else new CompositeChange(thread,changes).apply();
    }
    private boolean impactProjectiles(){
        int budget=config.maxProjectileImpactsPerTick();boolean changed=false;Iterator<Projectile> iterator=projectiles.values().iterator();List<Long>remove=new ArrayList<>();
        while(iterator.hasNext()&&budget>0){Projectile projectile=iterator.next();if(projectile.due()>elapsed)continue;budget--;if(enemies.enemy(projectile.target()).isPresent())enemies.prepareHit(projectile.target(),projectile.resolution()).apply();remove.add(projectile.id());changed=true;}
        remove.forEach(projectiles::remove);return changed;
    }

    private boolean sync(){Set<Long>expected=new TreeSet<>();for(Participant participant:session.participants().values())if(participant.kind()!=Participant.Kind.SPECTATOR)for(DeployableAccess.Deployment deployment:deployables.owned(participant.id()))if(config.profiles().containsKey(deployment.profile()))expected.add(deployment.id());boolean changed=states.keySet().removeIf(id->!expected.contains(id));for(long id:expected)if(!states.containsKey(id)){DeployableAccess.Deployment deployment=deployables.deployment(id).orElseThrow();Profile profile=config.profiles().get(deployment.profile());states.put(id,new State(id,profile.defaultMode(),elapsed,null));changed=true;}return changed;}
    private CombatAccess.Attack effective(CombatAccess.Attack base,Map<Id,Double>modifiers){double damageAdd=0,damageMultiplier=0,rangeAdd=0,rangeMultiplier=0,cooldownAdd=0,cooldownMultiplier=0,critChanceAdd=0,critMultiplierAdd=0;for(var entry:modifiers.entrySet()){Effect effect=config.modifierBindings().get(entry.getKey());if(effect==null)continue;double value=entry.getValue();switch(effect){case DAMAGE_ADD->damageAdd+=value;case DAMAGE_MULTIPLIER_DELTA->damageMultiplier+=value;case RANGE_ADD->rangeAdd+=value;case RANGE_MULTIPLIER_DELTA->rangeMultiplier+=value;case COOLDOWN_ADD_TICKS->cooldownAdd+=value;case COOLDOWN_MULTIPLIER_DELTA->cooldownMultiplier+=value;case CRIT_CHANCE_ADD->critChanceAdd+=value;case CRIT_MULTIPLIER_ADD->critMultiplierAdd+=value;}}double damage=bounded((base.damage()+damageAdd)*Math.max(0,1+damageMultiplier),0,1_000_000_000),range=bounded((base.range()+rangeAdd)*Math.max(0,1+rangeMultiplier),0,1_000_000),cooldownValue=(base.cooldownTicks()+cooldownAdd)*Math.max(0.000001,1+cooldownMultiplier);long cooldown=Math.max(1,Math.min(1_000_000,Math.round(cooldownValue)));double critChance=bounded(base.critChance()+critChanceAdd,0,1),critMultiplier=bounded(base.critMultiplier()+critMultiplierAdd,1,100);return new CombatAccess.Attack(base.damageType(),damage,range,cooldown,critChance,critMultiplier,base.delivery(),base.aoeCap(),base.chainCap(),base.statuses());}
    private UUID requester(long deployment){return UUID.nameUUIDFromBytes(("svarcade/tower/"+session.id()+"/"+deployment).getBytes(StandardCharsets.UTF_8));}
    private static long entropy(long deployment,UUID target,long attacks){return deployment*0x9E3779B97F4A7C15L^target.getMostSignificantBits()^Long.rotateLeft(target.getLeastSignificantBits(),17)^attacks;}
    private static double bounded(double value,double min,double max){if(!Double.isFinite(value))throw new IllegalStateException("Non-finite tower stat");return Math.max(min,Math.min(max,value));}

    private static Map<String,Object> encodeResolution(CombatAccess.Resolution resolution){List<Object>statuses=new ArrayList<>();for(CombatAccess.AppliedStatus s:resolution.statuses())statuses.add(Map.of("id",s.status().toString(),"kind",s.kind().name(),"duration",s.durationTicks(),"interval",s.tickInterval(),"magnitude",s.magnitude(),"stacking",s.stacking().name(),"stacks",s.stacks()));return Map.of("damage",resolution.damage(),"critical",resolution.critical(),"delivery",resolution.delivery().name(),"aoe_cap",resolution.aoeCap(),"chain_cap",resolution.chainCap(),"statuses",statuses);}
    private static CombatAccess.Resolution decodeResolution(Node n){n.only("damage","critical","delivery","aoe_cap","chain_cap","statuses");List<CombatAccess.AppliedStatus>statuses=new ArrayList<>();for(Node s:n.nodes("statuses")){s.only("id","kind","duration","interval","magnitude","stacking","stacks");try{statuses.add(new CombatAccess.AppliedStatus(Id.of(s.string("id")),CombatAccess.StatusKind.valueOf(s.string("kind")),s.integer("duration",1,100_000_000),s.integer("interval",1,1_000_000),Numbers.decimal(s,"magnitude",-1_000_000,1_000_000),CombatAccess.Stacking.valueOf(s.string("stacking")),(int)s.integer("stacks",1,1024)));}catch(IllegalArgumentException e){throw new ConfigException("Invalid restored projectile status",e);}}try{return new CombatAccess.Resolution(Numbers.decimal(n,"damage",0,1_000_000_000),n.bool("critical",false),CombatAccess.Delivery.valueOf(n.string("delivery")),(int)n.integer("aoe_cap",0,100_000),(int)n.integer("chain_cap",0,100_000),statuses);}catch(IllegalArgumentException e){throw new ConfigException("Invalid restored projectile resolution",e);}}

    @Override public int stateSchema(){return 2;}
    @Override public Map<String,Object> snapshot(){requireActive();List<Object>rows=new ArrayList<>();for(State state:states.values()){Map<String,Object>row=new LinkedHashMap<>();row.put("deployment",state.deployment);row.put("mode",state.mode.name());row.put("remaining",Math.max(0,state.nextDue-elapsed));if(state.lastTarget!=null)row.put("last_target",state.lastTarget.toString());rows.add(row);}List<Object>shots=new ArrayList<>();for(Projectile projectile:projectiles.values())shots.add(Map.of("id",projectile.id(),"remaining",Math.max(0,projectile.due()-elapsed),"deployment",projectile.deployment(),"target",projectile.target().toString(),"resolution",encodeResolution(projectile.resolution())));return Values.map(Map.of("revision",revision,"attacks",attacks,"misses",misses,"next_projectile",nextProjectile,"towers",rows,"projectiles",shots));}
    @Override public void restore(int schema,Map<String,Object>saved){
        thread.check();if(active||closed||(schema!=1&&schema!=2))throw new IllegalArgumentException("Invalid tower restore");Node n=new Node(saved,"tower-state");if(schema==1)n.only("revision","attacks","misses","towers");else n.only("revision","attacks","misses","next_projectile","towers","projectiles");long restoredRevision=n.integer("revision",0,Long.MAX_VALUE-1),restoredAttacks=n.integer("attacks",0,Long.MAX_VALUE),restoredMisses=n.integer("misses",0,Long.MAX_VALUE);NavigableMap<Long,State>restored=new TreeMap<>();
        for(Node row:n.nodes("towers")){row.only("deployment","mode","remaining","last_target");long deploymentId=row.integer("deployment",1,Long.MAX_VALUE-1);DeployableAccess.Deployment deployment=deployables.deployment(deploymentId).orElseThrow(()->new ConfigException("Restored tower deployment missing"));Profile profile=config.profiles().get(deployment.profile());if(profile==null)throw new ConfigException("Restored deployment is not a tower profile");TargetingAccess.Mode mode;try{mode=TargetingAccess.Mode.valueOf(row.string("mode"));}catch(IllegalArgumentException e){throw new ConfigException("Unknown restored target mode",e);}if(!profile.modes().contains(mode))throw new ConfigException("Restored target mode unavailable");long remaining=row.integer("remaining",0,1_000_000);UUID lastTarget=row.has("last_target")?UUID.fromString(row.string("last_target")):null;if(restored.putIfAbsent(deploymentId,new State(deploymentId,mode,remaining,lastTarget))!=null)throw new ConfigException("Duplicate restored tower state");}
        NavigableMap<Long,Projectile>restoredProjectiles=new TreeMap<>();long restoredNext=1;if(schema==2){restoredNext=n.integer("next_projectile",1,Long.MAX_VALUE);if(n.list("projectiles").size()>config.maxProjectiles())throw new ConfigException("Restored projectile capacity");for(Node row:n.nodes("projectiles")){row.only("id","remaining","deployment","target","resolution");long id=row.integer("id",1,Long.MAX_VALUE-1),remaining=row.integer("remaining",0,1_000_000),deployment=row.integer("deployment",1,Long.MAX_VALUE-1);if(id>=restoredNext||!restored.containsKey(deployment))throw new ConfigException("Invalid restored projectile owner");UUID target=UUID.fromString(row.string("target"));Projectile projectile=new Projectile(id,remaining,deployment,target,decodeResolution(row.node("resolution")));if(restoredProjectiles.putIfAbsent(id,projectile)!=null)throw new ConfigException("Duplicate restored projectile");}}
        states.clear();states.putAll(restored);projectiles.clear();projectiles.putAll(restoredProjectiles);elapsed=0;lastTick=-1;revision=restoredRevision;attacks=restoredAttacks;misses=restoredMisses;nextProjectile=restoredNext;active=true;Set<Long>before=Set.copyOf(states.keySet());boolean changed=sync();if(changed||!states.keySet().equals(before))throw new ConfigException("Restored tower set mismatch");
    }
    @Override public void close(){thread.check();states.clear();projectiles.clear();active=false;closed=true;}
}
