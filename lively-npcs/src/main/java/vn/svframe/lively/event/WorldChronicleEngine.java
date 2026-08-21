package vn.svframe.lively.event;

import vn.svframe.lively.persistence.WorldHistoryJournal;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Human-readable semantic history derived from the append-only world history journal.
 * It can suggest memorial/book concepts through facts but never edits blocks or inventories by itself.
 */
public final class WorldChronicleEngine {
    private static final Duration ERA_GAP = Duration.ofDays(7);
    private static final int ERA_ENTRY_LIMIT = 24;
    private static final int MAX_ENTRIES = 20_000;

    public record ChronicleEntry(long sequence, Instant at, String title, WorldEventEngine.Category category,
                                 String seed, String structureId, double significance, Map<String, String> facts) {
        public ChronicleEntry {
            Objects.requireNonNull(at); Objects.requireNonNull(title); Objects.requireNonNull(category); Objects.requireNonNull(seed);
            significance = Math.max(0D, Math.min(1D, significance));
            facts = Map.copyOf(facts == null ? Map.of() : facts);
        }
    }

    public record Era(int index, String id, String name, Instant startedAt, Instant endedAt,
                      List<Long> entries, Map<String, String> facts) {
        public Era {
            entries = List.copyOf(entries);
            facts = Map.copyOf(facts == null ? Map.of() : facts);
        }
    }

    public record Snapshot(List<ChronicleEntry> entries, List<Era> eras) {
        public Snapshot { entries = List.copyOf(entries); eras = List.copyOf(eras); }
    }

    private final ConcurrentSkipListMap<Long, ChronicleEntry> entries = new ConcurrentSkipListMap<>();
    private final AtomicLong sequence = new AtomicLong();
    private volatile List<Era> eras = List.of();

    public synchronized ChronicleEntry record(WorldEventEngine.WorldEvent event) {
        Objects.requireNonNull(event);
        long seq = sequence.incrementAndGet();
        Map<String, String> facts = new LinkedHashMap<>(event.facts());
        facts.put("event", event.id().toString());
        facts.put("phase", event.phase().name());
        facts.put("intensity", Double.toString(event.intensity()));
        if (event.intensity() >= .82D) {
            facts.putIfAbsent("memorial_suggestion", memorialKind(event.category()));
            facts.putIfAbsent("chronicle_book", "true");
        }
        ChronicleEntry entry = new ChronicleEntry(seq, Instant.now(), title(event), event.category(), event.seed(),
                event.structureId(), significance(event), facts);
        putBounded(entry);
        rebuildEras();
        return entry;
    }

    /** Rebuilds durable chronicle state from verified journal records after a server restart. */
    public synchronized void rebuild(List<WorldHistoryJournal.Entry> history) {
        entries.clear(); sequence.set(0L);
        if (history != null) {
            history.stream().filter(item -> "event_finished".equals(item.type())).sorted(Comparator.comparingLong(WorldHistoryJournal.Entry::sequence))
                    .limit(MAX_ENTRIES).forEach(item -> {
                        Map<String, String> facts = new LinkedHashMap<>(item.facts());
                        WorldEventEngine.Category category = category(facts.get("category"));
                        String seed = facts.getOrDefault("seed", item.subjectId());
                        String structure = blankToNull(facts.get("structure"));
                        double intensity = number(facts.get("intensity"), .5D);
                        double significance = Math.max(.15D, Math.min(1D, .28D + intensity * .62D));
                        ChronicleEntry entry = new ChronicleEntry(item.sequence(), item.at(), title(category, seed), category,
                                seed, structure, significance, facts);
                        entries.put(entry.sequence(), entry);
                        sequence.accumulateAndGet(entry.sequence(), Math::max);
                    });
        }
        trim();
        rebuildEras();
    }

    public List<ChronicleEntry> latest(int limit) {
        int bounded = Math.max(0, Math.min(200, limit));
        if (bounded == 0) return List.of();
        return entries.descendingMap().values().stream().limit(bounded).toList();
    }

    public List<Era> eras() { return eras; }
    public Snapshot snapshot() { return new Snapshot(List.copyOf(entries.values()), eras); }
    public synchronized void clear() { entries.clear(); eras = List.of(); sequence.set(0L); }

    private void putBounded(ChronicleEntry entry) {
        entries.put(entry.sequence(), entry);
        trim();
    }

    private void trim() {
        while (entries.size() > MAX_ENTRIES) entries.pollFirstEntry();
    }

    private void rebuildEras() {
        List<EraBuilder> builders = new ArrayList<>();
        EraBuilder current = null;
        for (ChronicleEntry entry : entries.values()) {
            boolean explicitBreak = Boolean.parseBoolean(entry.facts().getOrDefault("era_break", "false"));
            boolean majorBreak = entry.significance() >= .92D && (entry.category() == WorldEventEngine.Category.DISASTER
                    || "villain_emergence".equals(entry.facts().get("kind")));
            boolean gap = current != null && Duration.between(current.lastAt, entry.at()).compareTo(ERA_GAP) > 0;
            boolean full = current != null && current.entries.size() >= ERA_ENTRY_LIMIT;
            if (current == null || explicitBreak || majorBreak || gap || full) {
                if (current != null) current.endedAt = entry.at();
                current = new EraBuilder(builders.size() + 1, entry);
                builders.add(current);
            } else current.add(entry);
        }
        List<Era> result = new ArrayList<>(builders.size());
        for (EraBuilder builder : builders) result.add(builder.build());
        eras = List.copyOf(result);
    }

    private static double significance(WorldEventEngine.WorldEvent event) {
        double categoryWeight = switch (event.category()) {
            case DISASTER, FACTION_CONFLICT, POLITICAL -> .16D;
            case CRIME, MIGRATION, MYSTERY -> .10D;
            case ECONOMIC, DISCOVERY -> .08D;
            case FESTIVAL, SOCIAL -> .05D;
        };
        return Math.max(0D, Math.min(1D, .22D + event.intensity() * .68D + categoryWeight));
    }

    private static String memorialKind(WorldEventEngine.Category category) {
        return switch (category) {
            case DISASTER -> "memorial";
            case CRIME -> "grave_or_memorial";
            case FACTION_CONFLICT, POLITICAL -> "monument";
            case DISCOVERY, MYSTERY -> "archive_or_book";
            case MIGRATION -> "field_record";
            default -> "chronicle_book";
        };
    }

    private static String title(WorldEventEngine.WorldEvent event) { return title(event.category(), event.seed()); }
    private static String title(WorldEventEngine.Category category, String seed) {
        String readable = seed == null ? "unknown" : seed.replace('_', ' ').replace(':', ' ');
        return switch (category) {
            case CRIME -> "Vụ việc: " + readable;
            case DISASTER -> "Biến cố: " + readable;
            case MIGRATION -> "Đợt di cư: " + readable;
            case FACTION_CONFLICT -> "Xung đột: " + readable;
            case POLITICAL -> "Chuyển biến chính trị: " + readable;
            case ECONOMIC -> "Biến động kinh tế: " + readable;
            case FESTIVAL -> "Lễ hội: " + readable;
            case MYSTERY -> "Bí ẩn: " + readable;
            case DISCOVERY -> "Khám phá: " + readable;
            case SOCIAL -> "Sự kiện xã hội: " + readable;
        };
    }

    private static WorldEventEngine.Category category(String raw) {
        try { return WorldEventEngine.Category.valueOf(raw == null ? "DISCOVERY" : raw.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ignored) { return WorldEventEngine.Category.DISCOVERY; }
    }

    private static double number(String raw, double fallback) {
        try { return raw == null ? fallback : Double.parseDouble(raw); }
        catch (NumberFormatException ignored) { return fallback; }
    }
    private static String blankToNull(String raw) { return raw == null || raw.isBlank() ? null : raw; }

    private static final class EraBuilder {
        final int index;
        final Instant startedAt;
        final String seed;
        final WorldEventEngine.Category openingCategory;
        final List<Long> entries = new ArrayList<>();
        final Map<String, Integer> categoryCounts = new LinkedHashMap<>();
        Instant lastAt;
        Instant endedAt;

        EraBuilder(int index, ChronicleEntry first) {
            this.index = index; this.startedAt = first.at(); this.seed = first.seed(); this.openingCategory = first.category(); add(first);
        }
        void add(ChronicleEntry entry) {
            entries.add(entry.sequence()); lastAt = entry.at();
            categoryCounts.merge(entry.category().name(), 1, Integer::sum);
        }
        Era build() {
            String dominant = categoryCounts.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(openingCategory.name());
            String id = "era_" + index;
            String name = "Era " + index + " • " + dominant.toLowerCase(Locale.ROOT).replace('_', ' ');
            return new Era(index, id, name, startedAt, endedAt, entries,
                    Map.of("opening_seed", seed, "dominant_category", dominant));
        }
    }
}
