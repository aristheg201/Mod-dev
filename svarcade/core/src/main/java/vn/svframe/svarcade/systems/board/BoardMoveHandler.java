package vn.svframe.svarcade.systems.board;

import java.util.*;
import java.util.function.BooleanSupplier;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.runtime.*;
import vn.svframe.svarcade.security.*;
import vn.svframe.svarcade.systems.board.MovementRules.Move;
import vn.svframe.svarcade.systems.turn.TurnAccess;

/** Intent adapter only. Human and bot moves both execute through ActionDispatcher. */
public final class BoardMoveHandler implements ActionDispatcher.Handler {
    private final BoardAccess board;
    private final BooleanSupplier phaseAllows;
    private final Id effect;
    private final TurnAccess clock;
    public BoardMoveHandler(BoardAccess board, BooleanSupplier phaseAllows, Id effect) {
        this(board, null, phaseAllows, effect);
    }
    public BoardMoveHandler(BoardAccess board, TurnAccess clock, BooleanSupplier phaseAllows, Id effect) {
        this.board = Objects.requireNonNull(board); this.clock = clock; this.phaseAllows = Objects.requireNonNull(phaseAllows); this.effect = Objects.requireNonNull(effect);
    }
    public static Move decode(Map<String, Object> payload, int size) {
        Node n = new Node(payload, "move-intent"); n.only("from", "to", "promotion", "compound");
        return new Move((int) n.integer("from", 0, size - 1), (int) n.integer("to", 0, size - 1),
                n.has("promotion") ? Id.of(n.string("promotion")) : null, n.has("compound") ? Id.of(n.string("compound")) : null);
    }
    public static Map<String, Object> encode(Move move) {
        Map<String, Object> data = new LinkedHashMap<>(Map.of("from", move.from(), "to", move.to()));
        if (move.promotion() != null) data.put("promotion", move.promotion().toString());
        if (move.compound() != null) data.put("compound", move.compound().toString()); return Map.copyOf(data);
    }
    @Override public Optional<String> reject(GenericSession session, IntentGate.Facts facts, IntentGate.Intent intent) {
        if (!phaseAllows.getAsBoolean()) return Optional.of("phase");
        if (clock != null && (!clock.running() || clock.expired())) return Optional.of("clock");
        Participant actor = session.participants().get(facts.actor());
        if (actor == null || !actor.team().equals(board.position().turn())) return Optional.of("turn");
        return Optional.empty();
    }
    @Override public ActionDispatcher.Prepared prepare(GenericSession session, IntentGate.Facts facts, IntentGate.Intent intent) {
        Move move = decode(intent.payload(), board.position().size()); String turn = board.position().turn();
        BoardAccess.MoveChange boardChange = board.prepareMove(facts.actor(), move);
        StateChange change = clock == null ? boardChange : new CompositeChange(session.thread(), List.of(boardChange, clock.preparePass(turn, boardChange.result().turn())));
        return new ActionDispatcher.Prepared(change::apply, change::rollback, List.of(new ActionDispatcher.Effect(effect, encode(move))));
    }
}
