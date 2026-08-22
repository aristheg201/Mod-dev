package vn.svframe.lively.npc;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** Resolves admin-facing NPC references without making humans copy UUIDs. */
public final class NpcReference {
    public record Resolution(UUID id, String error) {
        public boolean found() { return id != null; }
        public static Resolution found(UUID id) { return new Resolution(Objects.requireNonNull(id), ""); }
        public static Resolution error(String message) { return new Resolution(null, message); }
    }

    private NpcReference() {}

    public static Resolution resolve(NpcRuntime runtime, String raw) {
        Objects.requireNonNull(runtime, "runtime");
        String reference = raw == null ? "" : raw.trim();
        if (reference.isEmpty()) return Resolution.error("NPC name is empty");

        try {
            UUID uuid = UUID.fromString(reference);
            if (runtime.get(uuid).isPresent()) return Resolution.found(uuid);
        } catch (IllegalArgumentException ignored) {}

        List<NpcDefinition> definitions = runtime.snapshot().values().stream()
                .sorted(Comparator.comparing(NpcDefinition::name, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(value -> value.id().toString()))
                .toList();

        List<NpcDefinition> exact = definitions.stream().filter(value -> value.name().equals(reference)).toList();
        if (exact.size() == 1) return Resolution.found(exact.getFirst().id());
        if (exact.size() > 1) return ambiguous(reference, exact);

        List<NpcDefinition> insensitive = definitions.stream()
                .filter(value -> value.name().equalsIgnoreCase(reference)).toList();
        if (insensitive.size() == 1) return Resolution.found(insensitive.getFirst().id());
        if (insensitive.size() > 1) return ambiguous(reference, insensitive);

        String needle = reference.toLowerCase(Locale.ROOT);
        List<NpcDefinition> prefix = definitions.stream()
                .filter(value -> value.name().toLowerCase(Locale.ROOT).startsWith(needle)).toList();
        if (prefix.size() == 1) return Resolution.found(prefix.getFirst().id());
        if (prefix.size() > 1) return ambiguous(reference, prefix);

        return Resolution.error("Unknown NPC: " + reference);
    }

    public static List<String> names(NpcRuntime runtime) {
        if (runtime == null) return List.of();
        return runtime.snapshot().values().stream().map(NpcDefinition::name)
                .distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    private static Resolution ambiguous(String reference, List<NpcDefinition> matches) {
        String names = matches.stream().limit(6)
                .map(value -> value.name() + " (" + value.role() + ")")
                .reduce((a, b) -> a + ", " + b).orElse("");
        return Resolution.error("Ambiguous NPC name '" + reference + "': " + names + ". Rename duplicates instead of using UUIDs.");
    }
}
