package vn.svframe.svarcade.systems.path;

import java.util.*;
import vn.svframe.svarcade.config.Id;

public interface PathAccess {
    record Agent(UUID id, Id path, double distance, double speed) { }
    void spawn(UUID id, Id path, double speed);
    void speed(UUID id, double speed);
    boolean remove(UUID id);
    Optional<Agent> agent(UUID id);
    Vec3 position(UUID id);
    double progress(UUID id);
    List<UUID> drainArrivals(int maximum);
    int size();
}
