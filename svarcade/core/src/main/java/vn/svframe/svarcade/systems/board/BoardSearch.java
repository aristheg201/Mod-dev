package vn.svframe.svarcade.systems.board;

import java.util.*;
import vn.svframe.svarcade.bot.*;
import vn.svframe.svarcade.config.ConfigException;
import vn.svframe.svarcade.systems.board.GridPosition.Piece;
import vn.svframe.svarcade.systems.board.MovementRules.*;
import vn.svframe.svarcade.systems.board.BoardOutcomeRules.Claim;
import vn.svframe.svarcade.systems.objective.ObjectiveAccess.Result;

/** Detached two-team board strategy using exactly the runtime's movement/outcome predicates. */
public final class BoardSearch {
    public record Snapshot(GridPosition position, Map<String, Integer> repetitions) {
        public Snapshot {
            Objects.requireNonNull(position);
            if (repetitions.size() > 100_000) throw new ConfigException("Search history limit");
            long characters = 0;
            for (var entry : repetitions.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null || entry.getValue() < 1) throw new ConfigException("Invalid search history");
                characters += entry.getKey().length();
                if (characters > 4_194_304) throw new ConfigException("Search history byte budget");
            }
            repetitions = Map.copyOf(repetitions);
        }
    }
    /** A claim with a move means an intended-move claim, not an executed move. */
    public record Choice(Move move, Claim claim) {
        public Choice { if (move == null && claim == null) throw new IllegalArgumentException("Empty board choice"); }
    }
    private record Trace(Trace previous, String position) { }
    private record Key(String position, long quiet, Map<String, Integer> pathCounts) { }
    /** Lazy caches are search-worker-local; logical position/history never mutate. */
    private static final class State {
        final GridPosition position;
        final Trace trace;
        final boolean claimed;
        String key;
        List<Successor> legal;
        Boolean threatened;
        Optional<Result> outcome;
        State(GridPosition position, Trace trace, boolean claimed) { this.position = position; this.trace = trace; this.claimed = claimed; }
    }
    private final MovementRules movement;
    private final BoardOutcomeRules outcomes;
    private final BoardSearchTuning tuning;
    public BoardSearch(MovementDefinition definition, BoardOutcomeRules.Config outcomes, BoardSearchTuning tuning) {
        if (definition.teams().size() != 2 || !outcomes.adjudicateImmobility()) throw new ConfigException("Minimax requires two teams and terminal immobility rules");
        this.movement = new MovementRules(definition); this.outcomes = new BoardOutcomeRules(outcomes, definition);
        this.tuning = Objects.requireNonNull(tuning); tuning.validateAgainst(definition);
    }
    public BoundedSearch.Result<Choice> search(Snapshot snapshot, ThinkBudget budget, long seed) {
        Objects.requireNonNull(snapshot); Objects.requireNonNull(budget);
        movement.validate(snapshot.position());
        Model model = new Model(snapshot);
        State root = new State(snapshot.position(), null, false);
        return new BoundedSearch<>(model, tuning.options(seed)).search(root, budget);
    }
    private final class Model implements BoundedSearch.Model<State, Choice, Key> {
        private final Snapshot snapshot;
        private final String rootTeam;
        Model(Snapshot snapshot) { this.snapshot = snapshot; rootTeam = snapshot.position().turn(); }
        private String canonical(State state, ThinkBudget budget) {
            if (state.key == null) state.key = movement.repetitionKey(state.position, budget::check);
            return state.key;
        }
        private int occurrences(State state, ThinkBudget budget) {
            String key = canonical(state, budget); int count = snapshot.repetitions().getOrDefault(key, 0);
            for (Trace trace = state.trace; trace != null; trace = trace.previous()) { budget.check(); if (key.equals(trace.position())) count = Math.incrementExact(count); }
            if (count < 1) throw new ConfigException("Root search history does not contain the current position");
            return count;
        }
        private List<Successor> legal(State state, ThinkBudget budget) {
            if (state.legal == null) state.legal = movement.successors(state.position, budget::check);
            return state.legal;
        }
        private boolean threatened(State state, ThinkBudget budget) {
            if (state.threatened == null) state.threatened = movement.threatened(state.position, state.position.turn(), budget::check);
            return state.threatened;
        }
        private Optional<Result> outcome(State state, ThinkBudget budget) {
            if (state.outcome == null) {
                boolean immobile = legal(state, budget).isEmpty();
                state.outcome = outcomes.assess(state.position, occurrences(state, budget), immobile, immobile && threatened(state, budget), budget::check);
            }
            return state.outcome;
        }
        public boolean maximizing(State state) { return state.position.turn().equals(rootTeam); }
        public OptionalDouble terminal(State state, ThinkBudget budget) {
            budget.check(); if (state.claimed) return OptionalDouble.of(0);
            Optional<Result> result = outcome(state, budget);
            if (result.isEmpty()) return OptionalDouble.empty();
            return OptionalDouble.of(result.get().draw() ? 0 : result.get().winners().contains(rootTeam) ? tuning.winScore() : -tuning.winScore());
        }
        public double evaluate(State state, ThinkBudget budget) {
            double total = 0;
            for (int square = 0; square < state.position.size(); square++) {
                budget.check(); Piece piece = state.position.at(square); if (piece == null) continue;
                double value = tuning.values().get(piece.type()) + tuning.square(piece.team(), piece.type(), square);
                total += piece.team().equals(rootTeam) ? value : -value;
            }
            if (tuning.mobility() != 0) total += (maximizing(state) ? 1 : -1) * tuning.mobility() * legal(state, budget).size();
            // A heuristic must never outrank a proven terminal result.
            return Math.clamp(total, -tuning.winScore() / 2, tuning.winScore() / 2);
        }
        public boolean forced(State state, ThinkBudget budget) { return threatened(state, budget); }
        public List<BoundedSearch.Edge<State, Choice>> successors(State state, ThinkBudget budget) {
            if (state.claimed || outcome(state, budget).isPresent()) return List.of();
            List<BoundedSearch.Edge<State, Choice>> edges = new ArrayList<>();
            int count = occurrences(state, budget);
            for (Claim claim : Claim.values()) if (outcomes.claimThreshold(state.position, count, claim).isPresent()) {
                edges.add(new BoundedSearch.Edge<>(new Choice(null, claim), new State(state.position, state.trace, true), true, 0));
            }
            for (Successor successor : legal(state, budget)) {
                budget.check(); String key = movement.repetitionKey(successor.position(), budget::check);
                State next = new State(successor.position(), new Trace(state.trace, key), false); next.key = key;
                Piece mover = state.position.at(successor.move().from()); double priority = 0;
                if (successor.captureSquare() >= 0) priority += 16 * tuning.values().get(state.position.at(successor.captureSquare()).type()) - tuning.values().get(mover.type());
                if (successor.move().promotion() != null) priority += tuning.values().get(successor.move().promotion()) - tuning.values().get(mover.type());
                boolean tactical = successor.captureSquare() >= 0 || successor.move().promotion() != null;
                edges.add(new BoundedSearch.Edge<>(new Choice(successor.move(), null), next, tactical, priority));
                int nextCount = occurrences(next, budget);
                for (Claim claim : Claim.values()) {
                    if (outcomes.claimThreshold(next.position, nextCount, claim).isPresent() && outcome(next, budget).isEmpty()) {
                        edges.add(new BoundedSearch.Edge<>(new Choice(successor.move(), claim), new State(state.position, state.trace, true), true, 0));
                    }
                }
            }
            return List.copyOf(edges);
        }
        public Key key(State state, ThinkBudget budget) {
            // Historical counts shared by this search are constant. Only the path delta belongs
            // in each TT key. Exact map equality prevents history-dependent draw aliasing.
            Map<String, Integer> delta = new HashMap<>();
            for (Trace trace = state.trace; trace != null; trace = trace.previous()) { budget.check(); delta.merge(trace.position(), 1, Math::addExact); }
            return new Key(canonical(state, budget), state.position.quietPlies(), Map.copyOf(delta));
        }
    }
}
