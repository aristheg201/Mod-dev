package vn.svframe.svarcade.bot;

import java.util.*;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.runtime.*;
import vn.svframe.svarcade.security.*;

/** Generic owner-thread bot scheduling with persisted assignments, backoff and shared workers. */
public final class BotSystem implements SessionSystem {
    public static final Id ID = Id.of("svarcade:bot");
    public record Config(Map<BotRuntime.Difficulty, BotProfile> profiles, BotRuntime.Difficulty defaultDifficulty,
                         Map<String, BotRuntime.Difficulty> teamDifficulties, int maxBotsPerTick, long seed) {
        public Config {
            profiles = Map.copyOf(profiles); teamDifficulties = Map.copyOf(teamDifficulties); Objects.requireNonNull(defaultDifficulty);
            if (!profiles.keySet().equals(EnumSet.allOf(BotRuntime.Difficulty.class)) || maxBotsPerTick < 1 || maxBotsPerTick > 1024 || teamDifficulties.size() > 1024) {
                throw new ConfigException("Bot definitions require exactly EASY/NORMAL/HARD and bounded scheduling");
            }
        }
        public static Config parse(Node n) {
            n.only("profiles", "default_difficulty", "team_difficulties", "max_bots_per_tick", "seed");
            Map<BotRuntime.Difficulty, BotProfile> profiles = new EnumMap<>(BotRuntime.Difficulty.class); Node entries = n.node("profiles");
            for (String key : entries.values().keySet()) {
                if (profiles.putIfAbsent(difficulty(key), BotProfile.parse(entries.node(key))) != null) throw new ConfigException("Duplicate difficulty profile");
            }
            Map<String, BotRuntime.Difficulty> teams = new LinkedHashMap<>();
            if (n.has("team_difficulties")) {
                Node values = n.node("team_difficulties");
                for (String team : values.values().keySet()) teams.put(team, difficulty(values.string(team)));
            }
            return new Config(profiles, difficulty(n.string("default_difficulty")), teams, (int) n.integer("max_bots_per_tick", 1, 1024), n.integer("seed", Long.MIN_VALUE, Long.MAX_VALUE));
        }
        private static BotRuntime.Difficulty difficulty(String value) {
            try { return BotRuntime.Difficulty.valueOf(value.toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException e) { throw new ConfigException("Unsupported bot difficulty: " + value, e); }
        }
    }
    private static final class Actor {
        final UUID id;
        final BotRuntime.Difficulty difficulty;
        long due, attempt, submittedAt, revision;
        boolean pending;
        Actor(UUID id, BotRuntime.Difficulty difficulty, long due) { this.id = id; this.difficulty = difficulty; this.due = due; }
    }
    private final GenericSession session;
    private final BotRuntime workers;
    private final ActionDispatcher dispatcher;
    private final BotDecisionSource source;
    private final Config config;
    private final Map<BotRuntime.Difficulty, BotDecisionSource.CompiledProfile> compiled;
    private final List<Actor> actors = new ArrayList<>();
    private boolean active, closed;
    private long elapsed, lastTick = -1;
    private int cursor;
    public BotSystem(GenericSession session, BotRuntime workers, ActionDispatcher dispatcher, BotDecisionSource source, Config config) {
        this.session = Objects.requireNonNull(session); this.workers = Objects.requireNonNull(workers); this.dispatcher = Objects.requireNonNull(dispatcher);
        this.source = Objects.requireNonNull(source); this.config = Objects.requireNonNull(config); session.thread().check();
        Map<BotRuntime.Difficulty, BotDecisionSource.CompiledProfile> profiles = new EnumMap<>(BotRuntime.Difficulty.class);
        config.profiles().forEach((difficulty, profile) -> profiles.put(difficulty, Objects.requireNonNull(source.compile(profile)))); compiled = Map.copyOf(profiles);
        Set<String> teams = new HashSet<>(); session.participants().values().forEach(p -> teams.add(p.team()));
        if (!teams.containsAll(config.teamDifficulties().keySet())) throw new ConfigException("Bot difficulty assigned to unknown team");
        session.resources().own("bot/session-scheduler", this::close);
    }
    @Override public void start() {
        session.thread().check(); if (active || closed) throw new IllegalStateException("Bot scheduler already initialized");
        for (Participant participant : session.participants().values()) if (participant.kind() == Participant.Kind.BOT) {
            BotRuntime.Difficulty difficulty = config.teamDifficulties().getOrDefault(participant.team(), config.defaultDifficulty());
            actors.add(new Actor(participant.id(), difficulty, config.profiles().get(difficulty).delayTicks()));
        }
        actors.sort(Comparator.comparing(actor -> actor.id)); active = true;
    }
    private void requireActive() { session.thread().check(); if (!active) throw new IllegalStateException("Bot scheduler inactive"); }
    @Override public void tick(long tick) {
        requireActive(); if (tick < 0 || lastTick >= 0 && tick <= lastTick) throw new IllegalArgumentException("Non-increasing bot tick");
        long delta = lastTick < 0 ? 0 : tick - lastTick; elapsed = Math.addExact(elapsed, delta); lastTick = tick;
        if (delta > 0 && !actors.isEmpty()) session.markDirty();
        int count = Math.min(config.maxBotsPerTick(), actors.size());
        for (int i = 0; i < count; i++) {
            Actor actor = actors.get(cursor); cursor = (cursor + 1) % actors.size();
            BotProfile profile = config.profiles().get(actor.difficulty);
            if (actor.pending) {
                if (!workers.hasPending(actor.id) || actor.revision != session.revision() || elapsed - actor.submittedAt >= profile.pendingTimeoutTicks() || !source.eligible(actor.id)) {
                    workers.cancel(actor.id); actor.pending = false; actor.due = Math.addExact(elapsed, profile.retryTicks()); session.markDirty();
                } else {
                    BotRuntime.Poll result = workers.pollResult(session, actor.id, dispatcher, facts(actor.id, tick));
                    if (!active || session.status() != GenericSession.Status.RUNNING) return;
                    if (result != BotRuntime.Poll.WAITING) {
                        actor.pending = false; actor.due = Math.addExact(elapsed, result == BotRuntime.Poll.APPLIED ? profile.delayTicks() : profile.retryTicks()); session.markDirty();
                    }
                }
                continue;
            }
            if (elapsed < actor.due || !source.eligible(actor.id)) continue;
            // No search or live-state callback is allowed inside the worker computation.
            long seed = config.seed() ^ actor.id.getMostSignificantBits() ^ actor.id.getLeastSignificantBits() ^ actor.attempt;
            BotRuntime.Strategy job = compiled.get(actor.difficulty).snapshot(actor.id, seed);
            BotRuntime.Context context = new BotRuntime.Context(session.id(), actor.id, session.revision(), Map.of("seed", seed), profile.parameters());
            actor.due = Math.addExact(elapsed, profile.retryTicks());
            if (workers.submit(context, job, profile.thinkNanos(), profile.operations())) {
                try { dispatcher.issueController(actor.id, Math.addExact(tick, profile.controllerTtlTicks())); }
                catch (RuntimeException failure) { workers.cancel(actor.id); throw failure; }
                actor.pending = true; actor.submittedAt = elapsed; actor.revision = session.revision(); actor.attempt = Math.incrementExact(actor.attempt);
            }
            session.markDirty();
        }
    }
    private IntentGate.Facts facts(UUID actor, long tick) {
        IntentGate.Facts facts = Objects.requireNonNull(source.facts(actor, tick));
        if (!facts.actor().equals(actor) || facts.tick() != tick) throw new IllegalStateException("Bot source returned mismatched authoritative facts");
        return facts;
    }
    @Override public int stateSchema() { return 1; }
    @Override public Map<String, Object> snapshot() {
        requireActive(); List<Object> assignments = new ArrayList<>();
        for (Actor actor : actors) assignments.add(Map.of("actor", actor.id.toString(), "difficulty", actor.difficulty.name(), "attempt", actor.attempt,
                "remaining", actor.pending ? 0L : Math.max(0L, actor.due - elapsed)));
        return Values.map(Map.of("assignments", assignments));
    }
    @Override public void restore(int schema, Map<String, Object> state) {
        session.thread().check(); if (active || closed || schema != 1) throw new IllegalArgumentException("Invalid bot scheduler restore");
        Node n = new Node(state, "bot-state"); n.only("assignments"); List<Actor> restored = new ArrayList<>(); Set<UUID> seen = new HashSet<>();
        for (Node entry : n.nodes("assignments")) {
            entry.only("actor", "difficulty", "attempt", "remaining"); UUID id = UUID.fromString(entry.string("actor"));
            Participant participant = session.participants().get(id);
            if (participant == null || participant.kind() != Participant.Kind.BOT || !seen.add(id)) throw new ConfigException("Invalid restored bot identity");
            BotRuntime.Difficulty difficulty = Config.difficulty(entry.string("difficulty"));
            Actor actor = new Actor(id, difficulty, entry.integer("remaining", 0, 72_000)); actor.attempt = entry.integer("attempt", 0, Long.MAX_VALUE - 1); restored.add(actor);
        }
        long expected = session.participants().values().stream().filter(p -> p.kind() == Participant.Kind.BOT).count();
        if (seen.size() != expected) throw new ConfigException("Restored bot assignments do not match participants");
        restored.sort(Comparator.comparing(actor -> actor.id)); actors.addAll(restored); active = true;
        // Futures/controller grants are deliberately never serialized or replayed after restart.
    }
    public Map<UUID, BotRuntime.Difficulty> assignments() {
        requireActive(); Map<UUID, BotRuntime.Difficulty> result = new LinkedHashMap<>(); actors.forEach(a -> result.put(a.id, a.difficulty)); return Map.copyOf(result);
    }
    @Override public void close() {
        session.thread().check(); if (closed) return; closed = true; active = false;
        workers.cancelSession(session.id()); actors.forEach(a -> dispatcher.revokeController(a.id)); actors.clear();
    }
}
