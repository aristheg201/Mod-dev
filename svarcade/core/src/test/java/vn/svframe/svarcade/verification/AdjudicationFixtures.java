package vn.svframe.svarcade.verification;

import java.util.*;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.systems.board.*;
import vn.svframe.svarcade.systems.board.BoardOutcomeRules.*;
import static vn.svframe.svarcade.verification.BoardFixtures.*;

/** Standard rules are fixture data, never switches in the production engine. */
public final class AdjudicationFixtures {
    private AdjudicationFixtures() { }
    public static Node data() {
        List<Integer> even=new ArrayList<>(),odd=new ArrayList<>();
        for (int s=0;s<64;s++) ((s%8+s/8)%2==0 ? even : odd).add(s);
        Map<String,Object> reasons=new LinkedHashMap<>(); for (Cause cause : Cause.values()) reasons.put(cause.name(),id(cause.name().toLowerCase(Locale.ROOT)).toString());
        List<Object> material=List.of(
                Map.of("types",List.of(),"minimum",0,"maximum",0),
                Map.of("types",List.of(id("knight").toString()),"minimum",1,"maximum",1,"opponent_types",List.of(id("queen").toString())),
                Map.of("types",List.of(id("bishop").toString()),"minimum",1,"maximum",64,"opponent_types",List.of(id("bishop").toString(),id("rook").toString(),id("queen").toString()),"same_class_types",List.of(id("bishop").toString())));
        return new Node(Map.of("repetition_claim",3,"repetition_automatic",5,"quiet_claim_plies",100,"quiet_automatic_plies",150,
                "adjudicate_immobility",true,"allow_draw_offers",true,"allow_resign",true,"reasons",reasons,"material",Map.of("cell_classes",List.of(even,odd),"patterns",material)),"adjudication");
    }
    public static Config config() { return Config.parse(data()); }
}
