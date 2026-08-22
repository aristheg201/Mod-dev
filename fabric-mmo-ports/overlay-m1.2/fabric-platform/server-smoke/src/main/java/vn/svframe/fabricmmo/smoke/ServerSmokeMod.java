package vn.svframe.fabricmmo.smoke;

import java.util.List;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import vn.svframe.mmocorefabric.MMOCoreFabricMod;
import vn.svframe.mythiclibfabric.MythicLibFabricMod;

public final class ServerSmokeMod implements ModInitializer {
    private static final List<String> REQUIRED = List.of("mythiclibfabric", "mmocorefabric", "mmoitemsfabric", "mythicmobsfabric");
    @Override public void onInitialize() {
        for (String id : REQUIRED) if (!FabricLoader.getInstance().isModLoaded(id)) throw new IllegalStateException("Missing Fabric MMO mod: " + id);
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            String definitions = MMOCoreFabricMod.definitionSummary();
            if (!definitions.equals("1:1:1")) throw new IllegalStateException("MMOCore legacy config boot mismatch: " + definitions);
            if (!MythicLibFabricMod.hasSkill("SMOKE_SKILL")) throw new IllegalStateException("MythicLib skill loader did not expose SMOKE_SKILL: " + MythicLibFabricMod.definitionSummary());
            if (!MythicLibFabricMod.definitionSummary().equals("skills=1,scripts=1")) throw new IllegalStateException("MythicLib legacy config boot mismatch: " + MythicLibFabricMod.definitionSummary());
            System.out.println("MMOCORE_LEGACY_CONFIG_BOOT=PASS");
            System.out.println("MYTHICLIB_LEGACY_CONFIG_BOOT=PASS");
            System.out.println("FABRIC_MMO_DEDICATED_SERVER_BOOT=PASS");
            server.stop(false);
        });
    }
}
