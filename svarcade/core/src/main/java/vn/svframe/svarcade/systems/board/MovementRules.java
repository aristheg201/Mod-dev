package vn.svframe.svarcade.systems.board;

import java.util.*;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.systems.board.GridPosition.*;
import vn.svframe.svarcade.systems.board.MovementDefinition.*;
import vn.svframe.svarcade.systems.board.MovementDefinition.Vector;

/** Pure bounded grid-rule evaluator. There are no game, species, or named piece branches. */
public final class MovementRules {
    public record Move(int from, int to, Id promotion, Id compound) { }
    public record Successor(Move move, GridPosition position, int captureSquare) { }
    private record Candidate(Move move, int capture, int companionFrom, int companionTo, int trailTarget) { }
    public static final class BudgetExceeded extends IllegalStateException {
        private static final long serialVersionUID = 1L;
        BudgetExceeded() { super("Movement evaluation budget exceeded"); }
    }
    private static final class Work {
        private int remaining;
        private final Runnable checkpoint;
        Work(int remaining) { this(remaining, () -> { }); }
        Work(int remaining, Runnable checkpoint) { this.remaining = remaining; this.checkpoint = Objects.requireNonNull(checkpoint); checkpoint.run(); }
        void spend() {
            if (--remaining < 0) throw new BudgetExceeded();
            if ((remaining & 63) == 0) checkpoint.run();
        }
    }
    private final MovementDefinition definition;
    public MovementRules(MovementDefinition definition) { this.definition = Objects.requireNonNull(definition); }
    public MovementDefinition definition() { return definition; }
    private Profile profile(Piece p) {
        Profile profile = definition.profiles().get(p.type());
        if (profile == null) throw new ConfigException("Unknown piece type: " + p.type());
        return profile;
    }
    public void validate(GridPosition position) {
        requireShape(position); Map<String, Integer> royals = new HashMap<>();
        for (int square = 0; square < position.size(); square++) {
            Piece piece = position.at(square); if (piece == null) continue;
            if (!definition.teams().contains(piece.team())) throw new ConfigException("Unknown piece team");
            Profile profile = profile(piece); if (profile.royal()) royals.merge(piece.team(), 1, Integer::sum);
            if (profile.promotionSquares().getOrDefault(piece.team(), Set.of()).contains(square)) throw new ConfigException("Unpromoted piece on mandatory promotion square");
        }
        if (definition.royalsPerTeam() > 0) for (String team : definition.teams()) {
            if (royals.getOrDefault(team, 0) != definition.royalsPerTeam()) throw new ConfigException("Invalid protected-piece count for " + team);
        }
        if (position.trail() != null) {
            Trail trail = position.trail(); Piece victim = position.at(trail.victim()); boolean authored = false;
            for (Pattern pattern : profile(victim).patterns()) if (pattern.createsTrail()) for (Vector vector : pattern.vectors()) {
                Vector d = direction(pattern, vector, victim.team());
                int from = position.square(trail.victim() % position.width() - 2 * d.x(), trail.victim() / position.width() - 2 * d.y());
                int middle = position.square(trail.victim() % position.width() - d.x(), trail.victim() / position.width() - d.y());
                if (from >= 0 && middle == trail.target() && position.at(from) == null && pattern.allowed(new Piece(victim.identity(), victim.type(), victim.team(), false), from)) authored = true;
            }
            if (!authored || !victim.moved() || position.ply() == 0) throw new ConfigException("Persisted trail has no authored origin");
        }
    }
    private void requireShape(GridPosition p) {
        if (p.width() != definition.width() || p.height() != definition.height() || !definition.teams().contains(p.turn())) throw new ConfigException("Board and movement definition mismatch");
    }
    private Vector direction(Pattern pattern, Vector vector, String team) {
        if (!pattern.relative()) return vector;
        Vector forward = definition.forward().get(team);
        return new Vector(vector.x() * forward.y() + vector.y() * forward.x(), -vector.x() * forward.x() + vector.y() * forward.y());
    }
    public List<Successor> successors(GridPosition position) { return successors(position, () -> { }); }
    /** Search cancellation/deadlines are checked inside move generation, not only between nodes. */
    public List<Successor> successors(GridPosition position, Runnable checkpoint) {
        requireShape(position); Work work = new Work(definition.maxEvaluations(), checkpoint);
        Map<Move, Candidate> candidates = new LinkedHashMap<>();
        for (int from = 0; from < position.size(); from++) {
            work.spend(); Piece piece = position.at(from);
            if (piece == null || !piece.team().equals(position.turn())) continue;
            for (Pattern pattern : profile(piece).patterns()) {
                if (!pattern.allowed(piece, from)) continue;
                for (Vector vector : pattern.vectors()) {
                    Vector direction = direction(pattern, vector, piece.team());
                    for (int step = 1; step <= pattern.maxSteps(); step++) {
                        work.spend(); int to = position.square(from % position.width() + direction.x() * step, from / position.width() + direction.y() * step);
                        if (to < 0) break; Piece target = position.at(to); int captured = -1;
                        boolean allowed = false;
                        if (step >= pattern.minSteps()) {
                            if (target == null) {
                                allowed = pattern.capture() != Capture.ONLY;
                                Trail trail = position.trail();
                                if (pattern.capturesTrail() && trail != null && trail.target() == to) {
                                    Piece victim = position.at(trail.victim());
                                    if (victim != null && victim.identity() == trail.identity() && !victim.team().equals(piece.team()) && profile(victim).capturable()) { allowed = true; captured = trail.victim(); }
                                }
                            } else if (!target.team().equals(piece.team()) && pattern.capture() != Capture.NEVER && profile(target).capturable()) { allowed = true; captured = to; }
                            if (allowed) {
                                int trailTarget = pattern.createsTrail() ? position.square(from % position.width() + direction.x(), from / position.width() + direction.y()) : -1;
                                addPromotions(candidates, piece, from, to, captured, trailTarget);
                            }
                        }
                        if (target != null) break;
                    }
                }
            }
        }
        for (Compound compound : definition.compounds()) {
            work.spend(); if (!compound.team().equals(position.turn()) || !compoundRight(position, compound)) continue;
            boolean clear = true;
            for (int square : compound.emptySquares()) if (position.at(square) != null) { clear = false; break; }
            if (!clear) continue;
            for (int square : compound.safeSquares()) {
                work.spend(); Piece[] cells = position.copyCells(); Piece moving = cells[compound.from()]; cells[compound.from()] = null; cells[square] = moving;
                GridPosition transit = new GridPosition(position, cells, position.turn(), null, position.quietPlies(), position.ply());
                if (attacked(transit, square, compound.team(), work)) { clear = false; break; }
            }
            if (clear) add(candidates, new Candidate(new Move(compound.from(), compound.to(), null, compound.id()), -1, compound.companionFrom(), compound.companionTo(), -1));
        }
        List<Successor> legal = new ArrayList<>();
        for (Candidate candidate : candidates.values()) {
            work.spend();
            GridPosition next = applyCandidate(position, candidate);
            if (!definition.protectRoyals() || safe(next, position.turn(), work)) legal.add(new Successor(candidate.move(), next, candidate.capture()));
        }
        return List.copyOf(legal);
    }
    private void addPromotions(Map<Move, Candidate> output, Piece piece, int from, int to, int capture, int trail) {
        Profile p = profile(piece);
        if (p.promotionSquares().getOrDefault(piece.team(), Set.of()).contains(to)) {
            for (Id promotion : p.promotionChoices()) add(output, new Candidate(new Move(from, to, promotion, null), capture, -1, -1, trail));
        } else add(output, new Candidate(new Move(from, to, null, null), capture, -1, -1, trail));
    }
    private void add(Map<Move, Candidate> output, Candidate candidate) {
        Candidate previous = output.putIfAbsent(candidate.move(), candidate);
        if (previous != null && !previous.equals(candidate)) throw new ConfigException("Ambiguous movement effects");
        if (output.size() > definition.maxCandidates()) throw new BudgetExceeded();
    }
    public boolean compoundRight(GridPosition p, Compound c) {
        Piece mover = p.at(c.from()), companion = p.at(c.companionFrom());
        return mover != null && companion != null && mover.team().equals(c.team()) && companion.team().equals(c.team())
                && mover.type().equals(c.moverType()) && companion.type().equals(c.companionType()) && (!c.unmovedOnly() || !mover.moved() && !companion.moved());
    }
    public GridPosition apply(GridPosition position, Move move) {
        return successors(position).stream().filter(s -> s.move().equals(move)).findFirst().orElseThrow(() -> new IllegalArgumentException("Illegal movement intent")).position();
    }
    private GridPosition applyCandidate(GridPosition position, Candidate candidate) {
        Move move = candidate.move(); Piece[] cells = position.copyCells(); Piece moving = cells[move.from()]; cells[move.from()] = null;
        if (candidate.capture() >= 0) cells[candidate.capture()] = null;
        cells[move.to()] = moving.movedAs(move.promotion() == null ? moving.type() : move.promotion());
        if (candidate.companionFrom() >= 0) {
            Piece companion = cells[candidate.companionFrom()]; cells[candidate.companionFrom()] = null; cells[candidate.companionTo()] = companion.movedAs(companion.type());
        }
        String nextTeam = definition.teams().get((definition.teams().indexOf(position.turn()) + 1) % definition.teams().size());
        Trail trail = candidate.trailTarget() < 0 ? null : new Trail(candidate.trailTarget(), move.to(), moving.identity());
        long quiet = candidate.capture() >= 0 || profile(moving).resetsQuietClock() ? 0 : Math.incrementExact(position.quietPlies());
        return new GridPosition(position, cells, nextTeam, trail, quiet, Math.incrementExact(position.ply()));
    }
    private boolean safe(GridPosition position, String team, Work work) {
        for (int square = 0; square < position.size(); square++) {
            work.spend(); Piece piece = position.at(square);
            if (piece != null && piece.team().equals(team) && profile(piece).royal() && attacked(position, square, team, work)) return false;
        }
        return true;
    }
    public int attackers(GridPosition position, int square, String defendedTeam) {
        requireShape(position); position.at(square); Work work = new Work(definition.maxEvaluations()); int count = 0;
        for (int from = 0; from < position.size(); from++) {
            Piece piece = position.at(from);
            if (piece != null && !piece.team().equals(defendedTeam) && attacks(position, from, square, piece, work)) count++;
        }
        return count;
    }
    public boolean threatened(GridPosition position, String team) { return threatened(position, team, () -> { }); }
    public boolean threatened(GridPosition position, String team, Runnable checkpoint) {
        requireShape(position); return !safe(position, team, new Work(definition.maxEvaluations(), checkpoint));
    }
    private boolean attacked(GridPosition position, int square, String team, Work work) {
        for (int from = 0; from < position.size(); from++) {
            work.spend(); Piece piece = position.at(from);
            if (piece != null && !piece.team().equals(team) && attacks(position, from, square, piece, work)) return true;
        }
        return false;
    }
    private boolean attacks(GridPosition position, int from, int target, Piece piece, Work work) {
        int dx = target % position.width() - from % position.width(), dy = target / position.width() - from / position.width();
        for (Pattern pattern : profile(piece).patterns()) {
            if (pattern.capture() == Capture.NEVER || !pattern.allowed(piece, from)) continue;
            for (Vector vector : pattern.vectors()) {
                work.spend(); Vector direction = direction(pattern, vector, piece.team());
                if (pattern.kind() == Kind.OFFSET) { if (dx == direction.x() && dy == direction.y()) return true; continue; }
                int steps = direction.x() == 0 ? dy / direction.y() : dx / direction.x();
                if (steps < pattern.minSteps() || steps > pattern.maxSteps() || direction.x() * steps != dx || direction.y() * steps != dy) continue;
                boolean blocked = false;
                for (int step = 1; step < steps; step++) {
                    work.spend(); int square = position.square(from % position.width() + direction.x() * step, from / position.width() + direction.y() * step);
                    if (position.at(square) != null) { blocked = true; break; }
                }
                if (!blocked) return true;
            }
        }
        return false;
    }
    /** Position equality ignores identities and irrelevant moved flags, retaining effective rights. */
    public String repetitionKey(GridPosition position) { return repetitionKey(position, () -> { }); }
    public String repetitionKey(GridPosition position, Runnable checkpoint) {
        Objects.requireNonNull(checkpoint).run();
        requireShape(position); StringBuilder key = new StringBuilder().append(position.width()).append('x').append(position.height()).append('|'); token(key, position.turn());
        for (int square = 0; square < position.size(); square++) {
            checkpoint.run(); Piece p = position.at(square); if (p == null) continue;
            key.append(square).append('@'); token(key, p.type().toString()); token(key, p.team());
            for (Pattern pattern : profile(p).patterns()) if (pattern.unmovedOnly() && pattern.allowed(new Piece(p.identity(), p.type(), p.team(), false), square)) {
                key.append(p.moved() ? "m1;" : "m0;"); break;
            }
        }
        for (Compound c : definition.compounds()) if (compoundRight(position, c)) { key.append('c'); token(key, c.id().toString()); }
        if (position.trail() != null && successors(position, checkpoint).stream().anyMatch(s -> s.captureSquare() == position.trail().victim() && s.move().to() == position.trail().target())) key.append("|trail:").append(position.trail().target());
        return key.toString();
    }
    private static void token(StringBuilder output, String value) { output.append(value.length()).append(':').append(value).append(';'); }
}
