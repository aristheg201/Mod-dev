package vn.svframe.svarcade;

import org.junit.jupiter.api.Test;
import vn.svframe.svarcade.verification.DeployableChecks;

class DeployableTest {
    @Test void deploymentIsCanonicalTransactionalAndRecoverable() { DeployableChecks.main(new String[0]); }
}
