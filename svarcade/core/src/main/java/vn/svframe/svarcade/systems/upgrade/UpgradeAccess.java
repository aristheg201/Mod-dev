package vn.svframe.svarcade.systems.upgrade;

import java.util.*;
import vn.svframe.svarcade.config.Id;
import vn.svframe.svarcade.runtime.*;

/** Match-only upgrades keyed by generic deployment IDs. */
public interface UpgradeAccess {
    SessionServices.Key<UpgradeAccess> ACCESS = new SessionServices.Key<>(Id.of("svarcade:upgrades"), UpgradeAccess.class);
    record Requirement(Id upgrade, int level) { }
    record Definition(Id id, int maxLevel, List<Long> costs, Set<Id> profiles, Set<Id> requiredTags,
                      Set<Id> forbiddenTags, List<Requirement> prerequisites, Map<Id, Double> modifiers) {
        public Definition {
            costs = List.copyOf(costs); profiles = Set.copyOf(profiles); requiredTags = Set.copyOf(requiredTags);
            forbiddenTags = Set.copyOf(forbiddenTags); prerequisites = List.copyOf(prerequisites); modifiers = Map.copyOf(modifiers);
        }
    }
    int level(long deployment, Id upgrade);
    Map<Id, Integer> levels(long deployment);
    Map<Id, Double> modifiers(long deployment);
    Map<Id, Definition> definitions();
    StateChange preparePurchase(UUID actor, long deployment, Id upgrade);
    long revision();
}
