package vn.svframe.svarcade.systems.board;

import java.util.*;
import vn.svframe.svarcade.config.*;

/** Immutable logical grid; representation, world coordinates and Pokemon are not rules. */
public final class GridPosition {
    public record Piece(long identity, Id type, String team, boolean moved) {
        public Piece {
            if (identity < 0 || team == null || team.isBlank() || team.length() > 160) throw new IllegalArgumentException("Invalid logical piece");
            Objects.requireNonNull(type);
        }
        Piece movedAs(Id replacement) { return new Piece(identity, replacement, team, true); }
    }
    public record Trail(int target, int victim, long identity) { }
    private final int width, height;
    private final Piece[] cells;
    private final String turn;
    private final Trail trail;
    private final long ply, quietPlies;
    public GridPosition(int width, int height, String turn, Map<Integer, Piece> pieces, Trail trail, long ply, long quietPlies) {
        if (width < 1 || height < 1 || width > 64 || height > 64 || ply < 0 || quietPlies < 0 || quietPlies > ply) throw new IllegalArgumentException("Invalid grid dimensions or counters");
        if (turn == null || turn.isBlank()) throw new IllegalArgumentException("Missing active team");
        this.width = width; this.height = height; this.turn = turn; this.trail = trail; this.ply = ply; this.quietPlies = quietPlies;
        cells = new Piece[width * height]; Set<Long> identities = new HashSet<>();
        for (var entry : pieces.entrySet()) {
            int square = entry.getKey(); Piece piece = Objects.requireNonNull(entry.getValue());
            if (square < 0 || square >= cells.length || !identities.add(piece.identity())) throw new IllegalArgumentException("Invalid square or duplicate piece identity");
            cells[square] = piece;
        }
        if (trail != null && (trail.target() < 0 || trail.target() >= cells.length || trail.victim() < 0 || trail.victim() >= cells.length
                || cells[trail.target()] != null || cells[trail.victim()] == null || cells[trail.victim()].identity() != trail.identity()
                || cells[trail.victim()].team().equals(turn))) throw new IllegalArgumentException("Invalid transient capture trail");
    }
    GridPosition(GridPosition previous, Piece[] cells, String turn, Trail trail, long quietPlies, long ply) {
        width = previous.width; height = previous.height; this.cells = cells; this.turn = turn; this.trail = trail; this.quietPlies = quietPlies; this.ply = ply;
    }
    public int width() { return width; }
    public int height() { return height; }
    public int size() { return cells.length; }
    public String turn() { return turn; }
    public Trail trail() { return trail; }
    public long ply() { return ply; }
    public long quietPlies() { return quietPlies; }
    public Piece at(int square) {
        if (square < 0 || square >= cells.length) throw new IllegalArgumentException("Square outside board");
        return cells[square];
    }
    public int square(int x, int y) { return x < 0 || y < 0 || x >= width || y >= height ? -1 : y * width + x; }
    Piece[] copyCells() { return cells.clone(); }
    public Map<String, Object> encode() {
        List<Object> pieces = new ArrayList<>();
        for (int i = 0; i < cells.length; i++) {
            Piece p = cells[i]; if (p == null) continue;
            pieces.add(Map.of("square", i, "identity", p.identity(), "type", p.type().toString(), "team", p.team(), "moved", p.moved()));
        }
        Map<String, Object> data = new LinkedHashMap<>(Map.of("width", width, "height", height, "turn", turn, "ply", ply, "quiet_plies", quietPlies, "pieces", pieces));
        if (trail != null) data.put("trail", Map.of("target", trail.target(), "victim", trail.victim(), "identity", trail.identity()));
        return Values.map(data);
    }
    public static GridPosition decode(Node n) {
        n.only("width", "height", "turn", "ply", "quiet_plies", "pieces", "trail");
        int width = (int) n.integer("width", 1, 64), height = (int) n.integer("height", 1, 64);
        Map<Integer, Piece> pieces = new LinkedHashMap<>();
        for (Node p : n.nodes("pieces")) {
            p.only("square", "identity", "type", "team", "moved");
            int square = (int) p.integer("square", 0, width * height - 1);
            Piece piece = new Piece(p.integer("identity", 0, Long.MAX_VALUE), Id.of(p.string("type")), p.string("team"), p.bool("moved", false));
            if (pieces.putIfAbsent(square, piece) != null) throw p.error("square", "Duplicate occupied square");
        }
        Trail trail = null;
        if (n.has("trail")) {
            Node t = n.node("trail"); t.only("target", "victim", "identity");
            trail = new Trail((int) t.integer("target", 0, width * height - 1), (int) t.integer("victim", 0, width * height - 1), t.integer("identity", 0, Long.MAX_VALUE));
        }
        return new GridPosition(width, height, n.string("turn"), pieces, trail, n.integer("ply", 0, Long.MAX_VALUE), n.integer("quiet_plies", 0, Long.MAX_VALUE));
    }
}
