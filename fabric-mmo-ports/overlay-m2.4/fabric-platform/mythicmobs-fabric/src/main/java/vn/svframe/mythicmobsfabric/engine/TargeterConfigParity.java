package vn.svframe.mythicmobsfabric.engine;

import java.util.Map;

/**
 * Presents the targeter configuration block to targeter implementations.
 *
 * Legacy MythicMobs parses mechanic and targeter configs independently. The M2.1
 * runtime originally retained the full @targeter{...} token but passed the
 * mechanic config map into Targeter.select(), causing radius/limit/sort/ignore
 * and every other targeter option to be silently ignored. This view restores the
 * original separation without changing the serialized SkillLine format.
 */
public final class TargeterConfigParity {
    private TargeterConfigParity() {}

    public static SkillLine view(SkillLine line) {
        if (line == null) return null;
        String token = line.targeter();
        if (token == null || token.isBlank()) return line;
        int open = token.indexOf('{');
        if (open < 0) return new SkillLine(line.mechanic(), Map.of(), token, line.trigger(), line.chance(), line.inlineConditions(), line.raw());
        int close = token.lastIndexOf('}');
        if (close <= open) return new SkillLine(line.mechanic(), Map.of(), token, line.trigger(), line.chance(), line.inlineConditions(), line.raw());
        Map<String, String> targeterConfig = SkillLine.parseConfig(token.substring(open + 1, close));
        return new SkillLine(line.mechanic(), targeterConfig, token, line.trigger(), line.chance(), line.inlineConditions(), line.raw());
    }

    public static String name(SkillLine line) {
        if (line == null || line.targeter() == null || line.targeter().isBlank()) return "self";
        String token = line.targeter().trim();
        int open = token.indexOf('{');
        return SkillLine.normalize(open < 0 ? token : token.substring(0, open));
    }
}
