package vn.svframe.mmoitemsfabric;

import eu.pb4.placeholders.api.PlaceholderContext;
import eu.pb4.placeholders.api.Placeholders;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Native Text Placeholder API implementation of MMOItems' legacy placeholder crafting condition. */
public final class MMOItemsPlaceholderConditionBridge {
    private static final Logger LOG = Logger.getLogger("MMOItems-Fabric/PlaceholderCondition");
    private static final double EQUALITY_THRESHOLD = 1.0E-5;

    private MMOItemsPlaceholderConditionBridge() {}

    public static boolean test(ServerPlayerEntity player, String expression) {
        if (player == null || expression == null) return false;
        String[] split = expression.split("~", -1);
        if (split.length != 3) return false;
        try {
            String left = resolve(player, split[0]);
            String comparator = split[1];
            String right = resolve(player, split[2]);
            return switch (comparator) {
                case "<" -> Double.parseDouble(left) < Double.parseDouble(right);
                case "<=" -> Double.parseDouble(left) <= Double.parseDouble(right);
                case ">" -> Double.parseDouble(left) > Double.parseDouble(right);
                case ">=" -> Double.parseDouble(left) >= Double.parseDouble(right);
                case "==", "=" -> Math.abs(Double.parseDouble(left) - Double.parseDouble(right)) <= EQUALITY_THRESHOLD;
                case "!=" -> Math.abs(Double.parseDouble(left) - Double.parseDouble(right)) > EQUALITY_THRESHOLD;
                case "equals", "eq" -> left.equals(right);
                case "neq" -> !left.equals(right);
                default -> false;
            };
        } catch (RuntimeException exception) {
            LOG.log(Level.FINE, "Could not evaluate crafting placeholder condition: " + expression, exception);
            return false;
        }
    }

    public static String resolve(ServerPlayerEntity player, String input) {
        Text parsed = Placeholders.parseText(Text.literal(input), PlaceholderContext.of(player));
        String value = parsed.getString();
        if (!value.equals(input)) return value;

        // Drop-in compatibility for common PlaceholderAPI %namespace_key% spelling.
        String translated = translatePercentPlaceholders(input);
        if (translated.equals(input)) return value;
        return Placeholders.parseText(Text.literal(translated), PlaceholderContext.of(player)).getString();
    }

    private static String translatePercentPlaceholders(String input) {
        StringBuilder out = new StringBuilder(input.length());
        int cursor = 0;
        while (cursor < input.length()) {
            int start = input.indexOf('%', cursor);
            if (start < 0) { out.append(input, cursor, input.length()); break; }
            int end = input.indexOf('%', start + 1);
            if (end < 0) { out.append(input, cursor, input.length()); break; }
            out.append(input, cursor, start);
            String body = input.substring(start + 1, end);
            int underscore = body.indexOf('_');
            if (underscore > 0 && body.indexOf(':') < 0) {
                String namespace = body.substring(0, underscore).toLowerCase(Locale.ROOT);
                String key = body.substring(underscore + 1);
                out.append('%').append(namespace).append(':').append(key).append('%');
            } else out.append('%').append(body).append('%');
            cursor = end + 1;
        }
        return out.toString();
    }
}
