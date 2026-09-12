package vn.svframe.svarcade.systems.turn;

import vn.svframe.svarcade.runtime.StateChange;

public interface TurnAccess {
    String team();
    boolean running();
    boolean expired();
    long remainingNanos(String team);
    StateChange preparePass(String actingTeam, String nextTeam);
    StateChange prepareRunning(boolean running);
}
