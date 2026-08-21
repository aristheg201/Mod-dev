package vn.svframe.lively.schedule;

import vn.svframe.lively.actor.ActorId;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Schedules are constraints and intentions, not hardcoded movement scripts. */
public final class ScheduleEngine {
    public record ScheduleEntry(int startMinute, int endMinute, String activity, String semanticLocation,
                                int priority, boolean mandatory, Map<String, String> constraints) {
        public ScheduleEntry {
            if (startMinute < 0 || startMinute >= 1440 || endMinute < 0 || endMinute > 1440 || startMinute == endMinute) throw new IllegalArgumentException("invalid schedule range");
            Objects.requireNonNull(activity); constraints = Map.copyOf(constraints); priority = Math.max(0, Math.min(100, priority));
        }
        public boolean active(int minute) {
            return startMinute < endMinute ? minute >= startMinute && minute < endMinute : minute >= startMinute || minute < endMinute;
        }
    }
    public record Occupation(String id, String workplaceType, List<String> activities, Map<String, Double> needsImpact) {
        public Occupation { Objects.requireNonNull(id); activities = List.copyOf(activities); needsImpact = Map.copyOf(needsImpact); }
    }

    private final ConcurrentHashMap<ActorId, List<ScheduleEntry>> schedules = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Occupation> occupations = new ConcurrentHashMap<>();

    public void setSchedule(ActorId actor, List<ScheduleEntry> entries) {
        if (entries.size() > 128) throw new IllegalArgumentException("schedule too large");
        schedules.put(actor, entries.stream().sorted(Comparator.comparingInt(ScheduleEntry::startMinute)).toList());
    }
    public Optional<ScheduleEntry> current(ActorId actor, int minuteOfDay) {
        return schedules.getOrDefault(actor, List.of()).stream().filter(e -> e.active(minuteOfDay))
                .max(Comparator.comparingInt(ScheduleEntry::priority));
    }
    public void registerOccupation(Occupation occupation) { occupations.put(occupation.id(), occupation); }
    public Optional<Occupation> occupation(String id) { return Optional.ofNullable(occupations.get(id)); }
    public Snapshot snapshot() { return new Snapshot(Map.copyOf(schedules), Map.copyOf(occupations)); }
    public void restore(Snapshot snapshot) { schedules.clear(); schedules.putAll(snapshot.schedules()); occupations.clear(); occupations.putAll(snapshot.occupations()); }
    public record Snapshot(Map<ActorId, List<ScheduleEntry>> schedules, Map<String, Occupation> occupations) {
        public Snapshot { schedules = Map.copyOf(schedules); occupations = Map.copyOf(occupations); }
    }
}
