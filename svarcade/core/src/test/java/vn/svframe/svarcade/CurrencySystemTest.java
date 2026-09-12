package vn.svframe.svarcade;

import org.junit.jupiter.api.Test;
import vn.svframe.svarcade.verification.CurrencyChecks;

class CurrencySystemTest {
    @Test void matchLedgerTransactionsAndRecovery() { CurrencyChecks.main(new String[0]); }
}
