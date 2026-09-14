package vn.svframe.svarcade.fabric;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Installs packaged defaults once without overwriting administrator-owned files. Invoke only on the bounded I/O executor. */
final class DefaultInstaller {
    private static final String ROOT = "/defaults/minigames/";
    private DefaultInstaller() { }

    static int install(Path target) {
        Objects.requireNonNull(target); Path root = target.toAbsolutePath().normalize();
        List<String> entries = manifest(); int created = 0;
        for (String entry : entries) {
            validate(entry); Path destination = root.resolve(entry).normalize();
            if (!destination.startsWith(root)) throw new IllegalStateException("Default path escaped target");
            try {
                if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) continue;
                Files.createDirectories(destination.getParent());
                try (InputStream in = resource(ROOT + entry)) {
                    Files.copy(in, destination); created++;
                } catch (FileAlreadyExistsException race) { /* administrator or another bootstrap won the create race */ }
            } catch (IOException e) { throw new UncheckedIOException("Cannot install default " + entry, e); }
        }
        return created;
    }

    private static List<String> manifest() {
        try (InputStream in = resource(ROOT + "index.txt"); BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            List<String> result = new ArrayList<>(); String line;
            while ((line = reader.readLine()) != null) {
                String value = line.trim(); if (value.isEmpty() || value.startsWith("#")) continue;
                if (result.size() >= 256) throw new IllegalStateException("Default manifest limit"); result.add(value);
            }
            if (result.isEmpty()) throw new IllegalStateException("Default manifest empty"); return List.copyOf(result);
        } catch (IOException e) { throw new UncheckedIOException("Cannot read default manifest", e); }
    }
    private static InputStream resource(String path) {
        InputStream in = DefaultInstaller.class.getResourceAsStream(path); if (in == null) throw new IllegalStateException("Missing packaged resource: " + path); return in;
    }
    private static void validate(String entry) {
        Path path = Path.of(entry);
        if (entry.contains("\\") || path.isAbsolute() || path.getNameCount() < 2 || entry.length() > 512) throw new IllegalStateException("Unsafe default path: " + entry);
        for (Path part : path) if (part.toString().equals(".") || part.toString().equals("..")) throw new IllegalStateException("Unsafe default path: " + entry);
        if (!entry.endsWith(".yml")) throw new IllegalStateException("Unsupported default resource: " + entry);
    }
}
