package vn.svframe.svarcade.systems.combat;

import java.util.*;
import vn.svframe.svarcade.config.Id;
import vn.svframe.svarcade.runtime.SessionServices;

/** Pure authoritative combat math. Actor ownership/state stays in composing systems. */
public interface CombatAccess {
    SessionServices.Key<CombatAccess> ACCESS = new SessionServices.Key<>(Id.of("svarcade:combat"), CombatAccess.class);
    enum Delivery { INSTANT, PROJECTILE, AOE, CHAIN }
    enum StatusKind { DOT, SLOW, STUN, BUFF, DEBUFF }
    enum Stacking { REFRESH, STACK, REPLACE, IGNORE }
    record StatusDefinition(StatusKind kind, long baseDurationTicks, long tickInterval, double magnitude,
                            Stacking stacking, int maxStacks, Set<Id> immunityTags) {
        public StatusDefinition { immunityTags = Set.copyOf(immunityTags); }
    }
    record StatusAttempt(Id status, double chance, double durationMultiplier, int stacks) { }
    record Attack(Id damageType, double damage, double range, long cooldownTicks, double critChance, double critMultiplier,
                  Delivery delivery, int aoeCap, int chainCap, List<StatusAttempt> statuses) {
        public Attack { statuses = List.copyOf(statuses); }
    }
    record Target(double armor, Map<Id, Double> resistances, Set<Id> tags, Map<Id, Double> statusResistances) {
        public Target { resistances = Map.copyOf(resistances); tags = Set.copyOf(tags); statusResistances = Map.copyOf(statusResistances); }
    }
    record AppliedStatus(Id status, StatusKind kind, long durationTicks, long tickInterval, double magnitude,
                         Stacking stacking, int stacks) { }
    record Resolution(double damage, boolean critical, Delivery delivery, int aoeCap, int chainCap, List<AppliedStatus> statuses) {
        public Resolution { statuses = List.copyOf(statuses); }
    }
    Resolution resolve(Attack attack, Target target, long entropy);
    Map<Id, StatusDefinition> statusDefinitions();
}
