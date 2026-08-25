package vn.svframe.mythiclibfabric.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Native Fabric port of MythicLib 1.7.1 Element/ElementManager data surface. */
public final class NativeElementRegistry {
    private final Map<String, Element> elements = new LinkedHashMap<>();

    public synchronized void clear() { elements.clear(); }

    public synchronized void load(Map<String, Object> root) {
        LinkedHashMap<String, Element> next = new LinkedHashMap<>();
        if (root != null) {
            for (Map.Entry<String, Object> entry : root.entrySet()) {
                if (!(entry.getValue() instanceof Map<?, ?> raw)) continue;
                @SuppressWarnings("unchecked") Map<String, Object> section = (Map<String, Object>) raw;
                Element element = Element.from(entry.getKey(), section);
                next.put(element.id(), element);
            }
        }
        elements.clear();
        elements.putAll(next);
    }

    public synchronized int size() { return elements.size(); }
    public synchronized List<Element> values() { return List.copyOf(elements.values()); }
    public synchronized Element get(String id) { return elements.get(normalize(id)); }

    public record Element(String id, String name, String icon, String loreIcon, String color,
                          String regularAttack, String criticalStrike) {
        public Element {
            id = normalize(id);
            if (name == null) throw new NullPointerException("Please specify an element name");
            icon = icon == null || icon.isBlank() ? "DIRT" : normalize(icon);
            loreIcon = loreIcon == null ? "?" : loreIcon;
            color = color == null ? "&f" : color;
            if (regularAttack == null) throw new NullPointerException("Could not find skill for regular attacks");
        }

        public String skill(boolean critical) {
            return critical && criticalStrike != null ? criticalStrike : regularAttack;
        }

        private static Element from(String id, Map<String, Object> section) {
            return new Element(
                    id,
                    string(section.get("name"), null),
                    string(section.get("icon"), "DIRT"),
                    string(first(section, "lore-icon", "lore_icon"), "?"),
                    string(section.get("color"), "&f"),
                    string(first(section, "regular-attack", "regular_attack"), null),
                    nullableString(first(section, "crit-strike", "crit_strike")));
        }
    }

    private static Object first(Map<String, Object> section, String... keys) {
        for (String key : keys) if (section.containsKey(key)) return section.get(key);
        return null;
    }

    private static String string(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private static String nullableString(Object value) {
        if (value == null) return null;
        String string = String.valueOf(value).trim();
        return string.isEmpty() ? null : string;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }
}
