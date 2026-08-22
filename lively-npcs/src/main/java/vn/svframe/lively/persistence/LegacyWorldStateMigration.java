package vn.svframe.lively.persistence;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

/**
 * One-time compatibility import from pre world-scoped alpha layouts.
 * Existing world-local state always wins; legacy files are copied, never deleted.
 */
public final class LegacyWorldStateMigration {
    private static final long MAX_SINGLE_FILE = 128L * 1024L * 1024L;
    private static final int MAX_FILES = 200_000;

    private LegacyWorldStateMigration() {}

    public static boolean importIfNeeded(Path legacyRoot, Path worldRoot) {
        Path marker = worldRoot.resolve(".legacy-config-imported");
        if (Files.exists(marker) || hasWorldState(worldRoot) || !Files.isDirectory(legacyRoot)) return false;
        try {
            Files.createDirectories(worldRoot);
            int[] count = {0};
            copyFile(legacyRoot.resolve("npcs").resolve("npcs.tsv"), worldRoot.resolve("npcs").resolve("npcs.tsv"), count);
            copyFile(legacyRoot.resolve("npcs").resolve("npcs.tsv.bak"), worldRoot.resolve("npcs").resolve("npcs.tsv.bak"), count);
            copyFile(legacyRoot.resolve("state").resolve("simulation.json"), worldRoot.resolve("state").resolve("simulation.json"), count);
            copyFile(legacyRoot.resolve("state").resolve("simulation.json.bak"), worldRoot.resolve("state").resolve("simulation.json.bak"), count);
            copyNpcStates(legacyRoot.resolve("state"), worldRoot.resolve("state").resolve("npcs"), count);
            copyDirectory(legacyRoot.resolve("history"), worldRoot.resolve("history"), count);
            Files.writeString(marker, "Imported legacy config-scoped Lively state. Source retained at: " + legacyRoot.toAbsolutePath() + "\n",
                    StandardCharsets.UTF_8);
            return count[0] > 0;
        } catch (IOException error) {
            throw new IllegalStateException("failed to import legacy Lively state", error);
        }
    }

    private static boolean hasWorldState(Path root) {
        return Files.exists(root.resolve("npcs").resolve("npcs.tsv"))
                || Files.exists(root.resolve("state").resolve("simulation.json"))
                || Files.isDirectory(root.resolve("state").resolve("npcs"));
    }

    private static void copyNpcStates(Path source, Path target, int[] count) throws IOException {
        if (!Files.isDirectory(source)) return;
        try (Stream<Path> files = Files.list(source)) {
            for (Path file : files.filter(Files::isRegularFile).filter(path -> path.getFileName().toString().endsWith(".lnpc")).toList()) {
                copyFile(file, target.resolve(file.getFileName()), count);
            }
        }
    }

    private static void copyDirectory(Path source, Path target, int[] count) throws IOException {
        if (!Files.isDirectory(source)) return;
        try (Stream<Path> files = Files.walk(source)) {
            for (Path path : files.filter(Files::isRegularFile).toList()) {
                Path relative = source.relativize(path);
                if (relative.getNameCount() > 8) throw new IOException("legacy history path too deep");
                copyFile(path, target.resolve(relative), count);
            }
        }
    }

    private static void copyFile(Path source, Path target, int[] count) throws IOException {
        if (!Files.isRegularFile(source) || Files.exists(target)) return;
        if (++count[0] > MAX_FILES) throw new IOException("legacy state file limit exceeded");
        long size = Files.size(source);
        if (size < 0L || size > MAX_SINGLE_FILE) throw new IOException("legacy state file too large: " + source.getFileName());
        Files.createDirectories(target.getParent());
        Path temp = target.resolveSibling(target.getFileName() + ".importing");
        Files.copy(source, temp, StandardCopyOption.REPLACE_EXISTING);
        try { Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE); }
        catch (IOException ignored) { Files.move(temp, target); }
    }
}
