package vn.svframe.svrelationships.gameplay;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record RouteDefinition(
        String id,
        String initialState,
        Map<String, State> states
) {
    public RouteDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(initialState, "initialState");
        states = Map.copyOf(states);
        if (!states.containsKey(initialState)) {
            throw new IllegalArgumentException("route initial state does not exist: " + initialState);
        }
    }

    public record State(
            String id,
            String displayKey,
            List<String> transitions
    ) {
        public State {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(displayKey, "displayKey");
            transitions = List.copyOf(transitions);
        }
    }
}
