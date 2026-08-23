package vn.svframe.mmocorefabric.runtime.event;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Small server-side event surface used by the Fabric port instead of Bukkit's PluginManager. */
public final class MMOCoreEventBus {
    private final List<Consumer<Event>> listeners = new ArrayList<>();

    public AutoCloseable register(Consumer<Event> listener) {
        Objects.requireNonNull(listener, "listener");
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    public <T extends Event> T post(T event) {
        for (Consumer<Event> listener : List.copyOf(listeners)) listener.accept(event);
        return event;
    }

    public sealed interface Event permits PlayerLevelChangeEvent, PlayerExperienceGainEvent, PlayerChangeClassEvent, PlayerDataLoadEvent {}

    public record PlayerLevelChangeEvent(String playerId, int oldLevel, int newLevel, LevelReason reason) implements Event {}

    public static final class PlayerExperienceGainEvent implements Event {
        private final String playerId;
        private final String source;
        private double experience;
        private boolean cancelled;

        public PlayerExperienceGainEvent(String playerId, double experience, String source) {
            this.playerId = Objects.requireNonNull(playerId, "playerId");
            this.source = Objects.requireNonNull(source, "source");
            this.experience = experience;
        }

        public String playerId() { return playerId; }
        public String source() { return source; }
        public double experience() { return experience; }
        public void setExperience(double experience) { this.experience = experience; }
        public boolean cancelled() { return cancelled; }
        public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    }

    public static final class PlayerChangeClassEvent implements Event {
        private final String playerId;
        private final String oldClassId;
        private final String newClassId;
        private final ClassChangeReason reason;
        private boolean cancelled;

        public PlayerChangeClassEvent(String playerId, String oldClassId, String newClassId, ClassChangeReason reason) {
            this.playerId = Objects.requireNonNull(playerId, "playerId");
            this.oldClassId = oldClassId;
            this.newClassId = Objects.requireNonNull(newClassId, "newClassId");
            this.reason = Objects.requireNonNull(reason, "reason");
        }

        public String playerId() { return playerId; }
        public String oldClassId() { return oldClassId; }
        public String newClassId() { return newClassId; }
        public ClassChangeReason reason() { return reason; }
        public boolean cancelled() { return cancelled; }
        public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    }

    public record PlayerDataLoadEvent(String playerId) implements Event {}

    public enum LevelReason { LEVEL_UP, COMMAND, RESET, CHOOSE_CLASS, CHOOSE_PROFILE, UNKNOWN, OTHER }
    public enum ClassChangeReason { COMMAND_SELECT, COMMAND_FORCE, GUI, UNKNOWN }
}
