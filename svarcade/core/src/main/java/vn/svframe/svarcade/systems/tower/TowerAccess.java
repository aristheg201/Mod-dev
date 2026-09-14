package vn.svframe.svarcade.systems.tower;

import java.util.*;
import vn.svframe.svarcade.config.Id;
import vn.svframe.svarcade.runtime.SessionServices;
import vn.svframe.svarcade.runtime.StateChange;
import vn.svframe.svarcade.systems.targeting.TargetingAccess;

/** Runtime combat controls for generic deployables. */
public interface TowerAccess {
    SessionServices.Key<TowerAccess> ACCESS = new SessionServices.Key<>(Id.of("svarcade:towers"), TowerAccess.class);
    record View(long deployment, TargetingAccess.Mode mode, long nextAttackTick, Optional<UUID> lastTarget) {
        public View { Objects.requireNonNull(mode); lastTarget = Objects.requireNonNull(lastTarget); }
    }
    Optional<View> tower(long deployment);
    List<View> towers(UUID owner);
    StateChange prepareTargetMode(UUID actor, long deployment, TargetingAccess.Mode mode);
    long attacks();
    long misses();
    long revision();
}
