package vn.svframe.svarcade.systems.board;

import java.util.*;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.systems.board.GridPosition.Piece;

/** Authored proofs of insufficient material, independent of named pieces and board coloring. */
public final class MaterialRules {
    public record Pattern(Set<Id> types, int minimum, int maximum, Optional<Set<Id>> opponentTypes, Set<Id> sameClassTypes) {
        public Pattern {
            types = Set.copyOf(types); opponentTypes = opponentTypes.map(Set::copyOf); sameClassTypes = Set.copyOf(sameClassTypes);
            if (minimum < 0 || maximum < minimum || maximum > 4096) throw new ConfigException("Material count bounds");
        }
    }
    public record Config(List<Set<Integer>> cellClasses, List<Pattern> patterns) {
        public Config {
            cellClasses = cellClasses.stream().map(Set::copyOf).toList(); patterns = List.copyOf(patterns);
            if (cellClasses.size() > 256 || patterns.size() > 128) throw new ConfigException("Material rule limits");
        }
        public static Config parse(Node n) {
            n.only("cell_classes", "patterns"); List<Set<Integer>> classes = new ArrayList<>();
            for (Object raw : n.list("cell_classes")) {
                if (!(raw instanceof List<?> squares)) throw new ConfigException("Cell class must list square indices");
                Set<Integer> cells = new LinkedHashSet<>();
                for (Object square : squares) if (!(square instanceof Integer i) || i < 0 || i >= 4096 || !cells.add(i)) throw new ConfigException("Invalid or duplicate cell class square");
                classes.add(cells);
            }
            List<Pattern> patterns = new ArrayList<>();
            for (Node p : n.nodes("patterns")) {
                p.only("types", "minimum", "maximum", "opponent_types", "same_class_types");
                patterns.add(new Pattern(ids(p,"types"), (int) p.integer("minimum",0,4096), (int) p.integer("maximum",0,4096),
                        p.has("opponent_types") ? Optional.of(ids(p,"opponent_types")) : Optional.empty(), p.has("same_class_types") ? ids(p,"same_class_types") : Set.of()));
            }
            return new Config(classes,patterns);
        }
        private static Set<Id> ids(Node n, String field) {
            Set<Id> ids = new LinkedHashSet<>(); n.strings(field).forEach(s -> ids.add(Id.of(s))); return Set.copyOf(ids);
        }
    }
    private final MovementDefinition definition;
    private final List<Pattern> patterns;
    private final int[] cellClasses;
    public MaterialRules(MovementDefinition definition, Config config) {
        this.definition = Objects.requireNonNull(definition); patterns = config.patterns(); cellClasses = new int[definition.width()*definition.height()]; Arrays.fill(cellClasses,-1);
        for (int i=0; i<config.cellClasses().size(); i++) for (int square : config.cellClasses().get(i)) {
            if (square < 0 || square >= cellClasses.length || cellClasses[square] != -1) throw new ConfigException("Cell partition overlaps or escapes board");
            cellClasses[square] = i;
        }
        boolean partitionUsed = false;
        for (Pattern p : patterns) {
            validate(p.types()); p.opponentTypes().ifPresent(this::validate); validate(p.sameClassTypes());
            partitionUsed |= !p.sameClassTypes().isEmpty();
        }
        if (partitionUsed) for (int value : cellClasses) if (value < 0) throw new ConfigException("Material cell partition must cover the board");
    }
    private void validate(Set<Id> types) {
        for (Id id : types) if (!definition.profiles().containsKey(id) || definition.profiles().get(id).royal()) throw new ConfigException("Material pattern requires a registered non-royal type: " + id);
    }
    public boolean insufficient(GridPosition position, String team) { return insufficient(position, team, () -> { }); }
    public boolean insufficient(GridPosition position, String team, Runnable checkpoint) {
        Objects.requireNonNull(checkpoint).run();
        if (!definition.teams().contains(team) || position.width()!=definition.width() || position.height()!=definition.height()) throw new IllegalArgumentException("Material board/team mismatch");
        Set<Id> subject = new HashSet<>(), opponent = new HashSet<>(); Map<Id,Integer> classes = new HashMap<>(); int count=0;
        for (int square=0; square<position.size(); square++) {
            checkpoint.run(); Piece piece=position.at(square); if (piece==null) continue;
            MovementDefinition.Profile profile=definition.profiles().get(piece.type()); if (profile==null) throw new ConfigException("Unknown material type");
            if (profile.royal()) continue;
            if (piece.team().equals(team)) { subject.add(piece.type()); count++; } else opponent.add(piece.type());
            int cell=cellClasses[square]; classes.merge(piece.type(),cell,(a,b) -> a.equals(b) ? a : -2);
        }
        for (Pattern pattern : patterns) {
            checkpoint.run();
            if (count<pattern.minimum() || count>pattern.maximum() || !pattern.types().containsAll(subject)
                    || pattern.opponentTypes().isPresent() && !pattern.opponentTypes().get().containsAll(opponent)) continue;
            int common=-1; boolean matches=true;
            for (Id type : pattern.sameClassTypes()) {
                Integer value=classes.get(type); if (value==null) continue;
                if (value<0 || common>=0 && common!=value) { matches=false; break; }
                common=value;
            }
            if (matches) return true;
        }
        return false;
    }
    public boolean insufficientForAll(GridPosition position) { return insufficientForAll(position, () -> { }); }
    public boolean insufficientForAll(GridPosition position, Runnable checkpoint) {
        for (String team : definition.teams()) if (!insufficient(position,team,checkpoint)) return false;
        return true;
    }
}
