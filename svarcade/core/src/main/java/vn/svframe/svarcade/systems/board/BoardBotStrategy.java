package vn.svframe.svarcade.systems.board;

import java.util.*;
import vn.svframe.svarcade.bot.*;
import vn.svframe.svarcade.config.*;

/** A compiled detached strategy. No session, controller, world or mutable board is captured. */
public final class BoardBotStrategy {
    public record Actions(Id move, Id repetitionClaim, Id quietClaim, Id acceptDraw) {
        public Actions {
            Objects.requireNonNull(move); Objects.requireNonNull(repetitionClaim); Objects.requireNonNull(quietClaim); Objects.requireNonNull(acceptDraw);
            if (new HashSet<>(List.of(move, repetitionClaim, quietClaim, acceptDraw)).size() != 4) throw new ConfigException("Board bot action bindings must be distinct");
        }
        public Set<Id> ids() { return Set.of(move, repetitionClaim, quietClaim, acceptDraw); }
        public static Actions parse(Node n) {
            n.only("move", "claim_repetition", "claim_quiet", "accept_draw");
            return new Actions(Id.of(n.string("move")), Id.of(n.string("claim_repetition")), Id.of(n.string("claim_quiet")), Id.of(n.string("accept_draw")));
        }
    }
    @FunctionalInterface public interface Compiler {
        BoardBotStrategy compile(MovementDefinition movement, BoardOutcomeRules.Config outcomes, Node tuning, Actions actions);
    }
    private final BoardSearch search;
    private final Actions actions;
    private final boolean acceptDrawWhenNotAhead;
    private BoardBotStrategy(BoardSearch search, Actions actions, boolean acceptDrawWhenNotAhead) {
        this.search = search; this.actions = actions; this.acceptDrawWhenNotAhead = acceptDrawWhenNotAhead;
    }
    public static BoardBotStrategy compile(MovementDefinition movement, BoardOutcomeRules.Config outcomes, Node tuning, Actions actions) {
        tuning.only("search", "evaluation", "accept_draw_when_not_ahead");
        Node evaluation = new Node(Map.of("search", tuning.node("search").values(), "evaluation", tuning.node("evaluation").values()), "board-bot");
        return new BoardBotStrategy(new BoardSearch(movement, outcomes, BoardSearchTuning.parse(evaluation)), actions, tuning.bool("accept_draw_when_not_ahead", false));
    }
    public static Registry<Compiler> builtins() { return new Registry<>(Map.of(Id.of("svarcade:minimax"), BoardBotStrategy::compile)); }
    public BotRuntime.BoundedStrategy bind(BoardSearch.Snapshot snapshot, boolean opponentOffer, long seed) {
        return new Detached(search, actions, snapshot, opponentOffer && acceptDrawWhenNotAhead, seed);
    }
    private record Detached(BoardSearch search, Actions actions, BoardSearch.Snapshot snapshot, boolean considerOffer, long seed) implements BotRuntime.BoundedStrategy {
        @Override public Optional<BotRuntime.Decision> decideWithinBudget(BotRuntime.Context context, ThinkBudget budget) {
            BoundedSearch.Result<BoardSearch.Choice> result = search.search(snapshot, budget, seed);
            budget.checkCancellation();
            if (result.move().isEmpty()) return Optional.empty();
            if (considerOffer && result.score().isPresent() && result.score().getAsDouble() <= 0) {
                return Optional.of(new BotRuntime.Decision(actions.acceptDraw(), Map.of()));
            }
            BoardSearch.Choice choice = result.move().get();
            if (choice.claim() == null) return Optional.of(new BotRuntime.Decision(actions.move(), BoardMoveHandler.encode(choice.move())));
            Id action = switch (choice.claim()) { case REPETITION -> actions.repetitionClaim(); case QUIET -> actions.quietClaim(); };
            return Optional.of(new BotRuntime.Decision(action, choice.move() == null ? Map.of() : Map.of("move", BoardMoveHandler.encode(choice.move()))));
        }
    }
}
