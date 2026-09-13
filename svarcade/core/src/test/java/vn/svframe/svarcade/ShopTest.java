package vn.svframe.svarcade;

import org.junit.jupiter.api.Test;
import vn.svframe.svarcade.verification.ShopChecks;

class ShopTest {
    @Test void shopStockCapsPhaseCurrencyDurationAndRecoveryAreAuthoritative() { ShopChecks.main(new String[0]); }
}
