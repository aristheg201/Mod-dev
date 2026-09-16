package vn.svframe.svrelationships.fabric.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

final class ConfigSchemaUpgradeTest {
    @TempDir Path root;

    @Test
    void bundledInstallRunsSchemaUpgradeAndBacksUpLegacyGameplayAndGuiFiles() throws Exception {
        Files.createDirectories(root.resolve("gui"));
        Path interactions = root.resolve("interactions.yml");
        Path mainGui = root.resolve("gui/main.yml");
        Files.writeString(interactions, "interactions:\n  legacy:\n    cooldown: 1d\n");
        Files.writeString(mainGui, "id: main\nscreen: generic_9x6\ntitle_key: ui.main.title\ncomponents: {}\n");

        BundledDefaults.installAll(root);

        assertEquals("4", Files.readString(root.resolve(".schema-version")).trim());
        assertTrue(Files.exists(root.resolve("interactions.yml.schema-v1.bak")));
        assertTrue(Files.exists(root.resolve("gui/main.yml.schema-v1.bak")));

        String installedInteractions = Files.readString(interactions);
        assertTrue(installedInteractions.contains("daily_talk:"));
        assertTrue(installedInteractions.contains("dating|engaged|married"));

        String installedMainGui = Files.readString(mainGui);
        assertTrue(installedMainGui.contains("title_key: gui.main.title"));
        assertFalse(installedMainGui.contains("ui.main.title"));

        // A second boot must be idempotent and must not replace admin-edited files again.
        Files.writeString(mainGui, installedMainGui + "\n# admin-edit\n");
        BundledDefaults.installAll(root);
        assertTrue(Files.readString(mainGui).contains("# admin-edit"));
    }
}
