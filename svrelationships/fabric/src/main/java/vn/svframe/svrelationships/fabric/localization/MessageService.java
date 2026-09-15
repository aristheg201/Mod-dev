package vn.svframe.svrelationships.fabric.localization;

import net.minecraft.text.Text;
import vn.svframe.svrelationships.fabric.config.ConfigService;

import java.util.Map;
import java.util.Objects;

public final class MessageService {
    private final ConfigService config;

    public MessageService(ConfigService config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    public Text text(String key) {
        return text(key, Map.of());
    }

    public Text text(String key, Map<String, ?> placeholders) {
        String template = config.snapshot().messages().getOrDefault(key, "<missing:" + key + ">");
        String rendered = template;
        for (Map.Entry<String, ?> entry : placeholders.entrySet()) {
            rendered = rendered.replace("<" + entry.getKey() + ">", String.valueOf(entry.getValue()));
        }
        return Text.literal(rendered);
    }
}
