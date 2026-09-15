package vn.svframe.svrelationships.gameplay;

import java.util.Map;

public record PartnershipDefinition(
        String routeId,
        Map<String, Milestone> milestones
) {
    public PartnershipDefinition {
        milestones = Map.copyOf(milestones);
    }

    public record Milestone(
            String id,
            String targetState,
            String selectorId,
            Map<String, String> requiredRanks,
            boolean requireHousehold,
            boolean createsPartnership,
            String messageKey
    ) {
        public Milestone {
            requiredRanks = Map.copyOf(requiredRanks);
        }
    }
}
