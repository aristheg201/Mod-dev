package vn.svframe.mythicmobsfabric.engine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public record SkillLine(String mechanic, Map<String, String> config, String targeter,
                        String trigger, double chance, List<String> inlineConditions, String raw) {
    public static SkillLine parse(String raw) {
        if (raw == null) throw new IllegalArgumentException("skill line is null");
        String line = raw.trim();
        if (line.startsWith("-")) line = line.substring(1).trim();
        if (line.isEmpty()) throw new IllegalArgumentException("skill line is empty");

        List<String> tokens = tokenize(line);
        if (tokens.isEmpty()) throw new IllegalArgumentException("skill line is empty");
        String head = tokens.getFirst();
        String mechanic = head;
        Map<String, String> config = new LinkedHashMap<>();
        int open = head.indexOf('{');
        if (open >= 0) {
            int close = head.lastIndexOf('}');
            if (close < open) throw new IllegalArgumentException("unclosed mechanic config: " + raw);
            mechanic = head.substring(0, open);
            config.putAll(parseConfig(head.substring(open + 1, close)));
        }
        mechanic = normalize(mechanic);

        String targeter = "self";
        String trigger = "";
        double chance = 1.0;
        List<String> conditions = new ArrayList<>();
        for (int i = 1; i < tokens.size(); i++) {
            String token = tokens.get(i);
            if (token.startsWith("@")) targeter = token.substring(1);
            else if (token.startsWith("~")) trigger = normalize(token.substring(1));
            else if (token.startsWith("?")) conditions.add(token.substring(1));
            else if (token.startsWith("#")) continue;
            else {
                try { chance = Double.parseDouble(token); }
                catch (NumberFormatException ignored) { conditions.add(token); }
            }
        }
        return new SkillLine(mechanic, Map.copyOf(config), targeter, trigger, clampChance(chance), List.copyOf(conditions), raw);
    }

    public String string(String key, String fallback, String... aliases) {
        String value = lookup(key, aliases);
        return value == null ? fallback : value;
    }
    public int integer(String key, int fallback, String... aliases) {
        try { return Integer.parseInt(string(key, Integer.toString(fallback), aliases)); }
        catch (NumberFormatException ignored) { return fallback; }
    }
    public long longValue(String key, long fallback, String... aliases) {
        try { return Long.parseLong(string(key, Long.toString(fallback), aliases)); }
        catch (NumberFormatException ignored) { return fallback; }
    }
    public double decimal(String key, double fallback, String... aliases) {
        try { return Double.parseDouble(string(key, Double.toString(fallback), aliases)); }
        catch (NumberFormatException ignored) { return fallback; }
    }
    public boolean bool(String key, boolean fallback, String... aliases) {
        String value = lookup(key, aliases);
        if (value == null) return fallback;
        return switch (normalize(value)) { case "true", "yes", "y", "1", "on" -> true; case "false", "no", "n", "0", "off" -> false; default -> fallback; };
    }

    private String lookup(String key, String... aliases) {
        String value = config.get(normalize(key));
        if (value != null) return value;
        for (String alias : aliases) if ((value = config.get(normalize(alias))) != null) return value;
        return null;
    }

    public static Map<String, String> parseConfig(String raw) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String token : splitConfig(raw)) {
            String part = token.trim();
            if (part.isEmpty()) continue;
            int eq = indexOutsideQuotes(part, '=');
            if (eq < 0) out.put(normalize(part), "true");
            else out.put(normalize(part.substring(0, eq)), unquote(part.substring(eq + 1).trim()));
        }
        return out;
    }

    private static List<String> tokenize(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int braces = 0;
        char quote = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quote != 0) {
                current.append(c);
                if (c == quote && (i == 0 || line.charAt(i - 1) != '\\')) quote = 0;
                continue;
            }
            if (c == '\'' || c == '"') { quote = c; current.append(c); continue; }
            if (c == '{') braces++;
            if (c == '}') braces = Math.max(0, braces - 1);
            if (Character.isWhitespace(c) && braces == 0) {
                if (!current.isEmpty()) { out.add(current.toString()); current.setLength(0); }
            } else current.append(c);
        }
        if (!current.isEmpty()) out.add(current.toString());
        return out;
    }

    private static List<String> splitConfig(String value) {
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        char quote = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (quote != 0) {
                current.append(c);
                if (c == quote && (i == 0 || value.charAt(i - 1) != '\\')) quote = 0;
                continue;
            }
            if (c == '\'' || c == '"') { quote = c; current.append(c); continue; }
            if (c == '[' || c == '(' || c == '{') depth++;
            if (c == ']' || c == ')' || c == '}') depth = Math.max(0, depth - 1);
            if ((c == ';' || c == ',') && depth == 0) { out.add(current.toString()); current.setLength(0); }
            else current.append(c);
        }
        out.add(current.toString());
        return out;
    }

    private static int indexOutsideQuotes(String value, char needle) {
        char quote = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (quote != 0) { if (c == quote && (i == 0 || value.charAt(i - 1) != '\\')) quote = 0; continue; }
            if (c == '\'' || c == '"') { quote = c; continue; }
            if (c == needle) return i;
        }
        return -1;
    }

    private static String unquote(String value) {
        if (value.length() >= 2 && ((value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"') ||
                (value.charAt(0) == '\'' && value.charAt(value.length() - 1) == '\''))) return value.substring(1, value.length() - 1);
        return value;
    }
    private static double clampChance(double value) { return Math.max(0.0, Math.min(1.0, value)); }
    public static String normalize(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace("_", "").replace("-", ""); }
}
