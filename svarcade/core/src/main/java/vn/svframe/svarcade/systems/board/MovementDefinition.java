package vn.svframe.svarcade.systems.board;

import java.util.*;
import vn.svframe.svarcade.config.*;

/** Compiled movement capabilities. All role names, vectors, promotion and compound paths are data. */
public record MovementDefinition(int width, int height, List<String> teams, Map<String, Vector> forward,
                                 boolean protectRoyals, int royalsPerTeam, int maxCandidates, int maxEvaluations,
                                 Map<Id, Profile> profiles, List<Compound> compounds) {
    public enum Kind { RAY, OFFSET }
    public enum Capture { NEVER, ONLY, OPTIONAL }
    public record Vector(int x, int y) {
        public Vector { if (x == 0 && y == 0 || Math.abs((long) x) > 64 || Math.abs((long) y) > 64) throw new ConfigException("Invalid movement vector"); }
    }
    public record Pattern(Kind kind, List<Vector> vectors, int minSteps, int maxSteps, Capture capture,
                          boolean relative, boolean unmovedOnly, Map<String, Set<Integer>> origins,
                          boolean createsTrail, boolean capturesTrail) {
        public Pattern {
            Objects.requireNonNull(kind); Objects.requireNonNull(capture); vectors = List.copyOf(vectors); origins = immutableSets(origins);
            if (vectors.isEmpty() || vectors.size() > 64 || minSteps < 1 || maxSteps < minSteps || maxSteps > 64
                    || kind == Kind.OFFSET && (minSteps != 1 || maxSteps != 1)) throw new ConfigException("Invalid movement step limits");
            if (createsTrail && (kind != Kind.RAY || minSteps != 2 || maxSteps != 2 || capture != Capture.NEVER)) throw new ConfigException("Trail requires an empty two-step ray");
            if (capturesTrail && capture != Capture.ONLY) throw new ConfigException("Trail capture requires capture-only pattern");
            if (kind == Kind.RAY && vectors.stream().anyMatch(v -> Math.abs(v.x()) > 1 || Math.abs(v.y()) > 1)) throw new ConfigException("Ray vectors must be unit directions");
        }
        boolean allowed(GridPosition.Piece piece, int origin) {
            return (!unmovedOnly || !piece.moved()) && (origins.isEmpty() || origins.getOrDefault(piece.team(), Set.of()).contains(origin));
        }
    }
    public record Profile(boolean royal, boolean capturable, boolean resetsQuietClock, List<Pattern> patterns,
                          Map<String, Set<Integer>> promotionSquares, List<Id> promotionChoices) {
        public Profile {
            patterns = List.copyOf(patterns); promotionSquares = immutableSets(promotionSquares); promotionChoices = List.copyOf(promotionChoices);
            if (patterns.isEmpty() || patterns.size() > 32 || promotionChoices.size() > 32 || (promotionSquares.isEmpty() != promotionChoices.isEmpty())
                    || new HashSet<>(promotionChoices).size() != promotionChoices.size()) throw new ConfigException("Invalid piece profile");
        }
    }
    public record Compound(Id id, String team, Id moverType, int from, int to, Id companionType, int companionFrom,
                           int companionTo, Set<Integer> emptySquares, List<Integer> safeSquares, boolean unmovedOnly) {
        public Compound {
            Objects.requireNonNull(id); Objects.requireNonNull(team); Objects.requireNonNull(moverType); Objects.requireNonNull(companionType);
            emptySquares = Set.copyOf(emptySquares); safeSquares = List.copyOf(safeSquares);
            if (new HashSet<>(List.of(from, to, companionFrom, companionTo)).size() != 4) throw new ConfigException("Compound squares must be distinct");
        }
    }
    private static Map<String, Set<Integer>> immutableSets(Map<String, Set<Integer>> values) {
        Map<String, Set<Integer>> copy = new LinkedHashMap<>(); values.forEach((k, v) -> copy.put(k, Set.copyOf(v))); return Map.copyOf(copy);
    }
    public MovementDefinition {
        if (width < 1 || height < 1 || width > 64 || height > 64 || teams.size() < 2 || teams.size() > 32 || royalsPerTeam < 0 || royalsPerTeam > 32
                || maxCandidates < 1 || maxCandidates > 65_536 || maxEvaluations < 1 || maxEvaluations > 2_000_000 || profiles.isEmpty() || profiles.size() > 256 || compounds.size() > 256) throw new ConfigException("Movement definition limits");
        teams = List.copyOf(teams); forward = Map.copyOf(forward); profiles = Map.copyOf(profiles); compounds = List.copyOf(compounds);
        if (new HashSet<>(teams).size() != teams.size() || !forward.keySet().equals(new HashSet<>(teams))) throw new ConfigException("Movement team/orientation mismatch");
        for (String team : teams) if (team.isBlank() || team.length() > 160) throw new ConfigException("Invalid movement team");
        for (Vector direction : forward.values()) if (Math.abs(direction.x()) + Math.abs(direction.y()) != 1) throw new ConfigException("Forward must be orthogonal unit vector");
        for (Profile p : profiles.values()) {
            if (!profiles.keySet().containsAll(p.promotionChoices())) throw new ConfigException("Unknown promotion type");
            validateSquares(p.promotionSquares(), teams, width * height);
            for (Pattern pattern : p.patterns()) validateSquares(pattern.origins(), teams, width * height);
        }
        Set<Id> ids = new HashSet<>();
        for (Compound c : compounds) {
            if (!ids.add(c.id()) || !teams.contains(c.team()) || !profiles.containsKey(c.moverType()) || !profiles.containsKey(c.companionType())) throw new ConfigException("Invalid compound reference");
            List<Integer> squares = new ArrayList<>(List.of(c.from(), c.to(), c.companionFrom(), c.companionTo())); squares.addAll(c.emptySquares()); squares.addAll(c.safeSquares());
            for (int s : squares) if (s < 0 || s >= width * height) throw new ConfigException("Compound square outside board");
            if (!c.emptySquares().containsAll(List.of(c.to(), c.companionTo())) || c.emptySquares().contains(c.from()) || c.emptySquares().contains(c.companionFrom())) throw new ConfigException("Invalid compound empty squares");
        }
    }
    private static void validateSquares(Map<String, Set<Integer>> values, List<String> teams, int size) {
        if (!teams.containsAll(values.keySet())) throw new ConfigException("Unknown team in square condition");
        for (Set<Integer> squares : values.values()) for (int s : squares) if (s < 0 || s >= size) throw new ConfigException("Condition square outside board");
    }
    private static Vector vector(Object value) {
        if (!(value instanceof List<?> list) || list.size() != 2 || !(list.get(0) instanceof Integer x) || !(list.get(1) instanceof Integer y)) throw new ConfigException("Vector requires two integers");
        return new Vector(x, y);
    }
    private static Map<String, Set<Integer>> squares(Node n, String field, int size) {
        if (!n.has(field)) return Map.of(); Map<String, Set<Integer>> result = new LinkedHashMap<>(); Node values = n.node(field);
        for (String team : values.values().keySet()) result.put(team, integerSet(values.list(team), size)); return result;
    }
    private static Set<Integer> integerSet(List<?> values, int size) {
        Set<Integer> result = new LinkedHashSet<>();
        for (Object value : values) if (!(value instanceof Integer i) || i < 0 || i >= size || !result.add(i)) throw new ConfigException("Expected unique board-square indices");
        return result;
    }
    public static MovementDefinition parse(Node n) {
        n.only("width", "height", "teams", "forward", "protect_royals", "royals_per_team", "max_candidates", "max_evaluations", "profiles", "compounds");
        int width = (int) n.integer("width", 1, 64), height = (int) n.integer("height", 1, 64), size = width * height;
        List<String> teams = List.copyOf(n.strings("teams")); Map<String, Vector> forward = new LinkedHashMap<>();
        n.node("forward").values().forEach((team, value) -> forward.put(team, vector(value)));
        Map<Id, Profile> profiles = new LinkedHashMap<>(); Node types = n.node("profiles");
        for (String name : types.values().keySet()) {
            Node p = types.node(name); p.only("royal", "capturable", "resets_quiet_clock", "patterns", "promotion_squares", "promotion_choices"); List<Pattern> patterns = new ArrayList<>();
            for (Node m : p.nodes("patterns")) {
                m.only("kind", "vectors", "min_steps", "max_steps", "capture", "relative", "unmoved_only", "origins", "creates_trail", "captures_trail");
                patterns.add(new Pattern(Kind.valueOf(m.string("kind")), m.list("vectors").stream().map(MovementDefinition::vector).toList(),
                        (int) m.integer("min_steps", 1, 64), (int) m.integer("max_steps", 1, 64), Capture.valueOf(m.string("capture")),
                        m.bool("relative", false), m.bool("unmoved_only", false), squares(m, "origins", size), m.bool("creates_trail", false), m.bool("captures_trail", false)));
            }
            profiles.put(Id.of(name), new Profile(p.bool("royal", false), p.bool("capturable", true), p.bool("resets_quiet_clock", false), patterns,
                    squares(p, "promotion_squares", size), p.has("promotion_choices") ? p.strings("promotion_choices").stream().map(Id::of).toList() : List.of()));
        }
        List<Compound> compounds = new ArrayList<>();
        if (n.has("compounds")) for (Node c : n.nodes("compounds")) {
            c.only("id", "team", "mover_type", "from", "to", "companion_type", "companion_from", "companion_to", "empty", "safe", "unmoved_only");
            compounds.add(new Compound(Id.of(c.string("id")), c.string("team"), Id.of(c.string("mover_type")), (int) c.integer("from", 0, size - 1), (int) c.integer("to", 0, size - 1),
                    Id.of(c.string("companion_type")), (int) c.integer("companion_from", 0, size - 1), (int) c.integer("companion_to", 0, size - 1),
                    integerSet(c.list("empty"), size), List.copyOf(integerSet(c.list("safe"), size)), c.bool("unmoved_only", true)));
        }
        return new MovementDefinition(width, height, teams, forward, n.bool("protect_royals", true), (int) n.integer("royals_per_team", 0, 32),
                (int) n.integer("max_candidates", 1, 65_536), (int) n.integer("max_evaluations", 1, 2_000_000), profiles, compounds);
    }
}
