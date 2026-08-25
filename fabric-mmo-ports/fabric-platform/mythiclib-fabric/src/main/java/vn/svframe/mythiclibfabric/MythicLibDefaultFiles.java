package vn.svframe.mythiclibfabric;

import net.fabricmc.loader.api.FabricLoader;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Installs the exact MythicLib 1.7.1 bundled defaults into the Fabric config directory. */
public final class MythicLibDefaultFiles {
    private static final Path ROOT = FabricLoader.getInstance().getConfigDir().resolve("MythicLib").toAbsolutePath().normalize();
    private static final String ARCHIVE_SHA256 = "fc68b690ccdd874c2c8c5ce6207218d19af6904938c1cd9437cfa4e7341ba86e";
    private static final int ARCHIVE_PARTS = 4;
    private static final Map<String, String> TARGETS = targets();

    private MythicLibDefaultFiles() { }

    public static void ensure() throws IOException {
        Files.createDirectories(ROOT);
        byte[] archive = readArchive();
        verifyArchive(archive);

        Map<String, Boolean> seen = new LinkedHashMap<>();
        for (String resource : TARGETS.keySet()) seen.put(resource, false);

        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String targetName = TARGETS.get(entry.getName());
                if (targetName == null) continue;
                seen.put(entry.getName(), true);

                Path target = ROOT.resolve(targetName).normalize();
                if (!target.startsWith(ROOT)) throw new IOException("Unsafe MythicLib default target: " + targetName);
                if (Files.exists(target)) continue;

                Files.createDirectories(target.getParent());
                Path temporary = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
                try {
                    Files.copy(zip, temporary, StandardCopyOption.REPLACE_EXISTING);
                    try {
                        Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
                    } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                        Files.move(temporary, target);
                    }
                } finally {
                    Files.deleteIfExists(temporary);
                }
            }
        }

        for (Map.Entry<String, Boolean> expected : seen.entrySet()) {
            if (!expected.getValue()) throw new IOException("Bundled MythicLib archive is missing " + expected.getKey());
        }
    }

    private static byte[] readArchive() throws IOException {
        ClassLoader loader = MythicLibDefaultFiles.class.getClassLoader();
        ByteArrayOutputStream output = new ByteArrayOutputStream(32 * 1024);
        for (int part = 1; part <= ARCHIVE_PARTS; part++) {
            String resource = "mythiclib-1.7.1-defaults.zip.part" + part;
            try (InputStream input = loader.getResourceAsStream(resource)) {
                if (input == null) throw new IOException("Missing bundled MythicLib resource archive part: " + resource);
                input.transferTo(output);
            }
        }
        return output.toByteArray();
    }

    private static void verifyArchive(byte[] archive) throws IOException {
        try {
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(archive));
            if (!ARCHIVE_SHA256.equals(digest)) throw new IOException("MythicLib 1.7.1 bundled defaults checksum mismatch: " + digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static Map<String, String> targets() {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        values.put("config.yml", "config.yml");
        values.put("default/elements.yml", "elements.yml");
        values.put("default/indicators.yml", "indicators.yml");
        values.put("default/mitigation_types.yml", "mitigation_types.yml");
        values.put("default/on_hit_effects.yml", "on_hit_effects.yml");
        values.put("default/stats.yml", "stats.yml");
        values.put("default/triggers.yml", "triggers.yml");
        values.put("default/script/elemental_attacks.yml", "script/elemental_attacks.yml");
        values.put("default/script/example_skills.yml", "script/example_skills.yml");
        values.put("default/script/mitigation_types.yml", "script/mitigation_types.yml");
        values.put("default/script/mmocore_scripts.yml", "script/mmocore_scripts.yml");
        values.put("default/script/mmoitems_scripts.yml", "script/mmoitems_scripts.yml");
        values.put("default/script/on_hit_effects.yml", "script/on_hit_effects.yml");
        values.put("default/skill/default_skills.yml", "skill/default_skills.yml");
        values.put("default/skill/example_skills.yml", "skill/example_skills.yml");
        return Map.copyOf(values);
    }
}
