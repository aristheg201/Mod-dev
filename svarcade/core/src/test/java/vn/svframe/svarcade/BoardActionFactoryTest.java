package vn.svframe.svarcade;

import org.junit.jupiter.api.Test;
import vn.svframe.svarcade.verification.BoardActionFactoryChecks;

class BoardActionFactoryTest {
    @Test void boardActionsCompileDependenciesFromDefinitionData() { BoardActionFactoryChecks.main(new String[0]); }
}
