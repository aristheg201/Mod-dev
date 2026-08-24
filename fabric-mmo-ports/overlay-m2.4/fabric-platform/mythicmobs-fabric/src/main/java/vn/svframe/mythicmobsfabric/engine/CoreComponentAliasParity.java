package vn.svframe.mythicmobsfabric.engine;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Restores the exact alias surface declared by MythicMobs 5.6.2 core component
 * annotations.  The TSV is generated from the original plugin and already ships
 * in the Fabric mod.  Whenever one alias/class has a Fabric implementation, all
 * aliases belonging to that same original class are bound to that implementation.
 *
 * This is deliberately applied after built-in/parity registrations so explicit
 * Fabric registrations keep precedence while missing legacy aliases are filled.
 */
public final class CoreComponentAliasParity {
    private static final String RESOURCE = "mythicmobs-core-components.tsv";
    private static final Map<String, Map<String, String>> CLASS_BY_ALIAS = new HashMap<>();
    private static final Map<String, Map<String, Set<String>>> ALIASES_BY_CLASS = new HashMap<>();

    static {
        load();
    }

    private CoreComponentAliasParity() {}

    public static void expand(SkillRuntime runtime) {
        expandField(runtime, "mechanics", "mechanic");
        expandField(runtime, "conditions", "condition");
        expandField(runtime, "targeters", "targeter");
    }

    public static Set<String> aliases(String type, String id) {
        String normalizedType = normalizeType(type);
        Map<String, String> classes = CLASS_BY_ALIAS.get(normalizedType);
        if (classes == null) return Set.of();
        String owner = classes.get(SkillLine.normalize(id));
        if (owner == null) return Set.of();
        return Set.copyOf(ALIASES_BY_CLASS.getOrDefault(normalizedType, Map.of()).getOrDefault(owner, Set.of()));
    }

    @SuppressWarnings("unchecked")
    private static void expandField(SkillRuntime runtime, String fieldName, String type) {
        try {
            Field field = SkillRuntime.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            Map<String, Object> registry = (Map<String, Object>) field.get(runtime);
            Map<String, Object> snapshot = new LinkedHashMap<>(registry);
            Map<String, String> classes = CLASS_BY_ALIAS.getOrDefault(type, Map.of());
            Map<String, Set<String>> aliasesByClass = ALIASES_BY_CLASS.getOrDefault(type, Map.of());

            for (Map.Entry<String, Object> entry : snapshot.entrySet()) {
                String owner = classes.get(SkillLine.normalize(entry.getKey()));
                if (owner == null) continue;
                for (String alias : aliasesByClass.getOrDefault(owner, Set.of())) {
                    registry.putIfAbsent(SkillLine.normalize(alias), entry.getValue());
                }
            }
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Unable to expand MythicMobs 5.6.2 " + type + " aliases", ex);
        }
    }

    private static void load() {
        try (InputStream stream = CoreComponentAliasParity.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (stream == null) throw new IllegalStateException("Missing " + RESOURCE);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank() || line.charAt(0) == '#') continue;
                    String[] parts = line.split("\\t", 3);
                    if (parts.length != 3) continue;
                    String type = normalizeType(parts[0]);
                    String alias = parts[1].trim();
                    String owner = parts[2].trim();
                    if (alias.isEmpty() || owner.isEmpty()) continue;
                    CLASS_BY_ALIAS.computeIfAbsent(type, ignored -> new HashMap<>())
                            .put(SkillLine.normalize(alias), owner);
                    ALIASES_BY_CLASS.computeIfAbsent(type, ignored -> new HashMap<>())
                            .computeIfAbsent(owner, ignored -> new LinkedHashSet<>())
                            .add(alias);
                }
            }
        } catch (Exception ex) {
            throw ex instanceof IllegalStateException state ? state
                    : new IllegalStateException("Unable to load MythicMobs 5.6.2 component alias registry", ex);
        }
    }

    private static String normalizeType(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
