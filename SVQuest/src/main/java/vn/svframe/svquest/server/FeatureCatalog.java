package vn.svframe.svquest.server;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import vn.svframe.svquest.SVQuest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Editable feature-id -> command routing loaded from config/svquest/features.json. */
public final class FeatureCatalog {
    private static final String DEFAULT_RESOURCE = "/svquest/defaults/features.json";
    private FeatureCatalog() {}

    public static volatile Map<String, String> COMMANDS = Map.of();

    static {
        try { COMMANDS = Collections.unmodifiableMap(parse(readResourceStrict())); }
        catch (Throwable t) { throw new ExceptionInInitializerError(t); }
    }

    public static synchronized int reloadFromConfig() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("svquest/features.json");
        try {
            Files.createDirectories(file.getParent());
            if (!Files.isRegularFile(file)) {
                try (InputStream in = FeatureCatalog.class.getResourceAsStream(DEFAULT_RESOURCE)) {
                    if (in == null) throw new IOException("Missing " + DEFAULT_RESOURCE);
                    Files.copy(in, file, StandardCopyOption.REPLACE_EXISTING);
                }
                SVQuest.LOGGER.info("Created editable SVQuest feature config: {}", file);
            }
            Map<String, String> next = Collections.unmodifiableMap(parse(readStrict(Files.readAllBytes(file), file.toString())));
            COMMANDS = next;
            SVQuest.LOGGER.info("SVQuest feature config loaded: {} command routes.", next.size());
            return next.size();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to reload SVQuest feature config", e);
        }
    }

    private static LinkedHashMap<String, String> parse(String text) {
        JsonElement parsed = JsonParser.parseString(text);
        JsonObject root = parsed.getAsJsonObject();
        JsonObject commands = root.has("commands") ? root.getAsJsonObject("commands") : root;
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        for (var entry : commands.entrySet()) {
            if (entry.getValue().isJsonNull()) continue;
            String id = entry.getKey().trim();
            String command = entry.getValue().getAsString().trim();
            if (!id.isEmpty() && !command.isEmpty()) out.put(id, command);
        }
        return out;
    }

    private static String readResourceStrict() throws IOException {
        try (InputStream in = FeatureCatalog.class.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (in == null) throw new IOException("Missing " + DEFAULT_RESOURCE);
            return readStrict(in.readAllBytes(), DEFAULT_RESOURCE);
        }
    }

    private static String readStrict(byte[] bytes, String source) throws IOException {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        } catch (java.nio.charset.CharacterCodingException e) {
            throw new IOException("Invalid UTF-8 in " + source, e);
        }
    }
}
