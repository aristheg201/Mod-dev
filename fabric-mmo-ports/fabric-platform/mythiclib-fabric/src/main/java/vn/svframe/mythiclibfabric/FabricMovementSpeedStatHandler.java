package vn.svframe.mythiclibfabric;

import net.minecraft.entity.attribute.EntityAttributes;
import vn.svframe.mythiclibfabric.runtime.NativeStatEngine;

/** Native Fabric counterpart of MythicLib 1.7.1 MovementSpeedStatHandler. */
public final class FabricMovementSpeedStatHandler extends FabricAttributeStatHandler {
    public FabricMovementSpeedStatHandler(NativeStatEngine engine, MythicLibStatSettings.Entry settings) {
        super("MOVEMENT_SPEED", EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.1d, settings);
        setModifierEditor((instance, modifier) -> {
            if (modifier.value() >= 0.0d) return modifier;
            double reduction = engine.stat(instance.entityId(), "SPEED_MALUS_REDUCTION");
            return modifier.multiply(1.0d - reduction / 100.0d);
        });
    }
}
