package vn.svframe.svarcade.config;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CodingErrorAction;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.api.lowlevel.Parse;
import org.snakeyaml.engine.v2.schema.JsonSchema;

/** Bounded, safe YAML loading. Invoke off-thread; never reads arbitrary Java tags. */
public final class DefinitionLoader {
    private static final int MAX_FILE = 1_048_576;
    private static final int MAX_FILES = 256;
    private static final long MAX_PACKAGE = 8L * MAX_FILE;
    private final Registry<SystemSchema> systems;
    public DefinitionLoader(Registry<SystemSchema> systems) { this.systems = Objects.requireNonNull(systems); }

    public List<Definition> loadAll(Path directory) {
        try {
            if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) throw new ConfigException("Missing definition directory: " + directory);
            List<Path> folders;
            try (var files = Files.list(directory)) {
                folders = files.sorted().limit(257).toList();
            }
            if (folders.size() > 256) throw new ConfigException("Too many definition entries");
            List<Definition> result = new ArrayList<>();
            Set<Id> ids = new HashSet<>();
            for (Path path : folders) {
                if (Files.isSymbolicLink(path)) throw new ConfigException("Definition symlinks are not allowed: " + path);
                if (!Files.isDirectory(path)) continue;
                Definition d = load(path);
                if (!ids.add(d.id())) throw new ConfigException("Duplicate definition: " + d.id());
                result.add(d);
            }
            return List.copyOf(result);
        } catch (IOException e) { throw new ConfigException("Cannot enumerate definitions", e); }
    }

    public Definition load(Path folder) {
        try {
            PackageFiles files = new PackageFiles(folder);
            Node game = files.read("game.yml");
            game.only("schema", "id", "enabled", "players", "systems", "arenas", "requires");
            int schema = (int) game.integer("schema", 1, 1);
            Id id = Id.of(game.string("id"));
            Node players = game.node("players"); players.only("min", "max");
            int min = (int) players.integer("min", 1, 1024);
            int max = (int) players.integer("max", min, 1024);
            Set<String> integrations = Set.of();
            if (game.has("requires")) {
                Node requires = game.node("requires"); requires.only("integrations");
                integrations = requires.strings("integrations");
            }
            List<Definition.SystemSpec> specs = new ArrayList<>();
            Set<Id> selected = new HashSet<>();
            for (Node entry : game.nodes("systems")) {
                entry.only("id", "config");
                Id system = Id.of(entry.string("id"));
                if (!selected.add(system)) throw entry.error("id", "Duplicate system");
                Node config = files.read(entry.string("config"));
                systems.require(system).validate(config);
                specs.add(new Definition.SystemSpec(system, config));
            }
            if (specs.isEmpty()) throw game.error("systems", "At least one system is required");
            for (Id system : selected) {
                if (!selected.containsAll(systems.require(system).dependencies())) throw game.error("systems", "Missing dependency for " + system);
            }
            Node arenaConfig = game.node("arenas"); arenaConfig.only("directory");
            Path arenaDir = files.path(arenaConfig.string("directory"));
            if (!Files.isDirectory(arenaDir)) throw game.error("arenas", "Expected directory");
            Map<String, Node> arenas = new LinkedHashMap<>();
            try (var stream = Files.list(arenaDir)) {
                List<Path> paths = stream.sorted().limit(MAX_FILES + 1L).toList();
                if (paths.size() > MAX_FILES) throw game.error("arenas", "Too many arena entries");
                for (Path arena : paths) {
                    if (Files.isSymbolicLink(arena)) throw game.error("arenas", "Symlink is not allowed");
                    if (!arena.toString().endsWith(".yml")) continue;
                    Node value = files.read(files.root.relativize(arena).toString());
                    String arenaId = value.string("id");
                    if (!arenaId.matches("[a-z0-9_-]{1,80}")) throw value.error("id", "Invalid arena identifier");
                    if (arenas.putIfAbsent(arenaId, value) != null) throw value.error("id", "Duplicate arena identifier");
                }
            }
            return new Definition(schema, id, files.fingerprint(), game.bool("enabled", true), min, max, integrations, specs, arenas);
        } catch (IOException e) { throw new ConfigException("Cannot load " + folder, e); }
    }

    private static final class PackageFiles {
        private final Path root;
        private final Map<String, byte[]> contents = new TreeMap<>();
        private long bytesRead;
        PackageFiles(Path folder) throws IOException {
            if (Files.isSymbolicLink(folder)) throw new ConfigException("Definition folder may not be a symlink");
            root = folder.toRealPath();
        }
        Path path(String name) throws IOException {
            Path relative = Path.of(name);
            if (relative.isAbsolute() || name.contains("\\") || relative.getNameCount() == 0) throw new ConfigException("Unsafe definition path: " + name);
            for (Path part : relative) if (part.toString().equals("..") || part.toString().equals(".")) throw new ConfigException("Unsafe definition path: " + name);
            Path cursor = root;
            for (Path part : relative) {
                cursor = cursor.resolve(part);
                if (Files.isSymbolicLink(cursor)) throw new ConfigException("Definition symlink: " + name);
            }
            Path resolved = cursor.toRealPath();
            if (!resolved.startsWith(root)) throw new ConfigException("Definition path escapes package: " + name);
            return resolved;
        }
        Node read(String name) throws IOException {
            Path path = path(name);
            String key = root.relativize(path).toString();
            byte[] bytes = contents.get(key);
            if (bytes == null) {
                if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw new ConfigException("Expected regular file: " + name);
                try (var in = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) { bytes = in.readNBytes(MAX_FILE + 1); }
                if (bytes.length > MAX_FILE || contents.size() >= MAX_FILES || (bytesRead += bytes.length) > MAX_PACKAGE) throw new ConfigException("Definition size limit exceeded");
                contents.put(key, bytes);
            }
            LoadSettings settings = LoadSettings.builder().setLabel(path.toString())
                    .setSchema(new JsonSchema()).setAllowDuplicateKeys(false)
                    .setMaxAliasesForCollections(0).setCodePointLimit(MAX_FILE).build();
            try {
                String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                        .decode(java.nio.ByteBuffer.wrap(bytes)).toString();
                // Bound nesting before the composer's recursive object construction.
                int depth = 0, events = 0;
                for (var event : new Parse(settings).parseString(text)) {
                    if (++events > 100_000) throw new ConfigException("Too many YAML events");
                    switch (event.getEventId()) {
                        case MappingStart, SequenceStart -> { if (++depth > 48) throw new ConfigException("YAML nesting limit"); }
                        case MappingEnd, SequenceEnd -> depth--;
                        default -> { }
                    }
                }
                return new Node(new Load(settings).loadFromString(text), path.toString());
            } catch (RuntimeException e) { throw new ConfigException("Invalid YAML " + path + ": " + e.getMessage(), e); }
        }
        String fingerprint() {
            try {
                MessageDigest hash = MessageDigest.getInstance("SHA-256");
                contents.forEach((name, bytes) -> {
                    hash.update(name.replace('\\', '/').getBytes(StandardCharsets.UTF_8));
                    hash.update((byte) 0);
                    hash.update(Integer.toString(bytes.length).getBytes(StandardCharsets.US_ASCII));
                    hash.update((byte) 0); hash.update(bytes);
                });
                return HexFormat.of().formatHex(hash.digest());
            } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
        }
    }
}
