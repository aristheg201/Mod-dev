package vn.svframe.lively.skin;

import java.net.URI;
import java.util.Locale;

/** Parses persisted skin references while keeping old plain Mojang usernames compatible. */
public record SkinSource(Kind kind, String value, String signature) {
    public enum Kind { DEFAULT, MOJANG, URL, TEXTURE, MINESKIN }

    public SkinSource {
        value = value == null ? "" : value.trim();
        signature = signature == null ? "" : signature.trim();
    }

    public static SkinSource parse(String raw) {
        if (raw == null || raw.isBlank() || raw.equalsIgnoreCase("default")) return new SkinSource(Kind.DEFAULT, "", "");
        String text = raw.trim();
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.startsWith("mojang:")) return new SkinSource(Kind.MOJANG, text.substring(7).trim(), "");
        if (lower.startsWith("url:")) return new SkinSource(Kind.URL, text.substring(4).trim(), "");
        if (lower.startsWith("mineskin:")) return new SkinSource(Kind.MINESKIN, text.substring(9).trim(), "");
        if (lower.startsWith("texture:")) {
            String payload = text.substring(8).trim();
            int split = payload.indexOf('|');
            return split < 0 ? new SkinSource(Kind.TEXTURE, payload, "")
                    : new SkinSource(Kind.TEXTURE, payload.substring(0, split), payload.substring(split + 1));
        }
        try {
            URI uri = URI.create(text);
            if (uri.getScheme() != null && (uri.getScheme().equalsIgnoreCase("https") || uri.getScheme().equalsIgnoreCase("http"))) {
                return new SkinSource(Kind.URL, text, "");
            }
        } catch (IllegalArgumentException ignored) {}
        return new SkinSource(Kind.MOJANG, text, "");
    }
}
