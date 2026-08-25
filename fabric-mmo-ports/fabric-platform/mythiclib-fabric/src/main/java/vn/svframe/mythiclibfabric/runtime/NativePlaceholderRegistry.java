package vn.svframe.mythiclibfabric.runtime;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Native MythicLib placeholder surface used by scripts, commands and integrations. */
public final class NativePlaceholderRegistry {
    private static final Pattern ANGLE = Pattern.compile("<([^<>]+)>");
    private static final Map<String, BiFunction<UUID, String, String>> PROVIDERS = new ConcurrentHashMap<>();

    static {
        register("stat", (player, argument) -> format(StatProviderRegistry.stat(player, normalize(argument))));
        register("player", (player, argument) -> {
            if ("uuid".equalsIgnoreCase(argument) || "id".equalsIgnoreCase(argument)) return player == null ? "" : player.toString();
            return "";
        });
    }

    private NativePlaceholderRegistry() { }

    public static void register(String namespace, BiFunction<UUID, String, String> provider) {
        if (namespace == null || namespace.isBlank()) throw new IllegalArgumentException("namespace must not be blank");
        PROVIDERS.put(namespace.trim().toLowerCase(Locale.ROOT), java.util.Objects.requireNonNull(provider, "provider"));
    }

    public static void unregister(String namespace) {
        if (namespace != null) PROVIDERS.remove(namespace.trim().toLowerCase(Locale.ROOT));
    }

    public static String resolve(UUID player, String placeholder) {
        if (placeholder == null) return "";
        String key = placeholder.trim();
        int split = key.indexOf('.');
        if (split < 0) split = key.indexOf(':');
        String namespace = split < 0 ? key : key.substring(0, split);
        String argument = split < 0 ? "" : key.substring(split + 1);
        BiFunction<UUID, String, String> provider = PROVIDERS.get(namespace.toLowerCase(Locale.ROOT));
        if (provider == null) return '<' + placeholder + '>';
        String value = provider.apply(player, argument);
        return value == null ? "" : value;
    }

    public static String parse(UUID player, String input) {
        if (input == null || input.isEmpty()) return input == null ? "" : input;
        Matcher matcher = ANGLE.matcher(input);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) matcher.appendReplacement(output, Matcher.quoteReplacement(resolve(player, matcher.group(1))));
        matcher.appendTail(output);
        return output.toString();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    private static String format(double value) {
        if (value == Math.rint(value)) return Long.toString((long) value);
        return Double.toString(value);
    }
}
