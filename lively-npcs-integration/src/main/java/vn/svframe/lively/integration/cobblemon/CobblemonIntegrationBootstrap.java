package vn.svframe.lively.integration.cobblemon;

import net.minecraft.server.MinecraftServer;
import vn.svframe.lively.api.LivelyApi;
import vn.svframe.lively.npc.NpcDefinition;

/** Cobblemon registration for native trainer/Pokemon bodies. Trainer combat is bound through NPCBattleActorMixin. */
public final class CobblemonIntegrationBootstrap {
    private CobblemonIntegrationBootstrap() {}

    /** Fabric metadata already requires Cobblemon 1.7.x, so initialization is mandatory rather than fail-open. */
    public static void install() {
        if (LivelyApi.npcs() != null) registerNpcBodyProvider();
    }

    public static boolean installNpcBodyProvider(MinecraftServer server) {
        if (LivelyApi.npcs() == null || server == null) return false;
        registerNpcBodyProvider();
        LivelyApi.npcs().restoreSpawned(server);
        return true;
    }

    private static void registerNpcBodyProvider() {
        LivelyApi.npcs().registerProvider(NpcDefinition.BodyType.EXTERNAL, definition -> {
            String key = definition.bodyKey() == null ? "" : definition.bodyKey().trim();
            return key.startsWith("npc:")
                    ? new CobblemonTrainerBody(definition.id())
                    : new CobblemonPokemonBody(definition.id());
        });
    }
}
