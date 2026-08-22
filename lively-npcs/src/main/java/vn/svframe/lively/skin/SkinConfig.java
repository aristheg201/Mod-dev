package vn.svframe.lively.skin;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;

/** Global skin-network policy. Secrets stay in config and are never serialized into NPC definitions. */
public record SkinConfig(
        Path cacheDirectory,
        Duration cacheTtl,
        Duration connectTimeout,
        Duration requestTimeout,
        int maxPageBytes,
        int maxPngBytes,
        boolean allowHttp,
        boolean allowUnlistedHosts,
        Set<String> allowedHosts,
        boolean mineSkinEnabled,
        String mineSkinApiKey,
        String userAgent
) {
    public static SkinConfig load(Path configDirectory) {
        Path file = configDirectory.resolve("skins.properties");
        Properties defaults = defaults();
        Properties properties = new Properties();
        properties.putAll(defaults);
        try {
            Files.createDirectories(configDirectory);
            if (Files.isRegularFile(file)) {
                try (Reader reader = Files.newBufferedReader(file)) { properties.load(reader); }
            } else {
                try (Writer writer = Files.newBufferedWriter(file)) {
                    defaults.store(writer, "Lively NPC skin resolver. MineSkin API key is optional but required for arbitrary PNG/page URLs to become Mojang-signed player textures.");
                }
            }
        } catch (IOException error) {
            throw new IllegalStateException("failed to load skin configuration", error);
        }
        Set<String> allowed = new LinkedHashSet<>();
        Arrays.stream(properties.getProperty("allowed_hosts", "").split(","))
                .map(String::trim).filter(v -> !v.isEmpty()).forEach(allowed::add);
        return new SkinConfig(
                configDirectory.resolve("skin-cache"),
                Duration.ofHours(longValue(properties, "cache_ttl_hours", 168L, 1L, 24L * 365L)),
                Duration.ofMillis(longValue(properties, "connect_timeout_ms", 5000L, 500L, 60000L)),
                Duration.ofMillis(longValue(properties, "request_timeout_ms", 15000L, 1000L, 120000L)),
                intValue(properties, "max_page_bytes", 2_097_152, 65536, 8_388_608),
                intValue(properties, "max_png_bytes", 1_048_576, 65536, 4_194_304),
                Boolean.parseBoolean(properties.getProperty("allow_http", "false")),
                Boolean.parseBoolean(properties.getProperty("allow_unlisted_hosts", "false")),
                Set.copyOf(allowed),
                Boolean.parseBoolean(properties.getProperty("mineskin.enabled", "true")),
                properties.getProperty("mineskin.api_key", "").trim(),
                properties.getProperty("user_agent", "LivelyNPCs/0.4 (SVFrame Studio)").trim()
        );
    }

    public boolean hostAllowed(String host) {
        if (allowUnlistedHosts) return true;
        if (host == null) return false;
        String lower = host.toLowerCase(java.util.Locale.ROOT);
        for (String rule : allowedHosts) {
            String normalized = rule.toLowerCase(java.util.Locale.ROOT);
            if (normalized.startsWith("*.")) {
                String suffix = normalized.substring(1);
                if (lower.endsWith(suffix) || lower.equals(normalized.substring(2))) return true;
            } else if (lower.equals(normalized)) return true;
        }
        return false;
    }

    private static Properties defaults() {
        Properties p = new Properties();
        p.setProperty("cache_ttl_hours", "168");
        p.setProperty("connect_timeout_ms", "5000");
        p.setProperty("request_timeout_ms", "15000");
        p.setProperty("max_page_bytes", "2097152");
        p.setProperty("max_png_bytes", "1048576");
        p.setProperty("allow_http", "false");
        p.setProperty("allow_unlisted_hosts", "false");
        p.setProperty("allowed_hosts", "textures.minecraft.net,namemc.com,*.namemc.com,minecraftskins.com,*.minecraftskins.com,skinsmc.org,*.skinsmc.org,planetminecraft.com,*.planetminecraft.com,mineskin.org,*.mineskin.org");
        p.setProperty("mineskin.enabled", "true");
        p.setProperty("mineskin.api_key", "");
        p.setProperty("user_agent", "LivelyNPCs/0.4 (SVFrame Studio)");
        return p;
    }

    private static long longValue(Properties p, String key, long fallback, long min, long max) {
        try { return Math.max(min, Math.min(max, Long.parseLong(p.getProperty(key, Long.toString(fallback))))); }
        catch (NumberFormatException ignored) { return fallback; }
    }
    private static int intValue(Properties p, String key, int fallback, int min, int max) {
        return (int) longValue(p, key, fallback, min, max);
    }
}
