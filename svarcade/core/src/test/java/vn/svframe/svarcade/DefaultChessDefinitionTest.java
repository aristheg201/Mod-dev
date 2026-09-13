package vn.svframe.svarcade;

import static org.junit.jupiter.api.Assertions.*;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import vn.svframe.svarcade.bot.BotRuntime;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.runtime.*;
import vn.svframe.svarcade.security.IntentGate;
import vn.svframe.svarcade.systems.StandardRuntimeCatalog;

class DefaultChessDefinitionTest {
    @Test void packagedChessDefinitionCompilesThroughProductionCatalog() throws Exception {
        URL game = Objects.requireNonNull(getClass().getResource("/defaults/minigames/pokemon_chess/game.yml"));
        Path folder = Paths.get(game.toURI()).getParent();
        ThreadGuard thread = new ThreadGuard();
        BotRuntime bots = new BotRuntime(thread, new Registry<>(Map.of()), 1, 2);
        try {
            SystemCatalog catalog = StandardRuntimeCatalog.create(bots,
                    session -> (actor, tick) -> new IntentGate.Facts(actor, tick, 0, true));
            Definition definition = new DefinitionLoader(catalog.schemas()).load(folder);
            assertEquals(Id.of("svarcade:pokemon_chess"), definition.id());
            assertEquals(Set.of("cobblemon"), definition.integrations());
            assertEquals(2, definition.minPlayers());
            assertEquals(2, definition.maxPlayers());
            assertEquals("default", definition.arenas().keySet().iterator().next());
            assertEquals(Set.of(Id.of("svarcade:movement"), Id.of("svarcade:turns"), Id.of("svarcade:objectives"),
                    Id.of("svarcade:board"), Id.of("svarcade:board_adjudication"), Id.of("svarcade:state_machine"),
                    Id.of("svarcade:actions"), Id.of("svarcade:board_bot_source"), Id.of("svarcade:bot")),
                    definition.systems().stream().map(Definition.SystemSpec::id).collect(java.util.stream.Collectors.toSet()));
        } finally { bots.close(); }
    }
}
