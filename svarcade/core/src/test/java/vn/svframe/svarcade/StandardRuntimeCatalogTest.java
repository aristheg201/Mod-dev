package vn.svframe.svarcade;

import org.junit.jupiter.api.Test;
import vn.svframe.svarcade.verification.StandardRuntimeCatalogChecks;

class StandardRuntimeCatalogTest {
    @Test void standardCatalogIncludesConfiguredBoardAndBotComposition() { StandardRuntimeCatalogChecks.main(new String[0]); }
}
