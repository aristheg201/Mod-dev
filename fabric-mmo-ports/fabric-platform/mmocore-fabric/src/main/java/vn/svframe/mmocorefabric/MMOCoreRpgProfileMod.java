package vn.svframe.mmocorefabric;

import net.fabricmc.api.ModInitializer;
import vn.svframe.mmocorefabric.mixin.MMOCoreFabricAccessor;
import vn.svframe.mmocorefabric.runtime.gameplay.ClassRuntime;
import vn.svframe.mmocorefabric.runtime.progression.PlayerProgress;
import vn.svframe.mythiclibfabric.runtime.RpgProfileRegistry;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Publishes MMOCore's authoritative player level, class and effective attributes to other Fabric ports. */
public final class MMOCoreRpgProfileMod implements ModInitializer {
    private static AutoCloseable registration;

    @Override
    public void onInitialize() {
        if (registration != null) return;
        registration = RpgProfileRegistry.register(MMOCoreRpgProfileMod::snapshot);
    }

    private static RpgProfileRegistry.Snapshot snapshot(UUID playerId) {
        Map<UUID, PlayerProgress> profiles = MMOCoreFabricAccessor.mmocore$getProfiles();
        PlayerProgress progress = profiles.get(playerId);
        if (progress == null) return null;

        ClassRuntime classes = MMOCoreFabricAccessor.mmocore$getClasses();
        String selectedClass = classes.selected(playerId).orElse("");
        Map<String, Double> attributes = new LinkedHashMap<>();

        if (!selectedClass.isEmpty()) {
            var definition = classes.definitions().get(selectedClass.toLowerCase(Locale.ROOT));
            if (definition != null) {
                for (String key : definition.attributes().keySet()) {
                    attributes.put(key.toLowerCase(Locale.ROOT), classes.attribute(playerId, key, progress.level()));
                }
            }
        }
        progress.attributes().forEach((key, value) -> {
            if (key != null && value != null) attributes.merge(key.toLowerCase(Locale.ROOT), value.doubleValue(), Double::sum);
        });
        return new RpgProfileRegistry.Snapshot(progress.level(), selectedClass, attributes);
    }
}
