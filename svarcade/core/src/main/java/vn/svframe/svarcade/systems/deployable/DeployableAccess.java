package vn.svframe.svarcade.systems.deployable;

import java.util.*;
import vn.svframe.svarcade.config.Id;
import vn.svframe.svarcade.runtime.*;

/** Match-local deployable ownership; sourceId is the adapter-supplied canonical identity. */
public interface DeployableAccess {
    SessionServices.Key<DeployableAccess> ACCESS = new SessionServices.Key<>(Id.of("svarcade:deployables"), DeployableAccess.class);
    record Point(double x, double y, double z) {
        public Point { if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) throw new IllegalArgumentException("Non-finite deployable position"); }
    }
    record Profile(Id id, long deployCost, long moveCost, long recallRefund, int maxPerActor, Set<Id> tags) {
        public Profile { tags = Set.copyOf(tags); }
    }
    record Deployment(long id, UUID owner, String sourceId, Id profile, Point position, long version) { }
    Optional<Deployment> deployment(long id);
    Optional<Deployment> bySource(String sourceId);
    List<Deployment> owned(UUID actor);
    Map<Id, Profile> profiles();
    StateChange prepareDeploy(UUID actor, String sourceId, Id profile, Point position);
    StateChange prepareMove(UUID actor, long deployment, Point position);
    StateChange prepareRecall(UUID actor, long deployment);
    long revision();
}
