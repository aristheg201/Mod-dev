package vn.svframe.svrelationships.fabric.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

final class ConfigSchemaUpgradeTest {
    @TempDir Path root;

    @Test
    void bundledInstallRunsSchemaUpgradeAndBacksUpLegacyGameplayFiles() throws Exception {
        Path interactions = root.resolve("interactions.yml");
        Files.createDirectories(root);
        Files.writeString(interactions, "interactions:\n  legacy:\n    cooldown: 1d\n");

        BundledDefaults.installAll(root);

        assertEquals("3", Files.readString(root.resolve(".schema-version")).trim());
        assertTrue(Files.exists(root.resolve("interactions.yml.schema-v1.bak")));
        String installed = Files.readString(interactions);
        assertTrue(installed.contains("daily_talk:"));
        assertTrue(installed.contains("dating|engaged|married"));

        // A second boot must be idempotent and must not replace admin-edited files again.
        Files.writeString(interactions, installed + "\n# admin-edit\n");
        BundledDefaults.installAll(root);
        assertTrue(Files.readString(interactions).contains("# admin-edit"));
    }
}
