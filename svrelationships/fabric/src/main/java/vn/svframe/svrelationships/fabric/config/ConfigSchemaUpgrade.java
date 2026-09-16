package vn.svframe.svrelationships.fabric.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/** One-time migration for shipped gameplay/GUI schema changes. */
final class ConfigSchemaUpgrade {
    private static final int CURRENT = 4;

    private static final List<String> V3_FILES = List.of(
            "interactions.yml",
            "daycare-policies.yml",
            "gui/relationship.yml",
            "gui/daycare.yml",
            "lang/en_us.yml",
            "lang/vi_vn.yml",
            "lang/en_us_gui.yml",
            "lang/vi_vn_gui.yml"
    );

    /** Files omitted by the v3 migration, which allowed stale ui.* keys to survive. */
    private static final List<String> V4_GUI_FILES = List.of(
            "gui/main.yml",
            "gui/owned.yml",
            "gui/partners.yml",
            "gui/daycare_setup.yml",
            "gui/family.yml"
    );

    private ConfigSchemaUpgrade() {}

    static void upgrade(Path root) throws IOException {
        Path marker = root.resolve(".schema-version");
        int version = readVersion(marker);
        if (version >= CURRENT) return;

        if (version < 3) {
            replaceFromDefaults(root, V3_FILES, version);
            version = 3;
        }
        if (version < 4) {
            replaceFromDefaults(root, V4_GUI_FILES, version);
            version = 4;
        }

        Files.writeString(marker, Integer.toString(version), StandardCharsets.UTF_8);
    }

    private static void replaceFromDefaults(Path root, List<String> files, int sourceVersion) throws IOException {
        for (String relative : files) {
            Path target = root.resolve(relative);
            if (Files.exists(target)) {
                Path backup = target.resolveSibling(target.getFileName() + ".schema-v" + sourceVersion + ".bak");
                if (!Files.exists(backup)) Files.copy(target, backup);
            }
            installDefault(relative, target);
        }
    }

    private static int readVersion(Path marker) {
        if (!Files.exists(marker)) return 1;
        try { return Integer.parseInt(Files.readString(marker, StandardCharsets.UTF_8).trim()); }
        catch (Exception ignored) { return 1; }
    }

    private static void installDefault(String relative, Path target) throws IOException {
        Path parent = target.getParent();
        if (parent != null) Files.createDirectories(parent);
        try (InputStream input = ConfigSchemaUpgrade.class.getResourceAsStream("/defaults/" + relative)) {
            if (input == null) throw new IOException("Missing bundled resource: /defaults/" + relative);
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
