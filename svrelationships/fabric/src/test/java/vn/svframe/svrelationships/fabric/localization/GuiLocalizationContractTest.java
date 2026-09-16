package vn.svframe.svrelationships.fabric.localization;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class GuiLocalizationContractTest {
    private static final List<String> GUI_FILES = List.of(
            "main.yml", "owned.yml", "relationship.yml", "partners.yml",
            "daycare.yml", "daycare_setup.yml", "family.yml"
    );

    @Test
    void everyBundledGuiLocalizationKeyExistsInEnglishAndVietnamese() throws Exception {
        Set<String> guiKeys = new LinkedHashSet<>();
        for (String file : GUI_FILES) collectGuiKeys(load("/defaults/gui/" + file), guiKeys);

        Set<String> english = loadLocale("en_us");
        Set<String> vietnamese = loadLocale("vi_vn");

        for (String key : guiKeys) {
            assertTrue(english.contains(key), () -> "Missing English GUI localization: " + key);
            assertTrue(vietnamese.contains(key), () -> "Missing Vietnamese GUI localization: " + key);
        }
    }

    private static Set<String> loadLocale(String locale) throws Exception {
        Set<String> keys = new LinkedHashSet<>();
        for (String suffix : List.of("", "_gui", "_admin", "_morning")) {
            Map<String, Object> map = load("/defaults/lang/" + locale + suffix + ".yml");
            keys.addAll(map.keySet());
        }
        return keys;
    }

    private static void collectGuiKeys(Map<String, Object> root, Set<String> keys) {
        addString(root.get("title_key"), keys);
        collectEntries(root.get("components"), keys);
        collectEntries(root.get("repeaters"), keys);
    }

    private static void collectEntries(Object raw, Set<String> keys) {
        if (!(raw instanceof Map<?, ?> entries)) return;
        for (Object value : entries.values()) {
            if (!(value instanceof Map<?, ?> entry)) continue;
            addString(entry.get("name_key"), keys);
            Object lore = entry.get("lore_keys");
            if (lore instanceof List<?> list) list.forEach(item -> addString(item, keys));
        }
    }

    private static void addString(Object value, Set<String> keys) {
        if (value != null && !String.valueOf(value).isBlank()) keys.add(String.valueOf(value));
    }

    private static Map<String, Object> load(String resource) throws Exception {
        try (InputStream input = GuiLocalizationContractTest.class.getResourceAsStream(resource)) {
            if (input == null) throw new IllegalStateException("Missing test resource: " + resource);
            Object loaded = new Yaml(new SafeConstructor(new LoaderOptions())).load(input);
            if (!(loaded instanceof Map<?, ?> raw)) throw new IllegalStateException("Expected map in " + resource);
            Map<String, Object> result = new LinkedHashMap<>();
            raw.forEach((key, value) -> result.put(String.valueOf(key), value));
            return result;
        }
    }
}
