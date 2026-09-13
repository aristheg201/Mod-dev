package vn.svframe.svarcade;

import org.junit.jupiter.api.Test;
import vn.svframe.svarcade.verification.SystemCatalogChecks;

class SystemCatalogTest {
    @Test void canonicalSchemaFactoryRegistryUsesTheSamePlans() { SystemCatalogChecks.main(new String[0]); }
}
