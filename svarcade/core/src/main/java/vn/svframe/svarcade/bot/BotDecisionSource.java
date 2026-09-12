package vn.svframe.svarcade.bot;

import java.util.UUID;
import vn.svframe.svarcade.config.Id;
import vn.svframe.svarcade.runtime.SessionServices;
import vn.svframe.svarcade.security.IntentGate;

/** Owner-thread capability: compile a profile once, snapshot visible state only when eligible. */
public interface BotDecisionSource {
    SessionServices.Key<BotDecisionSource> ACCESS = new SessionServices.Key<>(Id.of("svarcade:bot_decision_source"), BotDecisionSource.class);
    interface CompiledProfile {
        /** Return a detached worker computation capturing immutable data, never this live source. */
        BotRuntime.Strategy snapshot(UUID actor, long seed);
    }
    CompiledProfile compile(BotProfile profile);
    boolean eligible(UUID actor);
    IntentGate.Facts facts(UUID actor, long tick);
}
