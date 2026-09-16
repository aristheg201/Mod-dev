package vn.svframe.svrelationships.fabric.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

final class ConfigSchemaUpgradeTest {
    @TempDir Path root;

    @Test
    void bundledInstallMigratesLegacyGuiKeysAndBacksThemUp() throws Exception {
        Files.createDirectories(root.resolve("gui"));
        Path interactions = root.resolve("interactions.yml");
        Path mainGui = root.resolve("gui/main.yml");
        Files.writeString(interactions, "interactions:\n  legacy:\n    cooldown: 1d\n");
        Files.writeString(mainGui, "id: main\nscreen: generic_9x6\ntitle_key: ui.main.title\ncomponents: {}\n");

        BundledDefaults.installAll(root);

        assertEquals("4", Files.readString(root.resolve(".schema-version")).trim());
        assertTrue(Files.exists(root.resolve("interactions.yml.schema-v1.bak")));
        assertTrue(Files.exists(root.resolve("gui/main.yml.schema-v3.bak")));

        String installedInteractions = Files.readString(interactions);
        assertTrue(installedInteractions.contains("daily_talk:"));
        assertTrue(installedInteractions.contains("dating|engaged|married"));

        String installedMainGui = Files.readString(mainGui);
        assertTrue(installedMainGui.contains("title_key: gui.main.title"));
        assertFalse(installedMainGui.contains("title_key: ui.main.title"));
    }

    @Test
    void v4GuiMigrationDoesNotOverwriteSchemaV3GameplayConfig() throws Exception {
        Files.createDirectories(root.resolve("gui"));
        Files.writeString(root.resolve(".schema-version"), "3");

        Path interactions = root.resolve("interactions.yml");
        String adminGameplay = "interactions:\n  admin_custom:\n    cooldown: 2d\n";
        Files.writeString(interactions, adminGameplay);

        Path mainGui = root.resolve("gui/main.yml");
        Files.writeString(mainGui, "id: main\nscreen: generic_9x6\ntitle_key: ui.main.title\ncomponents: {}\n");

        BundledDefaults.installAll(root);

        assertEquals("4", Files.readString(root.resolve(".schema-version")).trim());
        assertEquals(adminGameplay, Files.readString(interactions));
        assertFalse(Files.exists(root.resolve("interactions.yml.schema-v3.bak")));
        assertTrue(Files.exists(root.resolve("gui/main.yml.schema-v3.bak")));
        assertTrue(Files.readString(mainGui).contains("title_key: gui.main.title"));

        Files.writeString(mainGui, Files.readString(mainGui) + "\n# admin-edit\n");
        BundledDefaults.installAll(root);
        assertTrue(Files.readString(mainGui).contains("# admin-edit"));
    }
}
