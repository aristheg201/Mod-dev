package vn.svframe.svarcade.systems.wave;

import java.util.*;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.runtime.*;

/** Deterministic authored waves. Spawn/clear delivery is acknowledged transactionally by composing systems. */
public interface WaveAccess {
    SessionServices.Key<WaveAccess> ACCESS = new SessionServices.Key<>(Id.of("svarcade:waves"), WaveAccess.class);
    record Group(Id enemy, int count, long intervalTicks, Id lane, Set<Id> modifiers, Set<Id> tags) {
        public Group { modifiers = Set.copyOf(modifiers); tags = Set.copyOf(tags); }
    }
    record Wave(Id id, List<Group> groups, Map<String, Object> clearReward, Map<String, Object> shop) {
        public Wave { groups = List.copyOf(groups); clearReward = Values.map(clearReward); shop = Values.map(shop); }
    }
    record Spawn(long sequence, int waveIndex, Id wave, int groupIndex, Id enemy, Id lane, Set<Id> modifiers, Set<Id> tags) {
        public Spawn { modifiers = Set.copyOf(modifiers); tags = Set.copyOf(tags); }
    }
    record Clear(int waveIndex, Id wave, Map<String, Object> reward, Map<String, Object> shop) {
        public Clear { reward = Values.map(reward); shop = Values.map(shop); }
    }
    List<Wave> waves();
    int nextWaveIndex();
    OptionalInt activeWaveIndex();
    boolean complete();
    StateChange prepareStartNext();
    List<Spawn> pendingSpawns(int maximum);
    StateChange prepareSpawned(long sequence);
    List<Spawn> outstandingSpawns();
    StateChange prepareResolved(long sequence);
    Optional<Clear> clearEvent();
    StateChange prepareClearAcknowledged();
    long revision();
}
