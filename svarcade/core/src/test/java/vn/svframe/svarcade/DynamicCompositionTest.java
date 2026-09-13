package vn.svframe.svarcade;

import org.junit.jupiter.api.Test;
import vn.svframe.svarcade.verification.DynamicCompositionChecks;

class DynamicCompositionTest {
    @Test void configSensitiveDependenciesAndCapabilitiesStayAligned() { DynamicCompositionChecks.main(new String[0]); }
}
