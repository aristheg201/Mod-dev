package vn.svframe.svarcade.systems.turn;

import vn.svframe.svarcade.runtime.StateChange;

public interface TurnAccess {
    String team();
    boolean running();
    boolean expired();
    long remainingNanos(String team);
    default StateChange preparePass(String actingTeam, String nextTeam) { return preparePass(actingTeam, nextTeam, true); }
    StateChange preparePass(String actingTeam, String nextTeam, boolean keepRunning);
    default StateChange prepareRunning(boolean running) { return prepareRunning(running, false); }
    StateChange prepareRunning(boolean running, boolean requireTimeRemaining);
}
