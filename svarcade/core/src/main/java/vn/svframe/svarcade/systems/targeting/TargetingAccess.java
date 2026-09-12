package vn.svframe.svarcade.systems.targeting;

import java.util.*;
import vn.svframe.svarcade.systems.path.Vec3;

public interface TargetingAccess {
    enum Mode { FIRST, LAST, CLOSEST, FARTHEST, STRONGEST, WEAKEST, LOWEST_HP, HIGHEST_HP }
    record Target(UUID id, Vec3 position, double health, double strength, double progress, Set<String> tags) {
        public Target {
            Objects.requireNonNull(id); Objects.requireNonNull(position);
            if (!Double.isFinite(health) || health < 0 || !Double.isFinite(strength) || strength < 0
                    || !Double.isFinite(progress) || progress < 0 || progress > 1 || tags.size() > 128) throw new IllegalArgumentException("Invalid target properties");
            tags = Set.copyOf(tags);
            for (String tag : tags) if (tag.isBlank() || tag.length() > 160) throw new IllegalArgumentException("Invalid target tag");
        }
    }
    record Filter(Set<String> all, Set<String> any, Set<String> none) {
        public Filter {
            all = Set.copyOf(all); any = Set.copyOf(any); none = Set.copyOf(none);
            if (all.size() + any.size() + none.size() > 128) throw new IllegalArgumentException("Filter too large");
        }
        public static Filter unrestricted() { return new Filter(Set.of(), Set.of(), Set.of()); }
        public boolean accepts(Set<String> tags) {
            return tags.containsAll(all) && (any.isEmpty() || !Collections.disjoint(any, tags)) && Collections.disjoint(none, tags);
        }
    }
    record Query(Vec3 origin, double range, Mode mode, Filter filter) {
        public Query {
            Objects.requireNonNull(origin); Objects.requireNonNull(mode); Objects.requireNonNull(filter);
            if (!Double.isFinite(range) || range < 0) throw new IllegalArgumentException("Invalid target range");
        }
    }
    void upsert(Target target);
    boolean remove(UUID id);
    Optional<Target> select(UUID requester, Query query);
    int size();
    Map<String, Long> metrics();
}
