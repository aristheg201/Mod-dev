package vn.svframe.svarcade.systems.board;

import java.util.*;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.runtime.*;
import vn.svframe.svarcade.security.*;

/** Generic data-bound board selection controller. No game identity or platform coordinates live here. */
public final class BoardInputSystem implements SessionSystem, BoardInputAccess {
    public static final Id ID = Id.of("svarcade:board_input");
    public static final SessionServices.Key<BoardInputAccess> ACCESS = new SessionServices.Key<>(ID, BoardInputAccess.class);

    private record Config(Id action) {
        static Config parse(Node n) { n.only("action"); return new Config(Id.of(n.string("action"))); }
    }
    public static final class Plan implements SystemSchema, SystemFactory {
        @Override public void validate(Node config) { Config.parse(config); }
        @Override public Set<Id> dependencies(Node config) { return Set.of(BoardSystem.ID, ActionSystem.ID); }
        @Override public Set<SessionServices.Key<?>> requires(Node config) { return Set.of(BoardSystem.ACCESS, ActionSystem.ACCESS); }
        @Override public Set<SessionServices.Key<?>> provides() { return Set.of(ACCESS); }
        @Override public SessionSystem create(GenericSession session, Node config) {
            Config parsed = Config.parse(config); ActionAccess actions = session.services().require(ActionSystem.ACCESS);
            if (!actions.actions().contains(parsed.action())) throw new ConfigException("Board input action is not configured: " + parsed.action());
            BoardInputSystem system = new BoardInputSystem(session, session.services().require(BoardSystem.ACCESS), actions, parsed.action());
            session.services().provide(ACCESS, system); return system;
        }
    }

    private final GenericSession session;
    private final BoardAccess board;
    private final ActionAccess actions;
    private final Id action;
    private final Map<UUID,Integer> selected = new LinkedHashMap<>();
    private final Map<UUID,Long> sequences = new LinkedHashMap<>();
    private final Map<UUID,UUID> controllers = new LinkedHashMap<>();
    private boolean active, closed;

    private BoardInputSystem(GenericSession session, BoardAccess board, ActionAccess actions, Id action) {
        this.session = session; this.board = board; this.actions = actions; this.action = action;
    }
    @Override public void start() {
        session.thread().check(); if (active || closed) throw new IllegalStateException("Board input already initialized");
        issueControllers(); active = true;
    }
    private void issueControllers() {
        for (Participant participant : session.participants().values()) if (participant.kind() == Participant.Kind.PLAYER)
            controllers.put(participant.id(), actions.issueController(participant.id(), Long.MAX_VALUE));
    }
    private void requireActive() { session.thread().check(); if (!active || closed) throw new IllegalStateException("Board input inactive"); }

    @Override public Result click(IntentGate.Facts facts, int square, boolean cancel) {
        requireActive(); UUID actor = facts.actor(); Participant participant = session.participants().get(actor);
        if (participant == null || participant.kind() != Participant.Kind.PLAYER) return new Result(Status.REJECTED, -1, "membership");
        if (cancel) { Integer previous = selected.remove(actor); return new Result(Status.CANCELLED, previous == null ? -1 : previous, "cancelled"); }
        GridPosition position = board.position();
        if (square < 0 || square >= position.size()) return new Result(Status.REJECTED, -1, "square");
        Integer from = selected.get(actor);
        if (from == null) {
            GridPosition.Piece piece = position.at(square);
            if (piece == null || !piece.team().equals(participant.team()) || !position.turn().equals(participant.team()))
                return new Result(Status.REJECTED, -1, "ownership");
            selected.put(actor, square); session.changed(); return new Result(Status.SELECTED, square, "selected");
        }
        UUID controller = controllers.get(actor); if (controller == null) return new Result(Status.REJECTED, from, "controller");
        long sequence = Math.incrementExact(sequences.getOrDefault(actor, -1L));
        IntentGate.Intent intent = new IntentGate.Intent(session.id(), controller, sequence, session.revision(), action, Map.of("from", from, "to", square));
        IntentGate.Result dispatched = actions.dispatchHuman(facts, intent);
        if (!dispatched.accepted()) return new Result(Status.REJECTED, from, dispatched.reason());
        sequences.put(actor, sequence); selected.remove(actor); return new Result(Status.MOVED, -1, "accepted");
    }

    @Override public void clear(UUID actor) { requireActive(); selected.remove(Objects.requireNonNull(actor)); }
    @Override public void tick(long tick) { requireActive(); }
    @Override public int stateSchema() { return 1; }
    @Override public Map<String,Object> snapshot() {
        requireActive(); List<Object> rows = new ArrayList<>();
        for (Participant participant : session.participants().values()) if (participant.kind() == Participant.Kind.PLAYER) {
            Map<String,Object> row = new LinkedHashMap<>(); row.put("actor", participant.id().toString());
            row.put("sequence", sequences.getOrDefault(participant.id(), -1L));
            Integer square = selected.get(participant.id()); if (square != null) row.put("selected", square); rows.add(row);
        }
        return Values.map(Map.of("players", rows));
    }
    @Override public void restore(int schema, Map<String,Object> state) {
        session.thread().check(); if (active || closed || schema != 1) throw new ConfigException("Invalid board input restore");
        Node n = new Node(state, "board-input-state"); n.only("players");
        for (Node row : n.nodes("players")) {
            row.only("actor", "sequence", "selected"); UUID actor = UUID.fromString(row.string("actor")); Participant participant = session.participants().get(actor);
            if (participant == null || participant.kind() != Participant.Kind.PLAYER) throw new ConfigException("Restored board input actor unavailable");
            long sequence = row.integer("sequence", -1, Long.MAX_VALUE - 1); sequences.put(actor, sequence);
            if (row.has("selected")) {
                int square = (int) row.integer("selected", 0, board.position().size() - 1); GridPosition.Piece piece = board.position().at(square);
                if (piece == null || !piece.team().equals(participant.team())) throw new ConfigException("Invalid restored board selection"); selected.put(actor, square);
            }
        }
        issueControllers(); active = true;
    }
    @Override public void close() {
        session.thread().check(); if (closed) return; closed = true; active = false;
        for (UUID actor : new ArrayList<>(controllers.keySet())) actions.revokeController(actor);
        controllers.clear(); selected.clear(); sequences.clear();
    }
}
