package vn.svframe.svarcade.systems.board;

import java.util.*;
import java.util.function.*;
import vn.svframe.svarcade.bot.*;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.runtime.*;
import vn.svframe.svarcade.security.IntentGate;

/** Bridges owner-thread board capabilities to immutable worker snapshots, never party stats. */
public final class BoardDecisionSource implements SessionSystem, BotDecisionSource {
    public static final Id ID = Id.of("svarcade:board_bot_source");
    private record Config(BoardBotStrategy.Actions actions, Set<String> allowedStates) {
        private Config { allowedStates = Set.copyOf(allowedStates); }
        static Config parse(Node config) {
            config.only("actions", "allowed_states");
            Set<String> states = config.has("allowed_states") ? config.strings("allowed_states") : Set.of();
            for (String state : states) if (!state.matches("[A-Za-z0-9_-]{1,80}")) throw config.error("allowed_states", "Invalid state identifier");
            return new Config(BoardBotStrategy.Actions.parse(config.node("actions")), states);
        }
    }
    public static final class Plan implements SystemSchema, SystemFactory {
        private final Registry<BoardBotStrategy.Compiler> strategies;
        private final Function<GenericSession, BooleanSupplier> legacyPhases;
        private final Function<GenericSession, BiFunction<UUID, Long, IntentGate.Facts>> facts;
        public Plan(Registry<BoardBotStrategy.Compiler> strategies,
                    Function<GenericSession, BiFunction<UUID, Long, IntentGate.Facts>> facts) {
            this(strategies, null, facts);
        }
        /** Compatibility constructor for existing embedding tests; production definitions use allowed_states. */
        public Plan(Registry<BoardBotStrategy.Compiler> strategies, Function<GenericSession, BooleanSupplier> phases,
                    Function<GenericSession, BiFunction<UUID, Long, IntentGate.Facts>> facts) {
            this.strategies = Objects.requireNonNull(strategies); legacyPhases = phases; this.facts = Objects.requireNonNull(facts);
        }
        @Override public void validate(Node config) { Config.parse(config); }
        @Override public Set<Id> dependencies(Node config) {
            Config parsed = Config.parse(config); Set<Id> result = new LinkedHashSet<>(Set.of(BoardSystem.ID, MovementSystem.ID, BoardAdjudicationSystem.ID));
            if (legacyPhases == null && !parsed.allowedStates().isEmpty()) result.add(StateMachineSystem.ID);
            return Set.copyOf(result);
        }
        @Override public Set<SessionServices.Key<?>> requires(Node config) {
            Config parsed = Config.parse(config); Set<SessionServices.Key<?>> result = new LinkedHashSet<>(Set.of(BoardSystem.ACCESS, MovementSystem.ACCESS, BoardAdjudicationSystem.ACCESS));
            if (legacyPhases == null && !parsed.allowedStates().isEmpty()) result.add(StateMachineAccess.ACCESS);
            return Set.copyOf(result);
        }
        @Override public Set<SessionServices.Key<?>> provides() { return Set.of(BotDecisionSource.ACCESS); }
        @Override public SessionSystem create(GenericSession session, Node config) {
            Config parsed = Config.parse(config); BooleanSupplier phase;
            if (legacyPhases != null) phase = Objects.requireNonNull(legacyPhases.apply(session));
            else if (parsed.allowedStates().isEmpty()) phase = () -> true;
            else {
                StateMachineAccess fsm = session.services().require(StateMachineAccess.ACCESS);
                phase = () -> parsed.allowedStates().contains(fsm.state());
            }
            BoardDecisionSource source = new BoardDecisionSource(session, session.services().require(BoardSystem.ACCESS), session.services().require(MovementSystem.ACCESS),
                    session.services().require(BoardAdjudicationSystem.ACCESS), parsed.actions(), strategies, phase, facts.apply(session));
            session.services().provide(BotDecisionSource.ACCESS, source); return source;
        }
    }
    private final GenericSession session;
    private final BoardAccess board;
    private final MovementDefinition movement;
    private final AdjudicationAccess adjudication;
    private final BoardBotStrategy.Actions actions;
    private final Registry<BoardBotStrategy.Compiler> strategies;
    private final BooleanSupplier phase;
    private final BiFunction<UUID, Long, IntentGate.Facts> facts;
    private boolean active, closed;
    public BoardDecisionSource(GenericSession session, BoardAccess board, MovementAccess movement, AdjudicationAccess adjudication,
                               BoardBotStrategy.Actions actions, Registry<BoardBotStrategy.Compiler> strategies, BooleanSupplier phase,
                               BiFunction<UUID, Long, IntentGate.Facts> facts) {
        this.session = Objects.requireNonNull(session); this.board = Objects.requireNonNull(board); this.movement = movement.definition();
        this.adjudication = Objects.requireNonNull(adjudication); this.actions = Objects.requireNonNull(actions); this.strategies = Objects.requireNonNull(strategies);
        this.phase = Objects.requireNonNull(phase); this.facts = Objects.requireNonNull(facts);
    }
    private void requireActive() { session.thread().check(); if (!active) throw new IllegalStateException("Board bot source inactive"); }
    @Override public Set<Id> requiredActions() { requireActive(); return actions.ids(); }
    @Override public CompiledProfile compile(BotProfile profile) {
        requireActive(); BoardBotStrategy compiled = strategies.require(profile.strategy()).compile(movement, adjudication.rules(), profile.parameters(), actions);
        return (actor, seed) -> {
            requireActive(); if (!eligible(actor)) throw new IllegalStateException("Board actor cannot think in this state");
            String team = session.participants().get(actor).team();
            BoardSearch.Snapshot snapshot = new BoardSearch.Snapshot(board.position(), board.repetitionCounts(), true);
            boolean offer = adjudication.offeredBy().filter(owner -> !owner.equals(team)).isPresent();
            return compiled.bind(snapshot, offer, seed);
        };
    }
    @Override public boolean eligible(UUID actor) {
        requireActive(); Participant participant = session.participants().get(actor);
        return participant != null && participant.kind() == Participant.Kind.BOT && participant.team().equals(board.position().turn()) && phase.getAsBoolean() && adjudication.mayPlay();
    }
    @Override public IntentGate.Facts facts(UUID actor, long tick) { requireActive(); return facts.apply(actor, tick); }
    @Override public void start() { session.thread().check(); if (active || closed) throw new IllegalStateException("Board bot source already initialized"); active = true; }
    @Override public void tick(long tick) { requireActive(); }
    @Override public int stateSchema() { return 1; }
    @Override public Map<String, Object> snapshot() { requireActive(); return Map.of(); }
    @Override public void restore(int schema, Map<String, Object> state) {
        if (schema != 1 || !state.isEmpty()) throw new ConfigException("Invalid board bot source state"); start();
    }
    @Override public void close() { session.thread().check(); active = false; closed = true; }
}
