package vn.svframe.svarcade;

import org.junit.jupiter.api.Test;
import vn.svframe.svarcade.verification.SessionChecks;

class SessionServicesTest {
    @Test void typedContractsAndLifecycleRegressionChecks() { SessionChecks.main(new String[0]); }
}
