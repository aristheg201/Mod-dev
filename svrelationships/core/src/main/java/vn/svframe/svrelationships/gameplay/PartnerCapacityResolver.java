package vn.svframe.svrelationships.gameplay;

import java.util.Objects;
import java.util.function.Predicate;

public final class PartnerCapacityResolver {
    public int resolve(PartnerCapacityDefinition definition, Predicate<String> permissionCheck) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(permissionCheck, "permissionCheck");
        int result = definition.fallback();
        for (PartnerCapacityDefinition.Rule rule : definition.rules()) {
            if (permissionCheck.test(rule.permission())) {
                result = Math.max(result, rule.capacity());
            }
        }
        return result;
    }
}
