package vn.svframe.svarcade;

import org.junit.jupiter.api.Test;
import vn.svframe.svarcade.verification.StateMachineSystemChecks;

class StateMachineSystemTest {
    @Test void dataDefinedLifecycleIsPublishedAsSessionCapability() { StateMachineSystemChecks.main(new String[0]); }
}
