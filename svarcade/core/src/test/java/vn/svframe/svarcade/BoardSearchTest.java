package vn.svframe.svarcade;

import org.junit.jupiter.api.Test;
import vn.svframe.svarcade.verification.BoardSearchChecks;

class BoardSearchTest {
    @Test void boardSearchUsesRuntimeRulesAndBoundedSnapshots() { BoardSearchChecks.main(new String[0]); }
}
