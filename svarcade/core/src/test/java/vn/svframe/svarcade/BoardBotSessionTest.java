package vn.svframe.svarcade;

import org.junit.jupiter.api.Test;
import vn.svframe.svarcade.verification.BoardBotSessionChecks;

class BoardBotSessionTest {
    @Test void allDifficultiesUseRealBoardClockAndActionSystems() throws Exception { BoardBotSessionChecks.main(new String[0]); }
}
