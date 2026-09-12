package vn.svframe.svarcade.systems.board;

import java.util.*;
import vn.svframe.svarcade.bot.BoundedSearch;
import vn.svframe.svarcade.config.*;

/** All evaluation weights and search choices come from the definition, not difficulty branches. */
public record BoardSearchTuning(BoundedSearch.Options search, double winScore, double mobility,
                                Map<Id, Double> values, Map<String, Map<Id, List<Double>>> squareTables) {
    public BoardSearchTuning {
        Objects.requireNonNull(search); values = Map.copyOf(values);
        if (!Double.isFinite(winScore) || winScore < 1 || winScore > 100_000_000
                || !Double.isFinite(mobility) || Math.abs(mobility) > 100_000) throw new ConfigException("Evaluation score limits");
        if (values.isEmpty() || values.size() > 256 || squareTables.size() > 32) throw new ConfigException("Evaluation table limits");
        values.values().forEach(v -> bounded(v, 0, 100_000));
        Map<String, Map<Id, List<Double>>> copy = new LinkedHashMap<>();
        squareTables.forEach((team, tables) -> {
            if (team == null || team.isBlank() || tables.size() > 256) throw new ConfigException("Evaluation team/table limits");
            Map<Id, List<Double>> types = new LinkedHashMap<>();
            tables.forEach((id, cells) -> {
                if (cells.size() > 4096) throw new ConfigException("Square evaluation limits");
                cells.forEach(v -> bounded(v, -100_000, 100_000)); types.put(id, List.copyOf(cells));
            });
            copy.put(team, Map.copyOf(types));
        });
        squareTables = Map.copyOf(copy);
    }
    public void validateAgainst(MovementDefinition definition) {
        if (!values.keySet().equals(definition.profiles().keySet())) throw new ConfigException("Evaluation must cover exactly the movement profiles");
        if (!definition.teams().containsAll(squareTables.keySet())) throw new ConfigException("Unknown evaluation team");
        squareTables.values().forEach(tables -> tables.forEach((id, cells) -> {
            if (!values.containsKey(id) || cells.size() != definition.width() * definition.height()) throw new ConfigException("Invalid piece-square table");
        }));
    }
    public double square(String team, Id type, int square) {
        List<Double> cells = squareTables.getOrDefault(team, Map.of()).get(type);
        return cells == null ? 0 : cells.get(square);
    }
    public BoundedSearch.Options options(long seed) {
        return new BoundedSearch.Options(search.depth(), search.alphaBeta(), search.moveOrdering(), search.tableCapacity(),
                search.quiescenceDepth(), search.maxSuccessors(), search.randomness(), seed);
    }
    public static BoardSearchTuning parse(Node n) {
        n.only("search", "evaluation"); Node s = n.node("search"), e = n.node("evaluation");
        s.only("depth", "alpha_beta", "move_ordering", "transposition_table", "table_capacity", "quiescence", "quiescence_depth", "max_successors", "randomness");
        e.only("win_score", "mobility", "piece_values", "square_tables");
        boolean table = s.bool("transposition_table", false), quiet = s.bool("quiescence", false);
        int tableCapacity = s.has("table_capacity") ? (int) s.integer("table_capacity", 0, 200_000) : 0;
        int quietDepth = s.has("quiescence_depth") ? (int) s.integer("quiescence_depth", 0, 16) : 0;
        if (table != (tableCapacity > 0) || quiet != (quietDepth > 0)) throw new ConfigException("Search feature flag and limits disagree");
        BoundedSearch.Options options = new BoundedSearch.Options((int) s.integer("depth", 1, 32), s.bool("alpha_beta", false),
                s.bool("move_ordering", false), tableCapacity, quietDepth, (int) s.integer("max_successors", 1, 8192), number(s.require("randomness"), 0, 1), 0);
        Map<Id, Double> values = new LinkedHashMap<>(); e.node("piece_values").values().forEach((key, value) -> values.put(Id.of(key), number(value, 0, 100_000)));
        Map<String, Map<Id, List<Double>>> squares = new LinkedHashMap<>();
        if (e.has("square_tables")) {
            Node teams = e.node("square_tables");
            for (String team : teams.values().keySet()) {
                Node types = teams.node(team); Map<Id, List<Double>> tables = new LinkedHashMap<>();
                for (String type : types.values().keySet()) tables.put(Id.of(type), types.list(type).stream().map(v -> number(v, -100_000, 100_000)).toList());
                squares.put(team, tables);
            }
        }
        return new BoardSearchTuning(options, number(e.require("win_score"), 1, 100_000_000), number(e.require("mobility"), -100_000, 100_000), values, squares);
    }
    private static double number(Object value, double min, double max) {
        if (!(value instanceof Integer || value instanceof Long || value instanceof Double)) throw new ConfigException("Expected numeric search tuning");
        double number = ((Number) value).doubleValue(); bounded(number, min, max); return number;
    }
    private static void bounded(double value, double min, double max) {
        if (!Double.isFinite(value) || value < min || value > max) throw new ConfigException("Search tuning number out of bounds");
    }
}
