package vn.svframe.svarcade;

import java.util.*;
import org.junit.jupiter.api.Test;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.security.*;
import vn.svframe.svarcade.systems.*;
import vn.svframe.svarcade.systems.deployable.*;
import vn.svframe.svarcade.systems.tower.*;
import vn.svframe.svarcade.systems.upgrade.*;
import static org.junit.jupiter.api.Assertions.*;

class TdActionFactoryTest {
    @Test void exposesAllAuthoritativeDeployableActionsWithCapabilityDependencies() {
        Registry<ActionHandlerFactory> handlers = TdActionFactories.create();
        assertEquals(Set.of(TdActionFactories.DEPLOY, TdActionFactories.MOVE, TdActionFactories.RECALL, TdActionFactories.SELL,
                TdActionFactories.UPGRADE, TdActionFactories.TARGET), handlers.ids());
        Node config = new Node(Map.of("effect", "test:changed", "allowed_states", List.of("PREPARE", "SHOP")), "action");
        handlers.ids().forEach(id -> handlers.require(id).validate(config));
        assertTrue(handlers.require(TdActionFactories.DEPLOY).dependencies(config).contains(DeployableSystem.ID));
        assertTrue(handlers.require(TdActionFactories.UPGRADE).dependencies(config).contains(UpgradeSystem.ID));
        assertTrue(handlers.require(TdActionFactories.TARGET).dependencies(config).contains(TowerSystem.ID));
        handlers.ids().forEach(id -> assertTrue(handlers.require(id).dependencies(config).contains(vn.svframe.svarcade.runtime.StateMachineSystem.ID)));
    }
}
