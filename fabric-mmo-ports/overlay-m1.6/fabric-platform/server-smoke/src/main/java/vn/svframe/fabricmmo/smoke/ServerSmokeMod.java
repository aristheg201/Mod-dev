package vn.svframe.fabricmmo.smoke;

import java.util.List;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import vn.svframe.mmocorefabric.MMOCoreFabricMod;
import vn.svframe.mmoitemsfabric.MMOItemsFabricMod;
import vn.svframe.mythiclibfabric.MythicLibFabricMod;
import vn.svframe.mythicmobsfabric.MythicMobsFabricMod;

public final class ServerSmokeMod implements ModInitializer {
    private static final List<String> REQUIRED = List.of("mythiclibfabric", "mmocorefabric", "mmoitemsfabric", "mythicmobsfabric");
    private static final String MMOCORE_EXPECTED = "1:1:1 | skills=1,trees=1,waypoints=1,curves=1,attributes=1";

    @Override
    public void onInitialize() {
        for (String id : REQUIRED) if (!FabricLoader.getInstance().isModLoaded(id)) throw new IllegalStateException("Missing Fabric MMO mod: " + id);
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            String definitions = MMOCoreFabricMod.definitionSummary();
            if (!definitions.equals(MMOCORE_EXPECTED)) throw new IllegalStateException("MMOCore legacy config boot mismatch: " + definitions + " expected=" + MMOCORE_EXPECTED);
            if (!MythicLibFabricMod.hasSkill("SMOKE_SKILL") || !MythicLibFabricMod.definitionSummary().equals("skills=1,scripts=1")) throw new IllegalStateException("MythicLib legacy config boot mismatch: " + MythicLibFabricMod.definitionSummary());
            if (!MMOItemsFabricMod.hasItem("SWORD", "SMOKE_BLADE") || !MMOItemsFabricMod.definitionSummary().equals("items=1,abilities=1")) throw new IllegalStateException("MMOItems legacy config boot mismatch: " + MMOItemsFabricMod.definitionSummary());
            if (MMOItemsFabricMod.createStack("SWORD", "SMOKE_BLADE", 1).isEmpty()) throw new IllegalStateException("MMOItems Fabric ItemStack materialization failed");
            if (!MythicMobsFabricMod.hasMob("SMOKE_MOB") || !MythicMobsFabricMod.hasSkill("SMOKE_MYTHIC")) throw new IllegalStateException("MythicMobs legacy config boot mismatch: " + MythicMobsFabricMod.definitionSummary());
            var spawn = server.getOverworld().getSpawnPos();
            var mob = MythicMobsFabricMod.spawn("SMOKE_MOB", server.getOverworld(), spawn.getX() + 0.5, spawn.getY() + 1, spawn.getZ() + 0.5, 1);
            if (mob == null || MythicMobsFabricMod.activeCount() != 1) throw new IllegalStateException("MythicMobs Fabric mob materialization failed: " + MythicMobsFabricMod.definitionSummary());
            MythicMobsFabricMod.remove(mob);
            System.out.println("MMOCORE_LEGACY_CONFIG_BOOT=PASS");
            System.out.println("MYTHICLIB_LEGACY_CONFIG_BOOT=PASS");
            System.out.println("MMOITEMS_LEGACY_CONFIG_BOOT=PASS");
            System.out.println("MYTHICMOBS_LEGACY_CONFIG_BOOT=PASS");
            System.out.println("FABRIC_MMO_DEDICATED_SERVER_BOOT=PASS");
            server.stop(false);
        });
    }
}
