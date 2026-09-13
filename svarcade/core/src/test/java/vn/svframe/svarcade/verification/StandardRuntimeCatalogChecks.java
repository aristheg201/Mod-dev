package vn.svframe.svarcade.verification;

import java.util.*;
import vn.svframe.svarcade.bot.BotRuntime;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.runtime.*;
import vn.svframe.svarcade.security.IntentGate;
import vn.svframe.svarcade.systems.StandardRuntimeCatalog;

public final class StandardRuntimeCatalogChecks {
    private StandardRuntimeCatalogChecks() { }
    public static void main(String[] args) {
        ThreadGuard thread = new ThreadGuard(); BotRuntime bots = new BotRuntime(thread, new Registry<>(Map.of()), 1, 2);
        try {
            SystemCatalog catalog = StandardRuntimeCatalog.create(bots,
                    session -> (actor, tick) -> new IntentGate.Facts(actor, tick, 0, true));
            Checks.equal(true, catalog.schemas().contains(Id.of("svarcade:board_bot_source")));
            Checks.equal(true, catalog.schemas().contains(Id.of("svarcade:bot")));
            Checks.equal(catalog.schemas().ids(), catalog.factories().ids());
        } finally { bots.close(); }
        System.out.println("StandardRuntimeCatalogChecks passed");
    }
}
