package vn.svframe.svarcade.systems.render;

import java.util.*;
import vn.svframe.svarcade.config.Id;
import vn.svframe.svarcade.runtime.*;

public interface RendererAccess {
    SessionServices.Key<RendererAccess> ACCESS = new SessionServices.Key<>(Id.of("svarcade:renderer"), RendererAccess.class);
    record Transform(double x, double y, double z, float yaw, float pitch) {
        public Transform { if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || !Float.isFinite(yaw) || !Float.isFinite(pitch)) throw new IllegalArgumentException("Transform"); }
    }
    record ObjectView(UUID id, Id profile, Transform transform, Map<String,Object> data, long version) { public ObjectView { data = Map.copyOf(data); } }
    enum EventType { UPSERT, REMOVE }
    record Event(EventType type, ObjectView object) { }
    StateChange prepareUpsert(UUID id, Id profile, Transform transform, Map<String,Object> data);
    StateChange prepareRemove(UUID id);
    Optional<ObjectView> object(UUID id);
    List<ObjectView> objects(int maximum);
    List<Event> drainEvents(int maximum);
    long revision();
}
