package vn.svframe.lively.world;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import vn.svframe.lively.LivelyNpcs;

import java.nio.file.Path;

/** Loads player-built structure classification rules before any server session begins. */
public final class BuiltStructureTypesBootstrap implements ModInitializer {
    @Override
    public void onInitialize() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("livelynpcs").resolve("building-types.json");
        try {
            int count = BuiltStructureTypeRegistry.load(file);
            LivelyNpcs.LOGGER.info("Loaded {} player-built structure type rules from {}", count, file);
        } catch (Exception error) {
            LivelyNpcs.LOGGER.error("Failed to load player-built structure type rules from {}; built-in defaults remain active", file, error);
        }
    }
}
