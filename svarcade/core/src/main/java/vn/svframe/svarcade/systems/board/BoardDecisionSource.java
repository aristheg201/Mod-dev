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
    public static final class Plan implements SystemSchema, SystemFactory {
        private final Registry<BoardBotStrategy.Compiler> strategies;
        private final Function<GenericSession, BooleanSupplier> phases;
        private final Function<GenericSession, BiFunction<UUID, Long, IntentGate.Facts>> facts;
        public Plan(Registry<BoardBotStrategy.Compiler> strategies, Function<GenericSession, BooleanSupplier> phases,
                    Function<GenericSession, BiFunction<UUID, Long, IntentGate.Facts>> facts) {
            this.strategies = Objects.requireNonNull(strategies); this.phases = Objects.requireNonNull(phases); this.facts = Objects.requireNonNull(facts);
        }
        @Override public void validate(Node config) { config.only("actions"); BoardBotStrategy.Actions.parse(config.node("actions")); }
        @Override public Set<Id> dependencies() { return Set.of(BoardSystem.ID, MovementSystem.ID, BoardAdjudicationSystem.ID); }
        @Override public Set<SessionServices.Key<?>> requires() { return Set.of(BoardSystem.ACCESS, MovementSystem.ACCESS, BoardAdjudicationSystem.ACCESS); }
        @Override public Set<SessionServices.Key<?>> provides() { return Set.of(BotDecisionSource.ACCESS); }
        @Override public SessionSystem create(GenericSession session, Node config) {
            validate(config);
            BoardDecisionSource source = new BoardDecisionSource(session, session.services().require(BoardSystem.ACCESS), session.services().require(MovementSystem.ACCESS),
                    session.services().require(BoardAdjudicationSystem.ACCESS), BoardBotStrategy.Actions.parse(config.node("actions")), strategies, phases.apply(session), facts.apply(session));
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
