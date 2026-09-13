package vn.svframe.svarcade.systems;

import java.util.Map;
import vn.svframe.svarcade.bot.*;
import vn.svframe.svarcade.config.Registry;
import vn.svframe.svarcade.runtime.*;
import vn.svframe.svarcade.security.*;
import vn.svframe.svarcade.systems.board.*;
import vn.svframe.svarcade.systems.combat.CombatSystem;
import vn.svframe.svarcade.systems.currency.CurrencySystem;
import vn.svframe.svarcade.systems.deployable.DeployableSystem;
import vn.svframe.svarcade.systems.objective.ObjectiveSystem;
import vn.svframe.svarcade.systems.path.PathSystem;
import vn.svframe.svarcade.systems.shop.ShopSystem;
import vn.svframe.svarcade.systems.targeting.TargetingSystem;
import vn.svframe.svarcade.systems.turn.TurnSystem;
import vn.svframe.svarcade.systems.upgrade.UpgradeSystem;

/** Canonical registry of production generic systems currently implemented by core. */
public final class CoreSystems {
    private CoreSystems() { }

    public static SystemCatalog create() {
        return create(new Registry<>(Map.of()), new Registry<>(Map.of()), new Registry<>(Map.of()));
    }
    public static SystemCatalog create(Registry<ActionHandlerFactory> actionHandlers) {
        return create(actionHandlers, new Registry<>(Map.of()), new Registry<>(Map.of()));
    }
    public static SystemCatalog create(Registry<ActionHandlerFactory> actionHandlers,
                                       Registry<StateMachineRuntime.Action> stateActions,
                                       Registry<StateMachineRuntime.Condition> stateConditions) {
        return builder(actionHandlers, stateActions, stateConditions).build();
    }
    public static SystemCatalog create(Registry<ActionHandlerFactory> actionHandlers,
                                       Registry<StateMachineRuntime.Action> stateActions,
                                       Registry<StateMachineRuntime.Condition> stateConditions,
                                       BotRuntime botRuntime) {
        return builder(actionHandlers, stateActions, stateConditions).add(BotSystem.ID, new BotSystem.Plan(botRuntime)).build();
    }
    /** Extension point for platform-owned or optional-integration systems without duplicating core registration. */
    public static SystemCatalog.Builder builder(Registry<ActionHandlerFactory> actionHandlers,
                                                Registry<StateMachineRuntime.Action> stateActions,
                                                Registry<StateMachineRuntime.Condition> stateConditions) {
        return SystemCatalog.builder()
                .add(MovementSystem.ID, new MovementSystem.Plan())
                .add(TurnSystem.ID, new TurnSystem.Plan())
                .add(CurrencySystem.ID, new CurrencySystem.Plan())
                .add(ObjectiveSystem.ID, new ObjectiveSystem.Plan())
                .add(PathSystem.ID, new PathSystem.Plan())
                .add(TargetingSystem.ID, new TargetingSystem.Plan())
                .add(CombatSystem.ID, new CombatSystem.Plan())
                .add(DeployableSystem.ID, new DeployableSystem.Plan())
                .add(UpgradeSystem.ID, new UpgradeSystem.Plan())
                .add(ShopSystem.ID, new ShopSystem.Plan())
                .add(BoardSystem.ID, new BoardSystem.Plan())
                .add(BoardAdjudicationSystem.ID, new BoardAdjudicationSystem.Plan())
                .add(ActionSystem.ID, new ActionSystem.Plan(actionHandlers))
                .add(StateMachineSystem.ID, new StateMachineSystem.Plan(stateActions, stateConditions));
    }
}
