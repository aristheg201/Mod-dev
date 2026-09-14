package vn.svframe.svarcade.runtime;

import java.util.*;
import vn.svframe.svarcade.config.*;

/** Explicit versioned player state owned by SVArcade; never serializes platform object graphs. */
public record PlayerStateSnapshot(int schema, EnumSet<Field> fields, Position position, Mode mode,
                                  List<Slot> inventory, int selectedSlot, List<Effect> effects) {
    public enum Field { POSITION, MODE, INVENTORY, EFFECTS }
    public record Position(String world, double x, double y, double z, float yaw, float pitch) {
        public Position { Objects.requireNonNull(world); if (world.isBlank() || world.length() > 256) throw new IllegalArgumentException("World id"); }
    }
    public record Mode(String gameMode, boolean allowFlight, boolean flying, float walkSpeed, float flySpeed) {
        public Mode { Objects.requireNonNull(gameMode); if (gameMode.isBlank() || gameMode.length() > 64) throw new IllegalArgumentException("Game mode"); }
    }
    public record Slot(int slot, String item, int count, String components) {
        public Slot {
            Objects.requireNonNull(item); Objects.requireNonNull(components);
            if (slot < 0 || slot > 1024 || item.isBlank() || item.length() > 256 || count < 1 || count > 9999 || components.length() > 131072)
                throw new IllegalArgumentException("Inventory slot");
        }
    }
    public record Effect(String id, int amplifier, long remainingTicks, boolean ambient, boolean particles, boolean icon) {
        public Effect { Objects.requireNonNull(id); if (id.isBlank() || id.length() > 256 || amplifier < 0 || amplifier > 255 || remainingTicks < 0) throw new IllegalArgumentException("Effect"); }
    }

    public PlayerStateSnapshot {
        Objects.requireNonNull(fields); Objects.requireNonNull(inventory); Objects.requireNonNull(effects);
        if (schema != 1) throw new IllegalArgumentException("Player state schema"); fields = fields.clone(); inventory = List.copyOf(inventory); effects = List.copyOf(effects);
        if (fields.contains(Field.POSITION) != (position != null) || fields.contains(Field.MODE) != (mode != null))
            throw new IllegalArgumentException("Player state field mismatch");
        if (fields.contains(Field.INVENTORY)) {
            if (selectedSlot < 0 || selectedSlot > 1024) throw new IllegalArgumentException("Selected slot");
            Set<Integer> unique = new HashSet<>(); for (Slot slot : inventory) if (!unique.add(slot.slot())) throw new IllegalArgumentException("Duplicate inventory slot");
        } else if (!inventory.isEmpty() || selectedSlot >= 0) throw new IllegalArgumentException("Unexpected inventory state");
        if (!fields.contains(Field.EFFECTS) && !effects.isEmpty()) throw new IllegalArgumentException("Unexpected effect state");
    }

    public Map<String,Object> toMap() {
        Map<String,Object> out = new LinkedHashMap<>(); out.put("schema", schema); out.put("fields", fields.stream().map(Enum::name).toList());
        if (position != null) out.put("position", Map.of("world", position.world(), "x", position.x(), "y", position.y(), "z", position.z(), "yaw", position.yaw(), "pitch", position.pitch()));
        if (mode != null) out.put("mode", Map.of("game_mode", mode.gameMode(), "allow_flight", mode.allowFlight(), "flying", mode.flying(), "walk_speed", mode.walkSpeed(), "fly_speed", mode.flySpeed()));
        if (fields.contains(Field.INVENTORY)) {
            out.put("selected_slot", selectedSlot); out.put("inventory", inventory.stream().map(s -> Map.of("slot", s.slot(), "item", s.item(), "count", s.count(), "components", s.components())).toList());
        }
        if (fields.contains(Field.EFFECTS)) out.put("effects", effects.stream().map(e -> Map.of("id", e.id(), "amplifier", e.amplifier(), "remaining_ticks", e.remainingTicks(), "ambient", e.ambient(), "particles", e.particles(), "icon", e.icon())).toList());
        return Values.map(out);
    }
}
