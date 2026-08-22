package vn.svframe.lively.economy;

import vn.svframe.lively.actor.ActorId;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Bounded gambling history plus behavioural habit state. Monetary movement remains server-authoritative elsewhere. */
public final class GamblingEngine {
    public enum Result { WIN, LOSS, PUSH, CANCELLED, UNPAID }

    public record Bet(UUID id, ActorId gambler, UUID houseBusiness, String game, String currency,
                      long stake, long payout, Result result, Instant occurredAt, Map<String, String> facts, long revision) {
        public Bet {
            Objects.requireNonNull(id); Objects.requireNonNull(gambler); Objects.requireNonNull(game);
            Objects.requireNonNull(currency); Objects.requireNonNull(result); Objects.requireNonNull(occurredAt);
            stake = Math.max(0L, stake); payout = Math.max(0L, payout);
            facts = Map.copyOf(facts == null ? Map.of() : facts);
        }
    }

    public record Habit(double exposure, double compulsion, long lifetimeStake, long lifetimePayout,
                        int consecutiveLosses, long revision) {
        public Habit {
            exposure = unit(exposure); compulsion = unit(compulsion);
            lifetimeStake = Math.max(0L, lifetimeStake); lifetimePayout = Math.max(0L, lifetimePayout);
            consecutiveLosses = Math.max(0, Math.min(10_000, consecutiveLosses));
        }
    }

    private static final int MAX_BETS = 100_000;
    private final ConcurrentHashMap<UUID, Bet> bets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ActorId, Habit> habits = new ConcurrentHashMap<>();
    private final AtomicLong revision = new AtomicLong();

    public Bet record(ActorId gambler, UUID houseBusiness, String game, String currency, long stake, long payout,
                      Result result, Map<String, String> facts) {
        if (stake < 0L || stake > 1_000_000_000_000L || payout < 0L || payout > 2_000_000_000_000L) {
            throw new IllegalArgumentException("invalid gambling amount");
        }
        long rev = revision.incrementAndGet();
        Bet bet = new Bet(UUID.randomUUID(), gambler, houseBusiness, normalize(game), normalize(currency), stake, payout,
                result, Instant.now(), sanitize(facts), rev);
        bets.put(bet.id(), bet);
        if (bets.size() > MAX_BETS) {
            bets.values().stream().sorted(java.util.Comparator.comparing(Bet::occurredAt))
                    .limit(Math.max(1, bets.size() - MAX_BETS)).map(Bet::id).toList().forEach(bets::remove);
        }
        habits.compute(gambler, (actor, old) -> updateHabit(old, stake, payout, result, rev));
        return bet;
    }

    public Habit habit(ActorId actor) { return habits.getOrDefault(actor, new Habit(0D, 0D, 0L, 0L, 0, 0L)); }
    public Optional<Bet> get(UUID id) { return Optional.ofNullable(bets.get(id)); }
    public Snapshot snapshot() { return new Snapshot(revision.get(), Map.copyOf(bets), Map.copyOf(habits)); }
    public void restore(Snapshot snapshot) {
        bets.clear(); habits.clear();
        if (snapshot != null) { bets.putAll(snapshot.bets()); habits.putAll(snapshot.habits()); }
        revision.set(snapshot == null ? 0L : Math.max(0L, snapshot.revision()));
    }

    private static Habit updateHabit(Habit old, long stake, long payout, Result result, long rev) {
        Habit base = old == null ? new Habit(0D, 0D, 0L, 0L, 0, 0L) : old;
        double exposure = unit(base.exposure() + Math.min(.035D, .006D + Math.log10(Math.max(1L, stake)) * .003D));
        int losses = result == Result.LOSS ? base.consecutiveLosses() + 1 : result == Result.WIN ? 0 : base.consecutiveLosses();
        double impulse = result == Result.LOSS ? .018D + Math.min(.045D, losses * .004D)
                : result == Result.WIN ? .012D : -.004D;
        double compulsion = unit(base.compulsion() * .985D + exposure * .018D + impulse);
        long lifetimeStake = saturatingAdd(base.lifetimeStake(), stake);
        long lifetimePayout = saturatingAdd(base.lifetimePayout(), payout);
        return new Habit(exposure, compulsion, lifetimeStake, lifetimePayout, losses, rev);
    }

    private static long saturatingAdd(long a, long b) {
        try { return Math.addExact(a, b); }
        catch (ArithmeticException ignored) { return Long.MAX_VALUE; }
    }

    private static String normalize(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
        return normalized.isBlank() ? "unknown" : normalized.substring(0, Math.min(96, normalized.length()));
    }

    private static Map<String, String> sanitize(Map<String, String> raw) {
        if (raw == null || raw.isEmpty()) return Map.of();
        HashMap<String, String> result = new HashMap<>();
        for (Map.Entry<String, String> entry : raw.entrySet()) {
            if (result.size() >= 32) break;
            String key = entry.getKey(), value = entry.getValue();
            if (key == null || key.isBlank() || key.length() > 64 || value == null || value.length() > 256) continue;
            result.put(key, value);
        }
        return Map.copyOf(result);
    }

    private static double unit(double value) { return Math.max(0D, Math.min(1D, value)); }

    public record Snapshot(long revision, Map<UUID, Bet> bets, Map<ActorId, Habit> habits) {
        public Snapshot {
            bets = Map.copyOf(bets == null ? Map.of() : bets);
            habits = Map.copyOf(habits == null ? Map.of() : habits);
        }
    }
}
