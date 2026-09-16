package vn.svframe.svrelationships.fabric.localization;

import net.kyori.adventure.platform.fabric.FabricAudiences;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.minecraft.text.Text;
import vn.svframe.svrelationships.fabric.config.ConfigService;

import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

public final class MessageService {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final Pattern PLACEHOLDER_NAME = Pattern.compile("[a-z0-9_-]+");

    private final ConfigService config;

    public MessageService(ConfigService config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    public Text text(String key) {
        return text(key, Map.of());
    }

    public Text text(String key, Map<String, ?> placeholders) {
        String template = resolveTemplate(config.snapshot().messages(), key);
        TagResolver.Builder resolver = TagResolver.builder();
        for (Map.Entry<String, ?> entry : placeholders.entrySet()) {
            String name = entry.getKey();
            if (name == null || !PLACEHOLDER_NAME.matcher(name).matches()) {
                continue;
            }
            resolver.resolver(Placeholder.unparsed(name, String.valueOf(entry.getValue())));
        }

        try {
            Component component = MINI_MESSAGE.deserialize(template, resolver.build());
            return FabricAudiences.nonWrappingSerializer().serialize(component);
        } catch (RuntimeException malformedMiniMessage) {
            String fallback = template;
            for (Map.Entry<String, ?> entry : placeholders.entrySet()) {
                fallback = fallback.replace("<" + entry.getKey() + ">", String.valueOf(entry.getValue()));
            }
            return Text.literal(fallback);
        }
    }

    static String resolveTemplate(Map<String, String> messages, String key) {
        String direct = messages.get(key);
        if (direct != null) return direct;

        // Compatibility for pre-v4 GUI configs which used ui.* while the
        // canonical localization namespace is gui.*. Keep both directions so
        // custom packs can be migrated without producing red missing-key UI.
        if (key.startsWith("ui.")) {
            String canonical = messages.get("gui." + key.substring(3));
            if (canonical != null) return canonical;
        } else if (key.startsWith("gui.")) {
            String legacy = messages.get("ui." + key.substring(4));
            if (legacy != null) return legacy;
        }

        return "<red>Missing localization: " + key + "</red>";
    }
}
