package vn.svframe.svrelationships.fabric.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/** One-time migration for the pre-playable relationship/daycare GUI schema. */
final class ConfigSchemaUpgrade {
    private static final int CURRENT = 2;
    private static final List<String> REPLACE_FROM_DEFAULT = List.of(
            "interactions.yml",
            "daycare-policies.yml",
            "gui/relationship.yml",
            "gui/daycare.yml",
            "lang/en_us.yml",
            "lang/vi_vn.yml",
            "lang/en_us_gui.yml",
            "lang/vi_vn_gui.yml"
    );

    private ConfigSchemaUpgrade() {}

    static void upgrade(Path root) throws IOException {
        Path marker = root.resolve(".schema-version");
        int version = readVersion(marker);
        if (version >= CURRENT) return;

        for (String relative : REPLACE_FROM_DEFAULT) {
            Path target = root.resolve(relative);
            if (Files.exists(target)) {
                Path backup = target.resolveSibling(target.getFileName() + ".schema-v1.bak");
                if (!Files.exists(backup)) Files.copy(target, backup);
            }
            installDefault(relative, target);
        }

        Files.writeString(marker, Integer.toString(CURRENT), StandardCharsets.UTF_8);
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
