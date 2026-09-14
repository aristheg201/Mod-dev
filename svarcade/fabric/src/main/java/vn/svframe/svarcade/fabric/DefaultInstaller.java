package vn.svframe.svarcade.fabric;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Installs packaged defaults once without overwriting administrator-owned files. Invoke only on the bounded I/O executor. */
final class DefaultInstaller {
    private DefaultInstaller() { }

    static int install(Path configRoot) {
        Objects.requireNonNull(configRoot); Path root = configRoot.toAbsolutePath().normalize();
        return installManifest("/defaults/config/", root) + installManifest("/defaults/minigames/", root.resolve("minigames"));
    }

    private static int installManifest(String resourceRoot, Path target) {
        List<String> entries = manifest(resourceRoot); int created = 0;
        for (String entry : entries) {
            validate(entry); Path destination = target.resolve(entry).normalize();
            if (!destination.startsWith(target)) throw new IllegalStateException("Default path escaped target");
            try {
                if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) continue;
                Files.createDirectories(destination.getParent());
                try (InputStream in = resource(resourceRoot + entry)) {
                    Files.copy(in, destination); created++;
                } catch (FileAlreadyExistsException race) { /* administrator or another bootstrap won the create race */ }
            } catch (IOException e) { throw new UncheckedIOException("Cannot install default " + entry, e); }
        }
        return created;
    }

    private static List<String> manifest(String root) {
        try (InputStream in = resource(root + "index.txt"); BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            List<String> result = new ArrayList<>(); String line;
            while ((line = reader.readLine()) != null) {
                String value = line.trim(); if (value.isEmpty() || value.startsWith("#")) continue;
                if (result.size() >= 512) throw new IllegalStateException("Default manifest limit"); result.add(value);
            }
            if (result.isEmpty()) throw new IllegalStateException("Default manifest empty"); return List.copyOf(result);
        } catch (IOException e) { throw new UncheckedIOException("Cannot read default manifest", e); }
    }
    private static InputStream resource(String path) {
        InputStream in = DefaultInstaller.class.getResourceAsStream(path); if (in == null) throw new IllegalStateException("Missing packaged resource: " + path); return in;
    }
    private static void validate(String entry) {
        Path path = Path.of(entry);
        if (entry.contains("\\") || path.isAbsolute() || path.getNameCount() < 1 || entry.length() > 512) throw new IllegalStateException("Unsafe default path: " + entry);
        for (Path part : path) if (part.toString().equals(".") || part.toString().equals("..")) throw new IllegalStateException("Unsafe default path: " + entry);
        if (!entry.endsWith(".yml")) throw new IllegalStateException("Unsupported default resource: " + entry);
    }
}
