package vn.svframe.svarcade.systems;

import java.util.Map;
import vn.svframe.svarcade.config.Registry;
import vn.svframe.svarcade.runtime.SystemCatalog;
import vn.svframe.svarcade.security.*;
import vn.svframe.svarcade.systems.board.*;
import vn.svframe.svarcade.systems.currency.CurrencySystem;
import vn.svframe.svarcade.systems.objective.ObjectiveSystem;
import vn.svframe.svarcade.systems.path.PathSystem;
import vn.svframe.svarcade.systems.targeting.TargetingSystem;
import vn.svframe.svarcade.systems.turn.TurnSystem;

/** Canonical registry of production generic systems currently implemented by core. */
public final class CoreSystems {
    private CoreSystems() { }

    public static SystemCatalog create() { return create(new Registry<>(Map.of())); }

    public static SystemCatalog create(Registry<ActionHandlerFactory> actionHandlers) {
        return SystemCatalog.builder()
                .add(MovementSystem.ID, new MovementSystem.Plan())
                .add(TurnSystem.ID, new TurnSystem.Plan())
                .add(CurrencySystem.ID, new CurrencySystem.Plan())
                .add(ObjectiveSystem.ID, new ObjectiveSystem.Plan())
                .add(PathSystem.ID, new PathSystem.Plan())
                .add(TargetingSystem.ID, new TargetingSystem.Plan())
                .add(BoardSystem.ID, new BoardSystem.Plan())
                .add(BoardAdjudicationSystem.ID, new BoardAdjudicationSystem.Plan())
                .add(ActionSystem.ID, new ActionSystem.Plan(actionHandlers))
                .build();
    }
}
