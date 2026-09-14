package vn.svframe.svarcade.systems.interaction;

import java.util.*;
import vn.svframe.svarcade.config.Id;
import vn.svframe.svarcade.runtime.*;

public interface InteractionAccess {
    SessionServices.Key<InteractionAccess> ACCESS = new SessionServices.Key<>(Id.of("svarcade:interactions"), InteractionAccess.class);
    record Point(double x, double y, double z) { public Point { if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) throw new IllegalArgumentException("Point"); } }
    record Binding(UUID object, Id action, UUID owner, Point position, double range, Set<Id> tags, long version) {
        public Binding { Objects.requireNonNull(object); Objects.requireNonNull(action); Objects.requireNonNull(position); tags = Set.copyOf(tags); if (!Double.isFinite(range) || range < 0) throw new IllegalArgumentException("Range"); }
    }
    StateChange prepareBind(UUID object, Id action, UUID owner, Point position, double range, Set<Id> tags);
    StateChange prepareRemove(UUID object);
    Optional<Binding> binding(UUID object);
    List<Binding> bindings(int maximum);
    long revision();
}
