package vn.svframe.svarcade.systems.presence;

import java.util.*;
import vn.svframe.svarcade.config.Id;
import vn.svframe.svarcade.runtime.SessionServices;

public interface PresenceAccess {
    SessionServices.Key<PresenceAccess> ACCESS = new SessionServices.Key<>(Id.of("svarcade:presence"), PresenceAccess.class);
    enum TimeoutPolicy { FORFEIT, RETAIN, BOT_TAKEOVER, CLEANUP }
    record Status(UUID participant, boolean connected, long disconnectedAt, long deadline, TimeoutPolicy policy) { }
    record Event(UUID participant, TimeoutPolicy policy) { }
    void disconnect(UUID participant, long tick);
    void reconnect(UUID participant, long tick);
    Status status(UUID participant);
    List<Event> drainTimeouts(int maximum);
    long revision();
}
