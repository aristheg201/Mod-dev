package vn.svframe.svarcade;

import org.junit.jupiter.api.Test;
import vn.svframe.svarcade.verification.SecurityLifecycleChecks;

class SecurityLifecycleTest {
    @Test void controllerAndRateLimitLifecycle() { SecurityLifecycleChecks.main(new String[0]); }
}
