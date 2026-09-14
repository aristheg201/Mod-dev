package vn.svframe.svarcade.systems.loadout;

import java.util.*;
import vn.svframe.svarcade.config.Id;
import vn.svframe.svarcade.runtime.SessionServices;
import vn.svframe.svarcade.runtime.StateChange;

public interface LoadoutAccess {
    SessionServices.Key<LoadoutAccess> ACCESS = new SessionServices.Key<>(Id.of("svarcade:loadouts"), LoadoutAccess.class);

    record Snapshot(String sourceId, Id species, String form, Set<Id> aspects, Set<Id> types, int level,
                    Set<Id> moves, Id ability, String heldItem) {
        public Snapshot {
            Objects.requireNonNull(sourceId);
            Objects.requireNonNull(species);
            form = form == null ? "" : form;
            aspects = Set.copyOf(aspects);
            types = Set.copyOf(types);
            moves = Set.copyOf(moves);
            heldItem = heldItem == null ? "" : heldItem;
        }
    }

    record Derived(String sourceId, double levelMultiplier, Set<Id> tags, Map<Id, Double> modifiers) {
        public Derived {
            tags = Set.copyOf(tags);
            modifiers = Map.copyOf(modifiers);
        }
    }

    StateChange prepareRegister(UUID owner, Snapshot snapshot);
    Optional<Snapshot> snapshot(String sourceId);
    Optional<Derived> derived(String sourceId);
    List<Snapshot> owned(UUID owner);
    long revision();
}
