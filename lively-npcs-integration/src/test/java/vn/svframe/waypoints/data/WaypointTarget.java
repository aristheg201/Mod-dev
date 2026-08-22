package vn.svframe.waypoints.data;

import java.util.UUID;

public record WaypointTarget(
        String id,
        String displayName,
        String locationName,
        String world,
        double x,
        double y,
        double z,
        UUID dynamicEntityUuid,
        UUID expectedNpcUuid,
        boolean serverWaypoint
) {}
