package vn.svframe.svarcade.systems.enemy;

import java.util.*;
import vn.svframe.svarcade.config.Id;
import vn.svframe.svarcade.runtime.SessionServices;
import vn.svframe.svarcade.runtime.StateChange;
import vn.svframe.svarcade.systems.combat.CombatAccess;

/** Authoritative runtime enemies composed from authored profiles and wave spawns. */
public interface EnemyAccess {
    SessionServices.Key<EnemyAccess> ACCESS = new SessionServices.Key<>(Id.of("svarcade:enemies"), EnemyAccess.class);

    enum Cause { DEATH, GOAL }

    record View(UUID id, long sequence, Id profile, Id lane, double health, double maxHealth,
                double speed, double armor, double strength, double progress, Set<Id> tags, Set<Id> statuses) {
        public View { tags = Set.copyOf(tags); statuses = Set.copyOf(statuses); }
    }

    record Outcome(UUID id, long sequence, Id profile, Cause cause, Map<String, Object> reward) {
        public Outcome { reward = Map.copyOf(reward); }
    }

    Optional<View> enemy(UUID id);
    List<View> enemies(int maximum);
    CombatAccess.Target combatTarget(UUID id);
    StateChange prepareHit(UUID id, CombatAccess.Resolution resolution);
    List<Outcome> drainOutcomes(int maximum);
    int size();
    long revision();
}
