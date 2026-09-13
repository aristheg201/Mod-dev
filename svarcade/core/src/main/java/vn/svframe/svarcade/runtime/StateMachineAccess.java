package vn.svframe.svarcade.runtime;

import java.util.Map;
import vn.svframe.svarcade.config.Id;

/** Authoritative data-defined session lifecycle capability. */
public interface StateMachineAccess {
    SessionServices.Key<StateMachineAccess> ACCESS = new SessionServices.Key<>(Id.of("svarcade:state_machine"), StateMachineAccess.class);
    boolean event(String event);
    String state();
    Map<String, Object> data();
    boolean terminal();
}
