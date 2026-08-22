package vn.svframe.lively.law;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/** Startup-loaded policy for the semantic justice runtime. No tick-path file I/O. */
public record LawConfig(
        boolean enabled,
        long reviewPulseTicks,
        long officerPulseTicks,
        int warrantEvidenceCount,
        double warrantScore,
        double convictionScore,
        double acquitAlibiStrength,
        long warrantLifetimeSeconds,
        long hearingDelaySeconds,
        long baseFine,
        long bountyUnit,
        long baseJailSeconds,
        long maxJailSeconds,
        boolean trackPlayers,
        boolean physicallyJailNpcs
) {
    public LawConfig {
        reviewPulseTicks = bound(reviewPulseTicks, 20L, 72_000L);
        officerPulseTicks = bound(officerPulseTicks, 10L, 12_000L);
        warrantEvidenceCount = (int) bound(warrantEvidenceCount, 1L, 64L);
        warrantScore = unit(warrantScore);
        convictionScore = unit(convictionScore);
        acquitAlibiStrength = unit(acquitAlibiStrength);
        warrantLifetimeSeconds = bound(warrantLifetimeSeconds, 60L, 2_592_000L);
        hearingDelaySeconds = bound(hearingDelaySeconds, 1L, 86_400L);
        baseFine = bound(baseFine, 0L, 1_000_000_000_000L);
        bountyUnit = bound(bountyUnit, 0L, 1_000_000_000_000L);
        baseJailSeconds = bound(baseJailSeconds, 0L, 86_400L);
        maxJailSeconds = bound(maxJailSeconds, baseJailSeconds, 31_536_000L);
    }

    public static LawConfig defaults() {
        return new LawConfig(true, 400L, 40L, 3, .70D, .72D, .70D,
                86_400L, 20L, 100L, 25L, 30L, 900L, true, true);
    }

    public static LawConfig load(Path file) {
        LawConfig fallback = defaults();
        Properties properties = new Properties();
        if (Files.isRegularFile(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { properties.load(reader); }
            catch (IOException ignored) { return fallback; }
        } else {
            writeDefaults(file, fallback);
            return fallback;
        }
        return new LawConfig(
                bool(properties, "law.enabled", fallback.enabled()),
                number(properties, "law.review_pulse_ticks", fallback.reviewPulseTicks()),
                number(properties, "law.officer_pulse_ticks", fallback.officerPulseTicks()),
                (int) number(properties, "law.warrant_min_evidence", fallback.warrantEvidenceCount()),
                decimal(properties, "law.warrant_score", fallback.warrantScore()),
                decimal(properties, "law.conviction_score", fallback.convictionScore()),
                decimal(properties, "law.acquit_alibi_strength", fallback.acquitAlibiStrength()),
                number(properties, "law.warrant_lifetime_seconds", fallback.warrantLifetimeSeconds()),
                number(properties, "law.hearing_delay_seconds", fallback.hearingDelaySeconds()),
                number(properties, "law.base_fine", fallback.baseFine()),
                number(properties, "law.bounty_unit", fallback.bountyUnit()),
                number(properties, "law.base_jail_seconds", fallback.baseJailSeconds()),
                number(properties, "law.max_jail_seconds", fallback.maxJailSeconds()),
                bool(properties, "law.track_players", fallback.trackPlayers()),
                bool(properties, "law.physically_jail_npcs", fallback.physicallyJailNpcs()));
    }

    private static void writeDefaults(Path file, LawConfig config) {
        try {
            Files.createDirectories(file.getParent());
            Properties properties = new Properties();
            properties.setProperty("law.enabled", Boolean.toString(config.enabled()));
            properties.setProperty("law.review_pulse_ticks", Long.toString(config.reviewPulseTicks()));
            properties.setProperty("law.officer_pulse_ticks", Long.toString(config.officerPulseTicks()));
            properties.setProperty("law.warrant_min_evidence", Integer.toString(config.warrantEvidenceCount()));
            properties.setProperty("law.warrant_score", Double.toString(config.warrantScore()));
            properties.setProperty("law.conviction_score", Double.toString(config.convictionScore()));
            properties.setProperty("law.acquit_alibi_strength", Double.toString(config.acquitAlibiStrength()));
            properties.setProperty("law.warrant_lifetime_seconds", Long.toString(config.warrantLifetimeSeconds()));
            properties.setProperty("law.hearing_delay_seconds", Long.toString(config.hearingDelaySeconds()));
            properties.setProperty("law.base_fine", Long.toString(config.baseFine()));
            properties.setProperty("law.bounty_unit", Long.toString(config.bountyUnit()));
            properties.setProperty("law.base_jail_seconds", Long.toString(config.baseJailSeconds()));
            properties.setProperty("law.max_jail_seconds", Long.toString(config.maxJailSeconds()));
            properties.setProperty("law.track_players", Boolean.toString(config.trackPlayers()));
            properties.setProperty("law.physically_jail_npcs", Boolean.toString(config.physicallyJailNpcs()));
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                properties.store(writer, "Lively NPCs justice policy");
            }
            try { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (IOException ignored) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
        } catch (IOException ignored) { }
    }

    private static boolean bool(Properties properties, String key, boolean fallback) {
        String raw = properties.getProperty(key);
        return raw == null ? fallback : Boolean.parseBoolean(raw.trim());
    }
    private static long number(Properties properties, String key, long fallback) {
        try { return Long.parseLong(properties.getProperty(key, Long.toString(fallback)).trim()); }
        catch (NumberFormatException ignored) { return fallback; }
    }
    private static double decimal(Properties properties, String key, double fallback) {
        try { return Double.parseDouble(properties.getProperty(key, Double.toString(fallback)).trim()); }
        catch (NumberFormatException ignored) { return fallback; }
    }
    private static long bound(long value, long min, long max) { return Math.max(min, Math.min(max, value)); }
    private static double unit(double value) { return Math.max(0D, Math.min(1D, value)); }
}
