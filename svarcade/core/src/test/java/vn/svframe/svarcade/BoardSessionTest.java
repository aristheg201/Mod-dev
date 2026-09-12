package vn.svframe.svarcade;

import org.junit.jupiter.api.Test;
import vn.svframe.svarcade.verification.BoardSessionChecks;

class BoardSessionTest {
    @Test void gameplayIngressAndPersistentHistory() { BoardSessionChecks.main(new String[0]); }
}
