package vn.svframe.svarcade;

import org.junit.jupiter.api.Test;
import vn.svframe.svarcade.verification.ActionSystemChecks;

class ActionSystemTest {
    @Test void configuredActionsOwnTheAuthoritativeDispatcher() { ActionSystemChecks.main(new String[0]); }
}
