import vn.svframe.mythiclibfabric.runtime.NativeStatEngine;

import java.util.UUID;

public final class NativeStatEngineSmoke {
    public static void main(String[] args) {
        NativeStatEngine engine = new NativeStatEngine();
        UUID player = UUID.randomUUID();
        String stat = "ATTACK_DAMAGE";

        engine.setBase(player, stat, 100.0d);
        engine.register(player, stat, new NativeStatEngine.Modifier(
                UUID.randomUUID(), "flat", 20.0d,
                NativeStatEngine.ModifierType.FLAT,
                NativeStatEngine.EquipmentSlot.OTHER,
                NativeStatEngine.ModifierSource.OTHER));
        engine.register(player, stat, new NativeStatEngine.Modifier(
                UUID.randomUUID(), "additive", 50.0d,
                NativeStatEngine.ModifierType.ADDITIVE_MULTIPLIER,
                NativeStatEngine.EquipmentSlot.OTHER,
                NativeStatEngine.ModifierSource.OTHER));
        engine.register(player, stat, new NativeStatEngine.Modifier(
                UUID.randomUUID(), "relative-a", 10.0d,
                NativeStatEngine.ModifierType.RELATIVE,
                NativeStatEngine.EquipmentSlot.OTHER,
                NativeStatEngine.ModifierSource.OTHER));
        engine.register(player, stat, new NativeStatEngine.Modifier(
                UUID.randomUUID(), "relative-b", -20.0d,
                NativeStatEngine.ModifierType.RELATIVE,
                NativeStatEngine.EquipmentSlot.OTHER,
                NativeStatEngine.ModifierSource.OTHER));

        assertClose(158.4d, engine.stat(player, stat), "modifier calculation order");

        UUID mainOnly = engine.register(player, stat, "main-only", 100.0d,
                NativeStatEngine.ModifierType.FLAT,
                NativeStatEngine.EquipmentSlot.MAIN_HAND,
                NativeStatEngine.ModifierSource.MELEE_WEAPON);
        double main = engine.finalValue(player, stat, NativeStatEngine.EquipmentSlot.MAIN_HAND);
        double off = engine.finalValue(player, stat, NativeStatEngine.EquipmentSlot.OFF_HAND);
        if (!(main > off)) throw new AssertionError("hand compatibility did not filter main-hand weapon modifier");
        engine.remove(player, stat, mainOnly);

        UUID temp = engine.registerTemporary(player, stat, "temporary", 25.0d,
                NativeStatEngine.ModifierType.FLAT,
                NativeStatEngine.EquipmentSlot.OTHER,
                NativeStatEngine.ModifierSource.OTHER,
                20L, 100L);
        if (engine.instance(player, stat).modifier(temp) == null) throw new AssertionError("temporary modifier was not registered");
        if (engine.tick(119L) != 0) throw new AssertionError("temporary modifier expired too early");
        if (engine.tick(120L) != 1) throw new AssertionError("temporary modifier did not expire on schedule");
        if (engine.instance(player, stat).modifier(temp) != null) throw new AssertionError("expired modifier remained registered");

        if (!NativeStatEngine.EquipmentSlot.MAIN_HAND.isCompatible(
                NativeStatEngine.ModifierSource.ARMOR, NativeStatEngine.EquipmentSlot.HEAD)) {
            throw new AssertionError("armor source should be compatible from either action hand");
        }
        if (NativeStatEngine.EquipmentSlot.OFF_HAND.isCompatible(
                NativeStatEngine.ModifierSource.MAINHAND_ITEM, NativeStatEngine.EquipmentSlot.MAIN_HAND)) {
            throw new AssertionError("main-hand source leaked into off-hand calculation");
        }

        System.out.println("MYTHICLIB_NATIVE_STAT_RUNTIME=PASS");
    }

    private static void assertClose(double expected, double actual, String label) {
        if (Math.abs(expected - actual) > 1.0E-9d) {
            throw new AssertionError(label + ": expected=" + expected + " actual=" + actual);
        }
    }
}
