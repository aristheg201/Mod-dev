package vn.svframe.svarcade;

import org.junit.jupiter.api.Test;
import vn.svframe.svarcade.verification.BotSystemChecks;

class BotSystemTest {
    @Test void configuredBotSchedulingAndRecovery() throws Exception { BotSystemChecks.main(new String[0]); }
}
