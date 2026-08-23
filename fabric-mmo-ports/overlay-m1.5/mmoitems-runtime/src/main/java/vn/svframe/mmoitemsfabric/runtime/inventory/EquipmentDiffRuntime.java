package vn.svframe.mmoitemsfabric.runtime.inventory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * MMOItems-compatible equipment diff ordering.
 * A replacement always produces UNEQUIP(old) before EQUIP(new).
 */
public final class EquipmentDiffRuntime<S> {
    private final Map<S, EquippedSnapshot> equipped = new HashMap<>();

    public List<Transition<S>> refresh(Map<S, EquippedSnapshot> observed) {
        Objects.requireNonNull(observed, "observed");
        List<Transition<S>> transitions = new ArrayList<>();

        for (Map.Entry<S, EquippedSnapshot> current : List.copyOf(equipped.entrySet())) {
            EquippedSnapshot next = observed.get(current.getKey());
            if (next == null || next.itemHash() != current.getValue().itemHash()) {
                transitions.add(new Transition<>(Kind.UNEQUIP, current.getKey(), current.getValue()));
                equipped.remove(current.getKey());
            }
        }

        for (Map.Entry<S, EquippedSnapshot> next : observed.entrySet()) {
            EquippedSnapshot current = equipped.get(next.getKey());
            if (current == null) {
                equipped.put(next.getKey(), next.getValue());
                transitions.add(new Transition<>(Kind.EQUIP, next.getKey(), next.getValue()));
            }
        }

        return List.copyOf(transitions);
    }

    public enum Kind { EQUIP, UNEQUIP }

    public record EquippedSnapshot(int itemHash, String identity) {
        public EquippedSnapshot {
            Objects.requireNonNull(identity, "identity");
        }
    }

    public record Transition<S>(Kind kind, S slot, EquippedSnapshot item) {}
}
