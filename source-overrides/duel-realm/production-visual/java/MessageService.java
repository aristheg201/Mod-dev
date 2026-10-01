package vn.svarcade.tcg.fabric;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.platform.fabric.FabricServerAudiences;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

/** Config-driven Kyori MiniMessage output with optional Text Placeholder API parsing. */
public final class MessageService {
    private static final Logger LOG = LoggerFactory.getLogger("cardworlds-messages");
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final Gson JSON = new Gson();

    private final MinecraftServer server;
    private final Path file;
    private volatile Map<String,String> messages = Map.of();

    public MessageService(MinecraftServer server) {
        this.server = server;
        this.file = FabricLoader.getInstance().getConfigDir().resolve("svarcade_tcg/messages.json");
        try {
            ensureFile();
            reload();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    public synchronized void reload() throws Exception {
        ensureFile();
        Map<String,String> loaded = JSON.fromJson(Files.readString(file),
            new TypeToken<LinkedHashMap<String,String>>(){}.getType());
        if (loaded == null) loaded = Map.of();
        this.messages = Map.copyOf(loaded);
    }

    public void send(ServerCommandSource source, String key) {
        send(source, key, Map.of());
    }

    public void send(ServerCommandSource source, String key, Map<String,String> values) {
        String raw = messages.get(key);
        if (raw == null) {
            LOG.warn("Missing Card Worlds message key {}", key);
            return;
        }

        TagResolver.Builder tags = TagResolver.builder();
        values.forEach((name, value) ->
            tags.resolver(Placeholder.unparsed(name, value == null ? "" : value)));

        Component body = vn.svarcade.tcg.integration.PlaceholderComponents.resolve(parseMini(raw,tags.build()),keyName->vn.svarcade.tcg.integration.PlaceholderBridge.value(source,keyName));
        String prefixRaw = messages.getOrDefault("prefix", "");
        Component prefix = prefixRaw.isBlank()
            ? Component.empty()
            : vn.svarcade.tcg.integration.PlaceholderComponents.resolve(parseMini(prefixRaw,tags.build()),keyName->vn.svarcade.tcg.integration.PlaceholderBridge.value(source,keyName));

        Audience audience = FabricServerAudiences.of(server).audience(source);
        audience.sendMessage(prefix.append(body));
    }

    private Component parseMini(String input, TagResolver resolver) {
        try {
            return MINI.deserialize(input, resolver);
        } catch (RuntimeException ex) {
            LOG.warn("Invalid MiniMessage in Card Worlds messages config", ex);
            return Component.text(input);
        }
    }

    private void ensureFile() throws Exception {
        Files.createDirectories(file.getParent());
        if (Files.exists(file)) return;
        try (var in = MessageService.class.getResourceAsStream("/data/svarcade_tcg/messages.json")) {
            if (in == null) throw new IllegalStateException("Missing bundled messages resource");
            Files.copy(in, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
