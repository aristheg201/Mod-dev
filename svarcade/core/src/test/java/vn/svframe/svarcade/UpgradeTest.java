package vn.svframe.svarcade;

import org.junit.jupiter.api.Test;
import vn.svframe.svarcade.verification.UpgradeChecks;

class UpgradeTest {
    @Test void upgradesAreFilteredTransactionalPrerequisiteBoundAndRecoverable() { UpgradeChecks.main(new String[0]); }
}
