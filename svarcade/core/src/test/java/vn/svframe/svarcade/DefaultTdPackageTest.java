package vn.svframe.svarcade;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.runtime.*;
import vn.svframe.svarcade.systems.*;
import static org.junit.jupiter.api.Assertions.*;

class DefaultTdPackageTest {
    @Test void packagedPokemonTdCompilesThroughProductionGenericSchemas() throws Exception {
        var game = Objects.requireNonNull(getClass().getClassLoader().getResource("defaults/minigames/pokemon_td/game.yml"));
        Path root = Paths.get(game.toURI()).getParent();
        SystemCatalog catalog = CoreSystems.create(TdActionFactories.create(), ValuePrimitives.actions(), ValuePrimitives.conditions());
        Definition definition = new DefinitionLoader(catalog.schemas()).load(root);
        assertEquals(Id.of("svarcade:pokemon_td"), definition.id());
        assertEquals(Set.of("cobblemon"), definition.integrations());
        Set<Id> systems = new HashSet<>(); definition.systems().forEach(system -> systems.add(system.id()));
        assertTrue(systems.containsAll(Set.of(Id.of("svarcade:loadouts"), Id.of("svarcade:deployables"), Id.of("svarcade:upgrades"),
                Id.of("svarcade:waves"), Id.of("svarcade:enemies"), Id.of("svarcade:towers"), Id.of("svarcade:actions"), Id.of("svarcade:wave_loop"))));
        for (String file : List.of("combat.yml","pokemon-profiles.yml","move-effects.yml","upgrades.yml","enemies.yml","waves.yml","bots.yml","editor.yml","ui.yml","rewards.yml"))
            assertTrue(Files.isRegularFile(root.resolve(file)), file);
    }
}
