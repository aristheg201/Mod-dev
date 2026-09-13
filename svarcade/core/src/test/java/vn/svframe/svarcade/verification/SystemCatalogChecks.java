package vn.svframe.svarcade.verification;

import java.util.Set;
import vn.svframe.svarcade.config.Id;
import vn.svframe.svarcade.runtime.SystemCatalog;
import vn.svframe.svarcade.systems.CoreSystems;

public final class SystemCatalogChecks {
    private SystemCatalogChecks() { }

    public static void main(String[] args) {
        SystemCatalog catalog = CoreSystems.create();
        Set<Id> expected = Set.of(
                Id.of("svarcade:movement"),
                Id.of("svarcade:turns"),
                Id.of("svarcade:currency"),
                Id.of("svarcade:objective"),
                Id.of("svarcade:path"),
                Id.of("svarcade:targeting"),
                Id.of("svarcade:board"),
                Id.of("svarcade:board_adjudication"));
        Checks.equal(expected, catalog.schemas().ids());
        Checks.equal(expected, catalog.factories().ids());
        for (Id id : expected) Checks.equal(catalog.schemas().require(id), catalog.factories().require(id));
        System.out.println("SystemCatalogChecks passed");
    }
}
