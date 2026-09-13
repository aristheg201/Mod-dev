package vn.svframe.svarcade;

import org.junit.jupiter.api.Test;
import vn.svframe.svarcade.verification.WaveChecks;

class WaveTest {
    @Test void wavesScheduleAcknowledgeRecoverAndClearDeterministically() { WaveChecks.main(new String[0]); }
}
