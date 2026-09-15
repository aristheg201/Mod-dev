package vn.svframe.svrelationships.gameplay;

import vn.svframe.svrelationships.relationship.RelationshipState;

import java.util.Map;
import java.util.Objects;

public final class RouteEngine {
    private final Map<String, RouteDefinition> routes;

    public RouteEngine(Map<String, RouteDefinition> routes) {
        this.routes = Map.copyOf(routes);
    }

    public String currentState(RelationshipState relationship, String routeId) {
        RouteDefinition route = requireRoute(routeId);
        String current = relationship.routeState(routeId);
        if (current == null) {
            current = route.initialState();
            relationship.setRouteState(routeId, current);
        }
        return current;
    }

    public boolean canTransition(RelationshipState relationship, String routeId, String targetState) {
        RouteDefinition route = requireRoute(routeId);
        String currentId = currentState(relationship, routeId);
        RouteDefinition.State current = route.states().get(currentId);
        return current != null && route.states().containsKey(targetState) && current.transitions().contains(targetState);
    }

    public String transition(RelationshipState relationship, String routeId, String targetState) {
        Objects.requireNonNull(relationship, "relationship");
        if (!canTransition(relationship, routeId, targetState)) {
            throw new IllegalStateException("Invalid transition for route " + routeId + ": " + currentState(relationship, routeId) + " -> " + targetState);
        }
        relationship.setRouteState(routeId, targetState);
        return targetState;
    }

    public void force(RelationshipState relationship, String routeId, String stateId) {
        RouteDefinition route = requireRoute(routeId);
        if (!route.states().containsKey(stateId)) {
            throw new IllegalArgumentException("Unknown state " + stateId + " for route " + routeId);
        }
        relationship.setRouteState(routeId, stateId);
    }

    public RouteDefinition requireRoute(String routeId) {
        RouteDefinition route = routes.get(routeId);
        if (route == null) {
            throw new IllegalArgumentException("Unknown route: " + routeId);
        }
        return route;
    }
}
