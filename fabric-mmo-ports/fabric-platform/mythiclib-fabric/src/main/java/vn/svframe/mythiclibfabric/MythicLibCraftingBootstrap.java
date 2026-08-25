package vn.svframe.mythiclibfabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

/** Native Fabric bootstrap for the complete MythicLib crafting station surface. */
public final class MythicLibCraftingBootstrap implements ModInitializer {
    @Override public void onInitialize() {
        MythicLibVanillaCraftingMod.initialize();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> MythicLibWorkbenchCommands.register(dispatcher));
    }
}
