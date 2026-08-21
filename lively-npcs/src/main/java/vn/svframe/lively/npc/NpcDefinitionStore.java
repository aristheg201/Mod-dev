package vn.svframe.lively.npc;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Small crash-safe definition store. NPC cognition remains in NpcStateStore. */
public final class NpcDefinitionStore {
    private static final int FORMAT = 1;
    private final Path file;

    public NpcDefinitionStore(Path file) { this.file = file; }

    public Map<UUID, NpcDefinition> loadAll() {
        Map<UUID, NpcDefinition> result = new HashMap<>();
        if (!Files.isRegularFile(file)) return result;
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String first = reader.readLine();
            if (!Integer.toString(FORMAT).equals(first)) throw new IllegalStateException("unsupported npc definition format");
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] p = line.split("\\t", -1);
                if (p.length < 18) continue;
                UUID id = UUID.fromString(p[0]);
                NpcDefinition definition = new NpcDefinition(id, unescape(p[1]), unescape(p[2]), NpcDefinition.BodyType.valueOf(p[3]),
                        unescape(p[4]), unescape(p[5]), unescape(p[6]), Double.parseDouble(p[7]), Double.parseDouble(p[8]), Double.parseDouble(p[9]),
                        Float.parseFloat(p[10]), Float.parseFloat(p[11]), Boolean.parseBoolean(p[12]), Boolean.parseBoolean(p[13]),
                        Boolean.parseBoolean(p[14]), Boolean.parseBoolean(p[15]), Boolean.parseBoolean(p[16]), Boolean.parseBoolean(p[17]), Map.of());
                result.put(id, definition);
            }
        } catch (Exception error) {
            throw new IllegalStateException("failed to load npc definitions", error);
        }
        return result;
    }

    public synchronized void saveAll(Map<UUID, NpcDefinition> definitions) {
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            try (BufferedWriter writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                writer.write(Integer.toString(FORMAT)); writer.newLine();
                for (NpcDefinition d : definitions.values().stream().sorted(java.util.Comparator.comparing(v -> v.id().toString())).toList()) {
                    writer.write(String.join("\t", d.id().toString(), escape(d.name()), escape(d.role()), d.bodyType().name(), escape(d.bodyKey()),
                            escape(d.skinName()), escape(d.world()), Double.toString(d.x()), Double.toString(d.y()), Double.toString(d.z()),
                            Float.toString(d.yaw()), Float.toString(d.pitch()), Boolean.toString(d.spawned()), Boolean.toString(d.aiEnabled()),
                            Boolean.toString(d.invulnerable()), Boolean.toString(d.gravity()), Boolean.toString(d.silent()), Boolean.toString(d.nameVisible())));
                    writer.newLine();
                }
            }
            try { Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (IOException ignored) { Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING); }
        } catch (IOException error) {
            throw new IllegalStateException("failed to save npc definitions", error);
        }
    }

    public void delete(UUID id) {
        Map<UUID, NpcDefinition> all = loadAll(); all.remove(id); saveAll(all);
    }

    private static String escape(String value) { return value.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n"); }
    private static String unescape(String value) { return value.replace("\\n", "\n").replace("\\t", "\t").replace("\\\\", "\\"); }
}
