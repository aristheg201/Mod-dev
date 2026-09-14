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
        if (fields.contains(Field.POSITION) != (position != null) || fields.contains(Field.MODE) != (mode != null)) throw new IllegalArgumentException("Player state field mismatch");
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

    public static PlayerStateSnapshot fromMap(Map<String,Object> value) {
        Node root = new Node(value, "player-state"); root.only("schema", "fields", "position", "mode", "inventory", "selected_slot", "effects");
        int schema = (int)root.integer("schema", 1, 1); EnumSet<Field> fields = EnumSet.noneOf(Field.class);
        for (String raw : root.strings("fields")) {
            try { fields.add(Field.valueOf(raw)); } catch (IllegalArgumentException e) { throw root.error("fields", "Unknown player-state field: " + raw); }
        }
        Position position = null; Mode mode = null; List<Slot> inventory = List.of(); int selectedSlot = -1; List<Effect> effects = List.of();
        if (fields.contains(Field.POSITION)) {
            Node n = root.node("position"); n.only("world", "x", "y", "z", "yaw", "pitch");
            position = new Position(n.string("world"), Numbers.decimal(n, "x", -30_000_000, 30_000_000), Numbers.decimal(n, "y", -30_000_000, 30_000_000),
                    Numbers.decimal(n, "z", -30_000_000, 30_000_000), (float)Numbers.decimal(n, "yaw", -360, 360), (float)Numbers.decimal(n, "pitch", -360, 360));
        } else if (root.has("position")) throw root.error("position", "Position present without POSITION field");
        if (fields.contains(Field.MODE)) {
            Node n = root.node("mode"); n.only("game_mode", "allow_flight", "flying", "walk_speed", "fly_speed");
            mode = new Mode(n.string("game_mode"), n.bool("allow_flight", false), n.bool("flying", false),
                    (float)Numbers.decimal(n, "walk_speed", 0, 1), (float)Numbers.decimal(n, "fly_speed", 0, 1));
        } else if (root.has("mode")) throw root.error("mode", "Mode present without MODE field");
        if (fields.contains(Field.INVENTORY)) {
            selectedSlot = (int)root.integer("selected_slot", 0, 1024); List<Slot> rows = new ArrayList<>();
            for (Node n : root.nodes("inventory")) { n.only("slot", "item", "count", "components"); rows.add(new Slot((int)n.integer("slot", 0, 1024), n.string("item"), (int)n.integer("count", 1, 9999), n.string("components", ""))); }
            inventory = List.copyOf(rows);
        } else if (root.has("inventory") || root.has("selected_slot")) throw root.error("inventory", "Inventory present without INVENTORY field");
        if (fields.contains(Field.EFFECTS)) {
            List<Effect> rows = new ArrayList<>();
            for (Node n : root.nodes("effects")) { n.only("id", "amplifier", "remaining_ticks", "ambient", "particles", "icon"); rows.add(new Effect(n.string("id"), (int)n.integer("amplifier", 0, 255), n.integer("remaining_ticks", 0, Long.MAX_VALUE), n.bool("ambient", false), n.bool("particles", true), n.bool("icon", true))); }
            effects = List.copyOf(rows);
        } else if (root.has("effects")) throw root.error("effects", "Effects present without EFFECTS field");
        return new PlayerStateSnapshot(schema, fields, position, mode, inventory, selectedSlot, effects);
    }
}
