package vn.svframe.mythiclibfabric;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/** Installs the exact MythicLib 1.7.1 bundled defaults into the Fabric config directory. */
public final class MythicLibDefaultFiles {
    private static final Path ROOT = FabricLoader.getInstance().getConfigDir().resolve("MythicLib");
    private static final List<Entry> DEFAULTS = List.of(
            new Entry("config.yml", "config.yml", 1),
            new Entry("default/elements.yml", "elements.yml", 1),
            new Entry("default/indicators.yml", "indicators.yml", 1),
            new Entry("default/mitigation_types.yml", "mitigation_types.yml", 1),
            new Entry("default/on_hit_effects.yml", "on_hit_effects.yml", 1),
            new Entry("default/stats.yml", "stats.yml", 1),
            new Entry("default/triggers.yml", "triggers.yml", 1),
            new Entry("default/script/elemental_attacks.yml", "script/elemental_attacks.yml", 1),
            new Entry("default/script/example_skills.yml", "script/example_skills.yml", 1),
            new Entry("default/script/mitigation_types.yml", "script/mitigation_types.yml", 1),
            new Entry("default/script/mmocore_scripts.yml", "script/mmocore_scripts.yml", 1),
            new Entry("default/script/mmoitems_scripts.yml", "script/mmoitems_scripts.yml", 1),
            new Entry("default/script/on_hit_effects.yml", "script/on_hit_effects.yml", 1),
            new Entry("default/skill/default_skills.yml", "skill/default_skills.yml", 12),
            new Entry("default/skill/example_skills.yml", "skill/example_skills.yml", 1)
    );

    private MythicLibDefaultFiles() { }

    public static void ensure() throws IOException {
        Files.createDirectories(ROOT);
        ClassLoader loader = MythicLibDefaultFiles.class.getClassLoader();
        for (Entry entry : DEFAULTS) {
            Path target = ROOT.resolve(entry.target()).normalize();
            if (!target.startsWith(ROOT) || Files.exists(target)) continue;
            Files.createDirectories(target.getParent());
            if (entry.parts() == 1) {
                try (InputStream input = required(loader, entry.resource())) {
                    Files.copy(input, target, StandardCopyOption.COPY_ATTRIBUTES);
                }
                continue;
            }
            try (OutputStream output = Files.newOutputStream(target)) {
                for (int part = 1; part <= entry.parts(); part++) {
                    String resource = entry.resource() + ".part" + String.format("%02d", part);
                    try (InputStream input = required(loader, resource)) {
                        input.transferTo(output);
                    }
                }
            } catch (IOException exception) {
                Files.deleteIfExists(target);
                throw exception;
            }
        }
    }

    private static InputStream required(ClassLoader loader, String resource) throws IOException {
        InputStream input = loader.getResourceAsStream(resource);
        if (input == null) throw new IOException("Missing bundled MythicLib resource: " + resource);
        return input;
    }

    private record Entry(String resource, String target, int parts) { }
}
