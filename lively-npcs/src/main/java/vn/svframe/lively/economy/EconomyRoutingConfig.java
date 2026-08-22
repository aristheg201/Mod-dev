package vn.svframe.lively.economy;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/** Human-editable external currency routing. Unknown currencies remain auto-routed by provider capability. */
public final class EconomyRoutingConfig {
    private EconomyRoutingConfig() {}

    public static Map<String, String> load(Path file) {
        try {
            if (!Files.isRegularFile(file)) writeDefaults(file);
            Properties properties = new Properties();
            try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { properties.load(reader); }
            LinkedHashMap<String, String> routes = new LinkedHashMap<>();
            for (String key : properties.stringPropertyNames()) {
                if (!key.startsWith("route.")) continue;
                String currency = key.substring("route.".length()).trim().toLowerCase(java.util.Locale.ROOT);
                String provider = properties.getProperty(key, "auto").trim().toLowerCase(java.util.Locale.ROOT);
                if (!currency.isBlank() && !provider.isBlank()) routes.put(currency, provider);
            }
            return Map.copyOf(routes);
        } catch (IOException error) {
            throw new IllegalStateException("failed to load Lively economy routing config", error);
        }
    }

    private static void writeDefaults(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        Properties defaults = new Properties();
        defaults.setProperty("route.cobbledollar", "cobbledollars");
        defaults.setProperty("route.cobbledollars", "cobbledollars");
        defaults.setProperty("route.cd", "cobbledollars");
        defaults.setProperty("route.beastcoin", "auto");
        defaults.setProperty("route.huntercoin", "auto");
        StringWriter writer = new StringWriter();
        defaults.store(writer, "Lively 1.0.1 economy routes. Providers: beconomy, cobbledollars, impactor, or auto.");
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temp, writer.toString(), StandardCharsets.UTF_8);
        try { Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE); }
        catch (IOException ignored) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
    }
}
