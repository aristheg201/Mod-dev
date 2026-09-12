package vn.svframe.svarcade;

import org.junit.jupiter.api.Test;
import vn.svframe.svarcade.verification.BotWorkerChecks;

class BotWorkerTest {
    @Test void workerLifecycleAndSharedIngress() throws Exception { BotWorkerChecks.main(new String[0]); }
}
