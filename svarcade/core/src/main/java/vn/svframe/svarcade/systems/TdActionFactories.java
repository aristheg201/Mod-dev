package vn.svframe.svarcade.systems;

import java.util.*;
import java.util.function.BooleanSupplier;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.runtime.*;
import vn.svframe.svarcade.security.*;
import vn.svframe.svarcade.systems.deployable.*;
import vn.svframe.svarcade.systems.targeting.*;
import vn.svframe.svarcade.systems.tower.*;
import vn.svframe.svarcade.systems.upgrade.*;

/** Data-bound gameplay actions for deployable-based games; action IDs remain definition-authored. */
public final class TdActionFactories {
    public static final Id DEPLOY = Id.of("svarcade:deploy");
    public static final Id MOVE = Id.of("svarcade:move_deployable");
    public static final Id RECALL = Id.of("svarcade:recall");
    public static final Id SELL = Id.of("svarcade:sell");
    public static final Id UPGRADE = Id.of("svarcade:upgrade");
    public static final Id TARGET = Id.of("svarcade:target_mode");
    private TdActionFactories() { }

    public static Registry<ActionHandlerFactory> create() {
        return new Registry.Builder<ActionHandlerFactory>()
                .add(DEPLOY, new DeployFactory())
                .add(MOVE, new MoveFactory())
                .add(RECALL, new RecallFactory(false))
                .add(SELL, new RecallFactory(true))
                .add(UPGRADE, new UpgradeFactory())
                .add(TARGET, new TargetFactory())
                .build();
    }

    private record Common(Id effect, Set<String> states) {
        Common { states = Set.copyOf(states); }
        static Common parse(Node n) {
            n.only("effect", "allowed_states");
            Set<String> states = n.has("allowed_states") ? n.strings("allowed_states") : Set.of();
            for (String state : states) if (!state.matches("[A-Za-z0-9_-]{1,80}")) throw n.error("allowed_states", "Invalid state identifier");
            return new Common(Id.of(n.string("effect")), states);
        }
        Set<Id> dependencies(Id system) {
            Set<Id> result = new LinkedHashSet<>(); result.add(system); if (!states.isEmpty()) result.add(StateMachineSystem.ID); return Set.copyOf(result);
        }
        Set<SessionServices.Key<?>> requires(SessionServices.Key<?> access) {
            Set<SessionServices.Key<?>> result = new LinkedHashSet<>(); result.add(access); if (!states.isEmpty()) result.add(StateMachineAccess.ACCESS); return Set.copyOf(result);
        }
        BooleanSupplier phase(GenericSession session) {
            if (states.isEmpty()) return () -> true;
            StateMachineAccess fsm = session.services().require(StateMachineAccess.ACCESS); return () -> states.contains(fsm.state());
        }
    }

    @FunctionalInterface private interface Prepare { StateChange apply(UUID actor, Map<String,Object> payload); }
    @FunctionalInterface private interface Check { void apply(UUID actor, Map<String,Object> payload); }

    private static ActionDispatcher.Handler handler(GenericSession session, Common common, Check check, Prepare prepare) {
        BooleanSupplier phase = common.phase(session);
        return new ActionDispatcher.Handler() {
            @Override public Optional<String> reject(GenericSession ignored, IntentGate.Facts facts, IntentGate.Intent intent) {
                if (!phase.getAsBoolean()) return Optional.of("phase");
                try { check.apply(facts.actor(), intent.payload()); return Optional.empty(); }
                catch (IllegalArgumentException | IllegalStateException failure) { return Optional.of("illegal"); }
            }
            @Override public ActionDispatcher.Prepared prepare(GenericSession ignored, IntentGate.Facts facts, IntentGate.Intent intent) {
                StateChange change = prepare.apply(facts.actor(), intent.payload());
                return new ActionDispatcher.Prepared(change::apply, change::rollback,
                        List.of(new ActionDispatcher.Effect(common.effect(), intent.payload())));
            }
        };
    }

    private static long deployment(Map<String,Object> payload) { return node(payload).integer("deployment", 1, Long.MAX_VALUE - 1); }
    private static Node node(Map<String,Object> payload) { return new Node(payload, "action-payload"); }
    private static DeployableAccess.Point point(Node n) {
        return new DeployableAccess.Point(Numbers.decimal(n, "x", -30_000_000, 30_000_000), Numbers.decimal(n, "y", -30_000_000, 30_000_000), Numbers.decimal(n, "z", -30_000_000, 30_000_000));
    }

    private static final class DeployFactory implements ActionHandlerFactory {
        @Override public void validate(Node config) { Common.parse(config); }
        @Override public Set<Id> dependencies(Node config) { return Common.parse(config).dependencies(DeployableSystem.ID); }
        @Override public Set<SessionServices.Key<?>> requires(Node config) { return Common.parse(config).requires(DeployableAccess.ACCESS); }
        @Override public ActionDispatcher.Handler create(GenericSession session, Node config) {
            Common common = Common.parse(config); DeployableAccess access = session.services().require(DeployableAccess.ACCESS);
            Check check = (actor,payload) -> { Node n=node(payload); n.only("source","profile","x","y","z"); access.prepareDeploy(actor,n.string("source"),Id.of(n.string("profile")),point(n)); };
            Prepare prepare = (actor,payload) -> { Node n=node(payload); return access.prepareDeploy(actor,n.string("source"),Id.of(n.string("profile")),point(n)); };
            return handler(session,common,check,prepare);
        }
    }

    private static final class MoveFactory implements ActionHandlerFactory {
        @Override public void validate(Node config) { Common.parse(config); }
        @Override public Set<Id> dependencies(Node config) { return Common.parse(config).dependencies(DeployableSystem.ID); }
        @Override public Set<SessionServices.Key<?>> requires(Node config) { return Common.parse(config).requires(DeployableAccess.ACCESS); }
        @Override public ActionDispatcher.Handler create(GenericSession session, Node config) {
            Common common=Common.parse(config); DeployableAccess access=session.services().require(DeployableAccess.ACCESS);
            Check check=(actor,payload)->{Node n=node(payload);n.only("deployment","x","y","z");access.prepareMove(actor,n.integer("deployment",1,Long.MAX_VALUE-1),point(n));};
            Prepare prepare=(actor,payload)->{Node n=node(payload);return access.prepareMove(actor,n.integer("deployment",1,Long.MAX_VALUE-1),point(n));};
            return handler(session,common,check,prepare);
        }
    }

    private static final class RecallFactory implements ActionHandlerFactory {
        private final boolean sell;
        private RecallFactory(boolean sell) { this.sell=sell; }
        @Override public void validate(Node config) { Common.parse(config); }
        @Override public Set<Id> dependencies(Node config) { return Common.parse(config).dependencies(DeployableSystem.ID); }
        @Override public Set<SessionServices.Key<?>> requires(Node config) { return Common.parse(config).requires(DeployableAccess.ACCESS); }
        @Override public ActionDispatcher.Handler create(GenericSession session, Node config) {
            Common common=Common.parse(config); DeployableAccess access=session.services().require(DeployableAccess.ACCESS);
            Check check=(actor,payload)->{Node n=node(payload);n.only("deployment");access.prepareRecall(actor,n.integer("deployment",1,Long.MAX_VALUE-1));};
            Prepare prepare=(actor,payload)->{Node n=node(payload);return access.prepareRecall(actor,n.integer("deployment",1,Long.MAX_VALUE-1));};
            return handler(session,common,check,prepare);
        }
        @Override public String toString() { return sell ? "sell" : "recall"; }
    }

    private static final class UpgradeFactory implements ActionHandlerFactory {
        @Override public void validate(Node config) { Common.parse(config); }
        @Override public Set<Id> dependencies(Node config) { return Common.parse(config).dependencies(UpgradeSystem.ID); }
        @Override public Set<SessionServices.Key<?>> requires(Node config) { return Common.parse(config).requires(UpgradeAccess.ACCESS); }
        @Override public ActionDispatcher.Handler create(GenericSession session, Node config) {
            Common common=Common.parse(config); UpgradeAccess access=session.services().require(UpgradeAccess.ACCESS);
            Check check=(actor,payload)->{Node n=node(payload);n.only("deployment","upgrade");access.preparePurchase(actor,n.integer("deployment",1,Long.MAX_VALUE-1),Id.of(n.string("upgrade")));};
            Prepare prepare=(actor,payload)->{Node n=node(payload);return access.preparePurchase(actor,n.integer("deployment",1,Long.MAX_VALUE-1),Id.of(n.string("upgrade")));};
            return handler(session,common,check,prepare);
        }
    }

    private static final class TargetFactory implements ActionHandlerFactory {
        @Override public void validate(Node config) { Common.parse(config); }
        @Override public Set<Id> dependencies(Node config) { return Common.parse(config).dependencies(TowerSystem.ID); }
        @Override public Set<SessionServices.Key<?>> requires(Node config) { return Common.parse(config).requires(TowerAccess.ACCESS); }
        @Override public ActionDispatcher.Handler create(GenericSession session, Node config) {
            Common common=Common.parse(config); TowerAccess access=session.services().require(TowerAccess.ACCESS);
            Check check=(actor,payload)->{Node n=node(payload);n.only("deployment","mode");access.prepareTargetMode(actor,n.integer("deployment",1,Long.MAX_VALUE-1),mode(n));};
            Prepare prepare=(actor,payload)->{Node n=node(payload);return access.prepareTargetMode(actor,n.integer("deployment",1,Long.MAX_VALUE-1),mode(n));};
            return handler(session,common,check,prepare);
        }
        private static TargetingAccess.Mode mode(Node n) {
            try { return TargetingAccess.Mode.valueOf(n.string("mode")); }
            catch (IllegalArgumentException e) { throw n.error("mode","Unknown target mode"); }
        }
    }
}
