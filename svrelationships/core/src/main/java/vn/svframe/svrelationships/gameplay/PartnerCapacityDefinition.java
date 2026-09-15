package vn.svframe.svrelationships.gameplay;

import java.util.List;
import java.util.Objects;

public record PartnerCapacityDefinition(
        int fallback,
        String downgradePolicy,
        List<Rule> rules
) {
    public PartnerCapacityDefinition {
        Objects.requireNonNull(downgradePolicy, "downgradePolicy");
        rules = List.copyOf(rules);
        if (fallback < 0) {
            throw new IllegalArgumentException("fallback capacity cannot be negative");
        }
    }

    public record Rule(String permission, int capacity) {
        public Rule {
            Objects.requireNonNull(permission, "permission");
            if (capacity < 0) {
                throw new IllegalArgumentException("capacity cannot be negative");
            }
        }
    }
}
