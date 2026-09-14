package vn.svframe.svarcade.systems.spectator;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import vn.svframe.svarcade.config.Id;
import vn.svframe.svarcade.runtime.SessionServices;
import vn.svframe.svarcade.runtime.StateChange;

public interface SpectatorAccess {
    SessionServices.Key<SpectatorAccess> ACCESS = new SessionServices.Key<>(Id.of("svarcade:spectators"), SpectatorAccess.class);
    record View(UUID player, String team) { public View { Objects.requireNonNull(player); Objects.requireNonNull(team); } }
    enum EventType { JOIN, LEAVE }
    record Event(EventType type, UUID player) { public Event { Objects.requireNonNull(type); Objects.requireNonNull(player); } }
    boolean enabled();
    int capacity();
    int size();
    boolean contains(UUID player);
    List<View> spectators();
    StateChange prepareJoin(UUID player);
    StateChange prepareLeave(UUID player);
    List<Event> drainEvents(int maximum);
    long revision();
}
