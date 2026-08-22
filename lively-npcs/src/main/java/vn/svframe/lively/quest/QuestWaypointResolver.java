package vn.svframe.lively.quest;

import net.minecraft.util.math.Vec3d;
import vn.svframe.lively.world.SemanticStructureRegistry;

import java.util.Map;
import java.util.Optional;

/** Pure semantic resolver for projecting a quest objective into a navigation target. */
public final class QuestWaypointResolver {
    public record Target(String world, Vec3d position, String label) {}

    private QuestWaypointResolver() {}

    public static Optional<Target> resolve(QuestRuntime.Quest quest, SemanticStructureRegistry structures) {
        if (quest == null || structures == null) return Optional.empty();
        return quest.objectives().stream()
                .filter(objective -> !objective.hidden())
                .filter(objective -> quest.progress().getOrDefault(objective.id(), 0L) < objective.required())
                .map(objective -> resolve(objective, quest.title(), structures))
                .flatMap(Optional::stream)
                .findFirst();
    }

    public static Optional<Target> resolve(QuestRuntime.Objective objective, String questTitle,
                                           SemanticStructureRegistry structures) {
        if (objective == null || structures == null) return Optional.empty();
        Map<String, String> facts = objective.facts();
        String structureId = facts.getOrDefault("structure", "");
        if (structureId.isBlank() && structures.get(objective.target()).isPresent()) structureId = objective.target();
        if (!structureId.isBlank()) {
            var structure = structures.get(structureId).orElse(null);
            if (structure != null) {
                Vec3d target = point(structure.points().get("entrance")).orElseGet(() -> center(structure.bounds()));
                return Optional.of(new Target(structure.bounds().world(), target, label(questTitle, objective)));
            }
        }

        String world = facts.get("world");
        if (world == null || world.isBlank()) return Optional.empty();
        try {
            double x = Double.parseDouble(facts.get("x"));
            double y = Double.parseDouble(facts.get("y"));
            double z = Double.parseDouble(facts.get("z"));
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return Optional.empty();
            return Optional.of(new Target(world, new Vec3d(x, y, z), label(questTitle, objective)));
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private static Optional<Vec3d> point(String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        String[] parts = raw.split(",");
        if (parts.length != 3) return Optional.empty();
        try {
            double x = Double.parseDouble(parts[0].trim());
            double y = Double.parseDouble(parts[1].trim());
            double z = Double.parseDouble(parts[2].trim());
            return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
                    ? Optional.of(new Vec3d(x, y, z)) : Optional.empty();
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    private static Vec3d center(SemanticStructureRegistry.Bounds bounds) {
        return new Vec3d((bounds.minX() + bounds.maxX() + 1D) / 2D,
                bounds.minY() + 1D, (bounds.minZ() + bounds.maxZ() + 1D) / 2D);
    }

    private static String label(String questTitle, QuestRuntime.Objective objective) {
        String explicit = objective.facts().get("waypoint_label");
        if (explicit != null && !explicit.isBlank()) return truncate(explicit, 96);
        String base = questTitle == null || questTitle.isBlank() ? objective.id() : questTitle;
        return truncate(base, 96);
    }

    private static String truncate(String value, int max) {
        String clean = value.replaceAll("[\\r\\n\\t]", " ").trim();
        return clean.length() <= max ? clean : clean.substring(0, max);
    }
}
