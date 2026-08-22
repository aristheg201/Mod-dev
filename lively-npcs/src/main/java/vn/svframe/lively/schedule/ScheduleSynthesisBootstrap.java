package vn.svframe.lively.schedule;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import vn.svframe.lively.actor.ActorId;
import vn.svframe.lively.api.LivelyApi;
import vn.svframe.lively.npc.NpcDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Generates conservative defaults only for NPCs that have home/work metadata and no authored schedule. */
public final class ScheduleSynthesisBootstrap implements ModInitializer {
    private static final int MAX_SYNTHESIZED_PER_PULSE = 128;

    @Override
    public void onInitialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % 1200L == 0L) synthesizeMissing();
        });
    }

    int synthesizeMissing() {
        if (LivelyApi.npcs() == null) return 0;
        int created = 0;
        for (NpcDefinition definition : LivelyApi.npcs().snapshot().values().stream()
                .sorted(java.util.Comparator.comparing(value -> value.id().toString())).toList()) {
            if (created >= MAX_SYNTHESIZED_PER_PULSE) break;
            if (!Boolean.parseBoolean(definition.metadata().getOrDefault("schedule.autonomous", "true"))) continue;
            ActorId actor = new ActorId(definition.id(), ActorId.Kind.NPC);
            if (LivelyApi.schedules().hasSchedule(actor)) continue;
            List<ScheduleEngine.ScheduleEntry> entries = synthesize(definition);
            if (entries.isEmpty()) continue;
            LivelyApi.schedules().setSchedule(actor, entries);
            ensureOccupation(definition);
            created++;
        }
        return created;
    }

    static List<ScheduleEngine.ScheduleEntry> synthesize(NpcDefinition definition) {
        String home = definition.metadata().get("home.structure");
        String work = definition.metadata().get("work.structure");
        if (home == null || home.isBlank() || work == null || work.isBlank()) return List.of();
        int workStart = minute(definition.metadata().get("schedule.work_start"), 480);
        int workEnd = minute(definition.metadata().get("schedule.work_end"), 1020);
        if (workStart == workEnd) workEnd = Math.floorMod(workStart + 480, 1440);

        List<ScheduleEngine.ScheduleEntry> result = new ArrayList<>();
        result.add(entry(1320, 360, "sleep", home, 90, false));
        if (workStart != 360) result.add(entry(360, workStart, "morning_routine", home, 45, false));
        result.add(entry(workStart, workEnd, occupationActivity(definition), work, 80, true));
        if (workEnd != 1140) result.add(entry(workEnd, 1140, "socialize", work, 35, false));
        result.add(entry(1140, 1320, "home_routine", home, 55, false));
        return List.copyOf(result);
    }

    private static void ensureOccupation(NpcDefinition definition) {
        String occupationId = definition.metadata().getOrDefault("occupation.id", definition.role()).trim().toLowerCase(Locale.ROOT);
        if (occupationId.isBlank() || LivelyApi.schedules().occupation(occupationId).isPresent()) return;
        String workplaceType = LivelyApi.structures().get(definition.metadata().get("work.structure"))
                .map(structure -> structure.type()).orElse("workplace");
        String activitiesRaw = definition.metadata().getOrDefault("occupation.activities", occupationActivity(definition));
        List<String> activities = java.util.Arrays.stream(activitiesRaw.split(","))
                .map(String::trim).filter(value -> !value.isBlank()).limit(16).toList();
        if (activities.isEmpty()) activities = List.of("work");
        LivelyApi.schedules().registerOccupation(new ScheduleEngine.Occupation(occupationId, workplaceType, activities,
                Map.of("purpose", -.018D, "money", -.012D, "fatigue", .006D)));
    }

    private static String occupationActivity(NpcDefinition definition) {
        String configured = definition.metadata().get("occupation.activity");
        return configured == null || configured.isBlank() ? "work" : configured.trim();
    }

    private static ScheduleEngine.ScheduleEntry entry(int start, int end, String activity, String location, int priority, boolean mandatory) {
        return new ScheduleEngine.ScheduleEntry(start, end, activity, location, priority, mandatory, Map.of("generated", "lively"));
    }

    private static int minute(String raw, int fallback) {
        try {
            int value = raw == null ? fallback : Integer.parseInt(raw.trim());
            return Math.max(0, Math.min(1439, value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
