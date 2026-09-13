package vn.svframe.svarcade.systems;

import java.util.*;
import java.util.function.*;
import vn.svframe.svarcade.bot.*;
import vn.svframe.svarcade.runtime.*;
import vn.svframe.svarcade.security.*;
import vn.svframe.svarcade.systems.board.*;

/** Default implemented generic-system catalog. Platform facts remain injected server authority. */
public final class StandardRuntimeCatalog {
    private StandardRuntimeCatalog() { }

    public static SystemCatalog create(BotRuntime bots,
                                       Function<GenericSession, BiFunction<UUID, Long, IntentGate.Facts>> facts) {
        Objects.requireNonNull(bots); Objects.requireNonNull(facts);
        return CoreSystems.builder(BoardActionFactories.create(), ValuePrimitives.actions(), ValuePrimitives.conditions())
                .add(BoardDecisionSource.ID, new BoardDecisionSource.Plan(BoardBotStrategy.builtins(), facts))
                .add(BotSystem.ID, new BotSystem.Plan(bots))
                .build();
    }
}
