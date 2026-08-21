package vn.svframe.lively.dialogue;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Lightweight offline intent/entity parser. No external language model required. */
public final class NluEngine {
    public enum Intent { ASSERT_INFORMATION, ASK_INFORMATION, OFFER_HELP, CHALLENGE, TRADE, GREETING, GOODBYE, UNKNOWN }
    public record Meaning(Intent intent, Map<String, String> slots, double confidence) {
        public Meaning { slots = Map.copyOf(slots); }
    }

    private static final Pattern POKEMON = Pattern.compile("(?i)\\b(mareep|pikachu|lucario|magikarp|eevee)\\b");
    private static final Pattern LOCATION = Pattern.compile("(?i)\\b(silverwoods|forest|rừng|cave|hang|market|chợ)\\b");

    public Meaning parse(String raw) {
        String text = raw == null ? "" : raw.trim();
        String s = text.toLowerCase(Locale.ROOT);
        Map<String, String> slots = new HashMap<>();
        capture(POKEMON, text).ifPresent(v -> slots.put("subject", v));
        capture(LOCATION, text).ifPresent(v -> slots.put("location", v));

        if (s.matches(".*(tôi thấy|mình thấy|i saw|i found).*")) return new Meaning(Intent.ASSERT_INFORMATION, slots, 0.84D);
        if (s.contains("ở đâu") || s.contains("where") || s.contains("biết gì")) return new Meaning(Intent.ASK_INFORMATION, slots, 0.79D);
        if (s.contains("giúp") || s.contains("help")) return new Meaning(Intent.OFFER_HELP, slots, 0.75D);
        if (s.contains("đấu") || s.contains("battle") || s.contains("challenge")) return new Meaning(Intent.CHALLENGE, slots, 0.78D);
        if (s.contains("mua") || s.contains("bán") || s.contains("trade") || s.contains("shop")) return new Meaning(Intent.TRADE, slots, 0.77D);
        if (s.matches(".*(xin chào|chào|hello|hi).*")) return new Meaning(Intent.GREETING, slots, 0.82D);
        if (s.matches(".*(tạm biệt|bye|goodbye).*")) return new Meaning(Intent.GOODBYE, slots, 0.90D);
        slots.put("text", text);
        return new Meaning(Intent.UNKNOWN, slots, 0.20D);
    }

    private static java.util.Optional<String> capture(Pattern pattern, String input) {
        Matcher matcher = pattern.matcher(input);
        return matcher.find() ? java.util.Optional.of(matcher.group(1)) : java.util.Optional.empty();
    }
}
