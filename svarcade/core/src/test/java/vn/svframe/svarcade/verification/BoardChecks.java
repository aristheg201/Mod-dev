package vn.svframe.svarcade.verification;

import java.util.*;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.systems.board.*;
import vn.svframe.svarcade.systems.board.MovementRules.*;
import static vn.svframe.svarcade.verification.BoardFixtures.*;
import static vn.svframe.svarcade.verification.Checks.*;

public final class BoardChecks {
    private BoardChecks() { }
    public static long perft(MovementRules rules, GridPosition position, int depth) {
        if (depth == 0) return 1;
        List<Successor> next = rules.successors(position); if (depth == 1) return next.size();
        long count = 0; for (Successor successor : next) count += perft(rules, successor.position(), depth - 1); return count;
    }
    public static void main(String[] ignored) {
        MovementDefinition config = MovementDefinition.parse(data(standard())); MovementRules rules = new MovementRules(config);
        GridPosition start = start(); rules.validate(start); equal(start.encode(), GridPosition.decode(new Node(start.encode(),"state")).encode());
        long[] expected = {1,20,400,8902,197281};
        for (int depth = 0; depth <= 4; depth++) equal(expected[depth], perft(rules, start, depth));
        GridPosition kiwi = fen("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1");
        rules.validate(kiwi); equal(48L,perft(rules,kiwi,1)); equal(2039L,perft(rules,kiwi,2)); equal(97862L,perft(rules,kiwi,3));
        GridPosition sparse = fen("8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1");
        equal(14L,perft(rules,sparse,1)); equal(191L,perft(rules,sparse,2)); equal(2812L,perft(rules,sparse,3));
        GridPosition castles = fen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1");
        equal(2L,rules.successors(castles).stream().filter(s -> s.move().compound()!=null).count());
        GridPosition castled = rules.apply(castles,new Move(square("e1"),square("g1"),null,id("white_short")));
        equal(id("king"),castled.at(square("g1")).type()); equal(id("rook"),castled.at(square("f1")).type()); equal(null,castled.at(square("h1")));
        GridPosition through = fen("4kr2/8/8/8/8/8/8/R3K2R w KQ - 0 1");
        equal(1L,rules.successors(through).stream().filter(s -> s.move().compound()!=null).count());
        GridPosition ep = play(rules,fen("7k/3p4/8/4P3/8/8/8/7K b - - 0 1"),"d7","d5");
        rules.validate(ep); GridPosition captured = play(rules,ep,"e5","d6"); equal(null,captured.at(square("d5"))); equal(0L,captured.quietPlies());
        GridPosition expired = play(rules,play(rules,ep,"h1","h2"),"h8","h7");
        rejects(IllegalArgumentException.class,() -> play(rules,expired,"e5","d6"));
        GridPosition pinned = fen("4r2k/8/8/3pP3/8/8/8/4K3 w - d6 0 2");
        rejects(IllegalArgumentException.class,() -> play(rules,pinned,"e5","d6"));
        Map<String,Object> noTrail = new LinkedHashMap<>(pinned.encode()); noTrail.remove("trail");
        equal(rules.repetitionKey(GridPosition.decode(new Node(noTrail,"position"))),rules.repetitionKey(pinned));
        GridPosition promotion = fen("7k/P7/8/8/8/8/8/7K w - - 0 1");
        equal(4L,rules.successors(promotion).stream().filter(s -> s.move().from()==square("a7")).count());
        rejects(IllegalArgumentException.class,() -> play(rules,promotion,"a7","a8"));
        GridPosition knight = rules.apply(promotion,new Move(square("a7"),square("a8"),id("knight"),null)); equal(id("knight"),knight.at(square("a8")).type());
        GridPosition mate = play(rules,play(rules,play(rules,play(rules,start,"f2","f3"),"e7","e5"),"g2","g4"),"d8","h4");
        equal(true,rules.threatened(mate,"white")); equal(0,rules.successors(mate).size());
        GridPosition stale = fen("7k/5K2/6Q1/8/8/8/8/8 b - - 0 1"); equal(false,rules.threatened(stale,"black")); equal(0,rules.successors(stale).size());
        GridPosition doubleCheck = fen("4r2k/8/8/8/1b6/8/8/4K3 w - - 0 1"); equal(2,rules.attackers(doubleCheck,square("e1"),"white"));
        GridPosition repeated = play(rules,play(rules,play(rules,play(rules,start,"g1","f3"),"g8","f6"),"f3","g1"),"f6","g8");
        equal(rules.repetitionKey(start),rules.repetitionKey(repeated));
        rejects(ConfigException.class,() -> rules.validate(fen("7k/8/8/8/8/8/8/8 w - - 0 1")));
        rejects(IllegalArgumentException.class,() -> new GridPosition(0,8,"white",Map.of(),null,0,0));
        System.out.println("BoardChecks: PASS (perft start=197281/4, Kiwipete=97862/3, legal rays/offsets, castling, en passant, promotion, check/double-check, mate/stalemate, repetition key)");
    }
}
