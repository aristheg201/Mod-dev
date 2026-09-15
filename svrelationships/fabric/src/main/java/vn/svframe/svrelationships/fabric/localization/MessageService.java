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
        String template = config.snapshot().messages().getOrDefault(key, "<red>Missing localization: " + key + "</red>");
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
            // A bad admin-edited line must never crash a GUI/command path. Fall
            // back to literal text while still resolving known placeholders.
            String fallback = template;
            for (Map.Entry<String, ?> entry : placeholders.entrySet()) {
                fallback = fallback.replace("<" + entry.getKey() + ">", String.valueOf(entry.getValue()));
            }
            return Text.literal(fallback);
        }
    }
}
