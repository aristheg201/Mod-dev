package vn.svframe.lively.economy;

import java.util.Locale;

/**
 * Centralizes visibility/access rules for ordinary and hidden businesses.
 * This is semantic authorization only. It never moves currency or inventory.
 */
public final class BusinessAccessPolicy {
    public record Decision(boolean visible, boolean allowed, double requiredTrust, String reason) {}

    private BusinessAccessPolicy() {}

    public static Decision evaluate(EconomyEngine.Business business, double relationshipTrust, double reputation) {
        if (business == null) return new Decision(false, false, 1D, "missing_business");
        boolean hidden = Boolean.parseBoolean(business.facts().getOrDefault("hidden", "false"));
        boolean illegal = Boolean.parseBoolean(business.facts().getOrDefault("illegal", "false"));
        double required = number(business.facts().get("access_trust"), hidden || illegal ? .35D : 0D, 0D, 1D);
        double standing = Math.max(clampSigned(relationshipTrust), clampSigned(reputation));

        if ((hidden || illegal) && standing < required) {
            return new Decision(false, false, required, "insufficient_trust");
        }
        if (!business.open()) return new Decision(true, false, required, "closed");
        return new Decision(true, true, required, "allowed");
    }

    public static boolean hidden(EconomyEngine.Business business) {
        if (business == null) return false;
        String kind = business.facts().getOrDefault("kind", "").trim().toLowerCase(Locale.ROOT);
        return Boolean.parseBoolean(business.facts().getOrDefault("hidden", "false"))
                || Boolean.parseBoolean(business.facts().getOrDefault("illegal", "false"))
                || kind.equals("black_market") || kind.equals("underworld");
    }

    private static double number(String raw, double fallback, double min, double max) {
        try { return Math.max(min, Math.min(max, Double.parseDouble(raw == null ? Double.toString(fallback) : raw.trim()))); }
        catch (NumberFormatException ignored) { return fallback; }
    }

    private static double clampSigned(double value) { return Math.max(-1D, Math.min(1D, value)); }
}
