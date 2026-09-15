package vn.svframe.svrelationships.gameplay;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PartnerCapacityResolverTest {
    @Test
    void takesHighestMatchingCapacity() {
        PartnerCapacityDefinition definition = new PartnerCapacityDefinition(1, "grandfather_existing", List.of(
                new PartnerCapacityDefinition.Rule("rank.vip", 2),
                new PartnerCapacityDefinition.Rule("rank.elite", 4),
                new PartnerCapacityDefinition.Rule("rank.legend", 6)
        ));
        Set<String> permissions = Set.of("rank.vip", "rank.elite");
        assertEquals(4, new PartnerCapacityResolver().resolve(definition, permissions::contains));
        assertEquals(1, new PartnerCapacityResolver().resolve(definition, ignored -> false));
    }
}
