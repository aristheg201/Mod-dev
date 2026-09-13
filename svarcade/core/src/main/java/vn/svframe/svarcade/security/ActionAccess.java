package vn.svframe.svarcade.security;

import java.util.*;
import vn.svframe.svarcade.bot.BotRuntime;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.runtime.*;

/** Session-local authoritative action service exposed as a typed capability. */
public interface ActionAccess {
    UUID issueController(UUID actor, long expires);
    void revokeController(UUID actor);
    IntentGate.Result dispatchHuman(IntentGate.Facts facts, IntentGate.Intent intent);
    IntentGate.Result dispatchBot(IntentGate.Facts facts, UUID intendedSession, long revision, BotRuntime.Decision decision);
    List<ActionDispatcher.Event> drainEvents(int maximum);
    Set<Id> actions();
    Map<String, Integer> ownedCounts();
}
