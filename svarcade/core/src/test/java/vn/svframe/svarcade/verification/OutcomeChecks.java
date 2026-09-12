package vn.svframe.svarcade.verification;

import java.util.*;
import vn.svframe.svarcade.systems.board.*;
import vn.svframe.svarcade.systems.board.BoardOutcomeRules.*;
import static vn.svframe.svarcade.verification.BoardFixtures.*;
import static vn.svframe.svarcade.verification.Checks.*;

public final class OutcomeChecks {
    private OutcomeChecks() { }
    public static void main(String[] ignored) {
        Config config=AdjudicationFixtures.config(); BoardOutcomeRules rules=new BoardOutcomeRules(config,standard());
        MaterialRules material=new MaterialRules(standard(),config.material());
        GridPosition kings=fen("7k/8/8/8/8/8/8/7K w - - 0 1"); equal(true,material.insufficientForAll(kings));
        equal(id("material"),rules.automatic(kings,1).orElseThrow().reason());
        equal(true,material.insufficientForAll(fen("7k/8/8/8/8/8/8/N6K w - - 0 1")));
        equal(true,material.insufficientForAll(fen("7k/8/8/8/8/8/8/B6K w - - 0 1")));
        equal(false,material.insufficientForAll(fen("7k/8/8/8/8/8/8/NN5K w - - 0 1")));
        equal(false,material.insufficientForAll(fen("6nk/8/8/8/8/8/8/N6K w - - 0 1")));
        equal(true,material.insufficientForAll(fen("7k/8/8/8/8/8/1b6/B6K w - - 0 1")));
        equal(false,material.insufficientForAll(fen("7k/8/8/8/8/8/b7/B6K w - - 0 1")));
        GridPosition queens=fen("q6k/8/8/8/8/8/8/N6K w - - 0 1");
        equal(true,material.insufficient(queens,"white")); equal(false,material.insufficient(queens,"black"));
        equal(id("timeout_draw"),rules.loss(queens,"black",true).reason()); equal(id("resign_draw"),rules.loss(queens,"black",false).reason());
        equal(id("timeout"),rules.loss(queens,"white",true).reason());
        equal(false,material.insufficient(fen("r6k/8/8/8/8/8/8/N6K w - - 0 1"),"white"));
        GridPosition initial=start(); equal(Optional.empty(),rules.automatic(initial,3));
        equal(id("repetition_claim"),rules.claim(initial,3,Claim.REPETITION).orElseThrow().reason());
        equal(id("repetition_auto"),rules.automatic(initial,5).orElseThrow().reason());
        equal(Optional.empty(),rules.claim(initial,2,Claim.REPETITION));
        GridPosition quiet=fen("7k/8/8/8/8/8/8/R6K w - - 100 51");
        equal(Optional.empty(),rules.automatic(quiet,1)); equal(id("quiet_claim"),rules.claim(quiet,1,Claim.QUIET).orElseThrow().reason());
        GridPosition auto=fen("7k/8/8/8/8/8/8/R6K w - - 150 76"); equal(id("quiet_auto"),rules.automatic(auto,1).orElseThrow().reason());
        GridPosition mate=fen("7k/6Q1/5K2/8/8/8/8/8 b - - 150 76"); equal(id("immobile_threatened"),rules.automatic(mate,5).orElseThrow().reason());
        GridPosition stalemate=fen("7k/5K2/6Q1/8/8/8/8/8 b - - 0 1"); equal(id("immobile_safe"),rules.automatic(stalemate,1).orElseThrow().reason());
        equal(Optional.empty(),rules.claim(mate,5,Claim.QUIET));
        System.out.println("OutcomeChecks: PASS (material proofs, same/opposite cell-class pieces, helpmate material, claim vs automatic thresholds, mate priority, timeout/resignation material exception)");
    }
}
