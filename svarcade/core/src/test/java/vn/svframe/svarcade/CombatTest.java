package vn.svframe.svarcade;

import org.junit.jupiter.api.Test;
import vn.svframe.svarcade.verification.CombatChecks;

class CombatTest {
    @Test void combatPrimitivesAreDeterministicBoundedAndDataDefined() { CombatChecks.main(new String[0]); }
}
