package vn.svframe.svarcade;

import org.junit.jupiter.api.Test;
import vn.svframe.svarcade.verification.PathProgressChecks;

class PathProgressTest {
    @Test void deterministicPathExposesNormalizedProgressForTargeting() { PathProgressChecks.main(new String[0]); }
}
