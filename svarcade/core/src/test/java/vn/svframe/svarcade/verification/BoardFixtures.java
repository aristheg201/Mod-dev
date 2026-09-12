package vn.svframe.svarcade.verification;

import java.util.*;
import java.util.stream.IntStream;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.systems.board.*;
import vn.svframe.svarcade.systems.board.GridPosition.*;
import vn.svframe.svarcade.systems.board.MovementDefinition.*;
import vn.svframe.svarcade.systems.board.MovementDefinition.Vector;

/** Test-only standard chess definition. Production engine never branches on these roles. */
public final class BoardFixtures {
    private BoardFixtures() { }
    public static Id id(String name) { return Id.of("test:" + name); }
    private static List<Vector> vectors(int... xy) {
        List<Vector> out = new ArrayList<>(); for (int i = 0; i < xy.length; i += 2) out.add(new Vector(xy[i], xy[i + 1])); return List.copyOf(out);
    }
    private static Pattern ordinary(Kind kind, List<Vector> vectors, int maximum) {
        return new Pattern(kind, vectors, 1, maximum, Capture.OPTIONAL, false, false, Map.of(), false, false);
    }
    private static Set<Integer> row(int row) { return IntStream.range(row * 8, row * 8 + 8).boxed().collect(java.util.stream.Collectors.toSet()); }
    public static MovementDefinition standard() {
        List<Vector> orthogonal = vectors(1,0,-1,0,0,1,0,-1), diagonal = vectors(1,1,1,-1,-1,1,-1,-1);
        List<Vector> all = new ArrayList<>(orthogonal); all.addAll(diagonal);
        Map<Id, Profile> profiles = new LinkedHashMap<>();
        profiles.put(id("rook"), new Profile(false, true, false, List.of(ordinary(Kind.RAY, orthogonal, 7)), Map.of(), List.of()));
        profiles.put(id("bishop"), new Profile(false, true, false, List.of(ordinary(Kind.RAY, diagonal, 7)), Map.of(), List.of()));
        profiles.put(id("queen"), new Profile(false, true, false, List.of(ordinary(Kind.RAY, all, 7)), Map.of(), List.of()));
        profiles.put(id("king"), new Profile(true, false, false, List.of(ordinary(Kind.OFFSET, all, 1)), Map.of(), List.of()));
        profiles.put(id("knight"), new Profile(false, true, false, List.of(ordinary(Kind.OFFSET, vectors(1,2,2,1,-1,2,-2,1,1,-2,2,-1,-1,-2,-2,-1), 1)), Map.of(), List.of()));
        List<Pattern> pawn = List.of(
                new Pattern(Kind.RAY, vectors(0,1), 1, 1, Capture.NEVER, true, false, Map.of(), false, false),
                new Pattern(Kind.RAY, vectors(0,1), 2, 2, Capture.NEVER, true, true, Map.of("white", row(1), "black", row(6)), true, false),
                new Pattern(Kind.OFFSET, vectors(-1,1,1,1), 1, 1, Capture.ONLY, true, false, Map.of(), false, true));
        profiles.put(id("pawn"), new Profile(false, true, true, pawn, Map.of("white", row(7), "black", row(0)), List.of(id("queen"), id("rook"), id("bishop"), id("knight"))));
        List<Compound> compounds = new ArrayList<>();
        for (String team : List.of("white", "black")) {
            int base = team.equals("white") ? 0 : 56;
            compounds.add(new Compound(id(team + "_short"), team, id("king"), base+4, base+6, id("rook"), base+7, base+5, Set.of(base+5,base+6), List.of(base+4,base+5,base+6), true));
            compounds.add(new Compound(id(team + "_long"), team, id("king"), base+4, base+2, id("rook"), base, base+3, Set.of(base+1,base+2,base+3), List.of(base+4,base+3,base+2), true));
        }
        return new MovementDefinition(8, 8, List.of("white", "black"), Map.of("white", new Vector(0,1), "black", new Vector(0,-1)), true, 1, 4096, 1_000_000, profiles, compounds);
    }
    public static GridPosition start() { return fen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"); }
    public static int square(String name) { return (name.charAt(1) - '1') * 8 + name.charAt(0) - 'a'; }
    public static GridPosition fen(String fen) {
        String[] fields = fen.split(" "); String[] ranks = fields[0].split("/"); Map<Integer, Piece> pieces = new LinkedHashMap<>();
        Map<Character, String> names = Map.of('p',"pawn",'n',"knight",'b',"bishop",'r',"rook",'q',"queen",'k',"king");
        String rights = fields.length > 2 ? fields[2] : "-";
        for (int row = 0; row < 8; row++) {
            int x = 0;
            for (char c : ranks[row].toCharArray()) {
                if (Character.isDigit(c)) { x += c - '0'; continue; }
                int square = (7-row)*8 + x++; String team = Character.isUpperCase(c) ? "white" : "black";
                char lower = Character.toLowerCase(c); boolean moved = true;
                if (lower == 'p') moved = square/8 != (team.equals("white") ? 1 : 6);
                if (c == 'K') moved = !rights.contains("K") && !rights.contains("Q");
                if (c == 'k') moved = !rights.contains("k") && !rights.contains("q");
                if (c == 'R') moved = !(square == 7 && rights.contains("K") || square == 0 && rights.contains("Q"));
                if (c == 'r') moved = !(square == 63 && rights.contains("k") || square == 56 && rights.contains("q"));
                pieces.put(square, new Piece(square, id(names.get(lower)), team, moved));
            }
            if (x != 8) throw new IllegalArgumentException("Test FEN rank width");
        }
        String turn = fields[1].equals("w") ? "white" : "black"; long quiet = fields.length > 4 ? Long.parseLong(fields[4]) : 0;
        long ply = fields.length > 5 ? 2 * (Long.parseLong(fields[5])-1) + (turn.equals("black") ? 1 : 0) : 100;
        Trail trail = null;
        if (fields.length > 3 && !fields[3].equals("-")) {
            int target = square(fields[3]), victim = target + (turn.equals("white") ? -8 : 8);
            trail = new Trail(target, victim, pieces.get(victim).identity()); ply = Math.max(1, ply);
        }
        return new GridPosition(8, 8, turn, pieces, trail, Math.max(ply, quiet), quiet);
    }
    public static GridPosition play(MovementRules rules, GridPosition position, String from, String to) {
        return rules.apply(position, new MovementRules.Move(square(from), square(to), null, null));
    }
    public static Node data(MovementDefinition definition) {
        Map<String,Object> profiles = new LinkedHashMap<>();
        definition.profiles().forEach((id, p) -> {
            List<Object> patterns = new ArrayList<>();
            for (Pattern m : p.patterns()) {
                Map<String,Object> map = new LinkedHashMap<>(Map.of("kind",m.kind().name(),"vectors",m.vectors().stream().map(v -> List.of(v.x(),v.y())).toList(),"min_steps",m.minSteps(),"max_steps",m.maxSteps(),"capture",m.capture().name(),"relative",m.relative(),"unmoved_only",m.unmovedOnly(),"creates_trail",m.createsTrail(),"captures_trail",m.capturesTrail()));
                Map<String,Object> origins = new LinkedHashMap<>(); m.origins().forEach((team, cells) -> origins.put(team, cells.stream().sorted().toList()));
                if (!origins.isEmpty()) map.put("origins",origins); patterns.add(map);
            }
            Map<String,Object> node = new LinkedHashMap<>(Map.of("royal",p.royal(),"capturable",p.capturable(),"resets_quiet_clock",p.resetsQuietClock(),"patterns",patterns));
            if (!p.promotionChoices().isEmpty()) {
                Map<String,Object> cells = new LinkedHashMap<>(); p.promotionSquares().forEach((team, squares) -> cells.put(team,squares.stream().sorted().toList()));
                node.put("promotion_squares",cells); node.put("promotion_choices",p.promotionChoices().stream().map(Id::toString).toList());
            }
            profiles.put(id.toString(),node);
        });
        List<Object> compounds = new ArrayList<>();
        for (Compound c : definition.compounds()) {
            Map<String,Object> node = new LinkedHashMap<>(Map.of("id",c.id().toString(),"team",c.team(),"mover_type",c.moverType().toString(),"from",c.from(),"to",c.to(),"companion_type",c.companionType().toString(),"companion_from",c.companionFrom(),"companion_to",c.companionTo(),"empty",c.emptySquares().stream().sorted().toList(),"safe",c.safeSquares()));
            node.put("unmoved_only",c.unmovedOnly()); compounds.add(node);
        }
        Map<String,Object> forward = new LinkedHashMap<>(); definition.forward().forEach((team,v) -> forward.put(team,List.of(v.x(),v.y())));
        return new Node(Map.of("width",definition.width(),"height",definition.height(),"teams",definition.teams(),"forward",forward,"protect_royals",definition.protectRoyals(),"royals_per_team",definition.royalsPerTeam(),"max_candidates",definition.maxCandidates(),"max_evaluations",definition.maxEvaluations(),"profiles",profiles,"compounds",compounds),"movement");
    }
}
