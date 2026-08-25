package vn.svframe.mmocorefabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Provisions the default files that the original MMOCore jar creates on first run. */
public final class MMOCoreDefaultConfigMod implements ModInitializer {
    private static final Logger LOG = Logger.getLogger("MMOCore-Fabric/Defaults");
    private static final Path ROOT = FabricLoader.getInstance().getConfigDir().resolve("MMOCore");
    private static final String DEFAULT_LEVELS = "100\n200\n300\n400\n500\n600\n700\n800\n900\n1000\n";

    @Override
    public void onInitialize() {
        Path curve = ROOT.resolve("exp-curves").resolve("levels.txt");
        if (Files.exists(curve)) return;
        try {
            Files.createDirectories(curve.getParent());
            Files.writeString(curve, DEFAULT_LEVELS, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            LOG.log(Level.SEVERE, "Could not provision MMOCore default exp-curves/levels.txt", exception);
        }
    }
}
