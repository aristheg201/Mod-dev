package vn.svframe.svrelationships.fabric.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public final class BundledDefaults {
    private static final List<String> RELATIVE_PATHS = List.of(
            "main.yml",
            "households.yml",
            "progression.yml",
            "routes.yml",
            "ranks.yml",
            "rewards.yml",
            "reward-policies.yml",
            "automatic-rewards.yml",
            "interactions.yml",
            "gifts.yml",
            "personalities.yml",
            "selectors.yml",
            "partnership.yml",
            "daycare.yml",
            "daycare-policies.yml",
            "inheritance.yml",
            "ceremonies.yml",
            "anniversaries.yml",
            "schedules.yml",
            "dialogues.yml",
            "gui/main.yml",
            "gui/owned.yml",
            "gui/relationship.yml",
            "gui/partners.yml",
            "gui/daycare.yml",
            "gui/family.yml",
            "lang/en_us.yml",
            "lang/en_us_gui.yml",
            "lang/en_us_admin.yml",
            "lang/en_us_morning.yml",
            "lang/vi_vn.yml",
            "lang/vi_vn_gui.yml",
            "lang/vi_vn_admin.yml",
            "lang/vi_vn_morning.yml"
    );

    private BundledDefaults() {}

    public static void installAll(Path root) throws IOException {
        Objects.requireNonNull(root, "root");
        for (String relativePath : RELATIVE_PATHS) installOne(root, relativePath);
        ConfigSchemaUpgrade.upgrade(root);
    }

    static List<String> relativePaths() { return RELATIVE_PATHS; }

    private static void installOne(Path root, String relativePath) throws IOException {
        Path target = root.resolve(relativePath);
        if (Files.exists(target)) return;
        Path parent = target.getParent();
        if (parent != null) Files.createDirectories(parent);
        String resourcePath = "/defaults/" + relativePath;
        try (InputStream input = BundledDefaults.class.getResourceAsStream(resourcePath)) {
            if (input == null) throw new IOException("Missing bundled resource: " + resourcePath);
            Files.copy(input, target);
        }
    }
}
