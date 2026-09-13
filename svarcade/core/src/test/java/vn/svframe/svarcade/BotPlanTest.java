package vn.svframe.svarcade;

import org.junit.jupiter.api.Test;
import vn.svframe.svarcade.verification.BotPlanChecks;

class BotPlanTest {
    @Test void botSchedulerDependenciesAreDefinitionCompiled() { BotPlanChecks.main(new String[0]); }
}
