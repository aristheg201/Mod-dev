import vn.svframe.mythiclibfabric.runtime.NativeStatEngine;
import vn.svframe.mythiclibfabric.runtime.NativeStatHandler;
import vn.svframe.mythiclibfabric.runtime.NativeStatModifier;
import vn.svframe.mythiclibfabric.runtime.NativeTemporaryStatModifier;

import java.text.DecimalFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public final class NativeStatEngineSmoke {
    public static void main(String[] args) {
        NativeStatEngine engine = new NativeStatEngine();
        UUID player = UUID.randomUUID();
        engine.onSessionOpen(player);
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
        if (!NativeStatEngine.EquipmentSlot.MAIN_HAND.isCompatible(
                NativeStatEngine.ModifierSource.ARMOR, NativeStatEngine.EquipmentSlot.ARMOR)) {
            throw new AssertionError("generic armor slot must be treated as a body slot");
        }
        if (NativeStatEngine.EquipmentSlot.OFF_HAND.isCompatible(
                NativeStatEngine.ModifierSource.MAINHAND_ITEM, NativeStatEngine.EquipmentSlot.MAIN_HAND)) {
            throw new AssertionError("main-hand source leaked into off-hand calculation");
        }

        AtomicInteger updates = new AtomicInteger();
        NativeStatHandler handled = new NativeStatHandler(
                "HANDLED", 50.0d, 0.0d, 100.0d, new DecimalFormat("0.0")) {
            @Override
            public double getPlayerDefaultBase() {
                return 25.0d;
            }
        };
        handled.setModifierEditor((instance, modifier) -> modifier.multiply(2.0d));
        handled.addUpdateListener(instance -> updates.incrementAndGet());
        engine.registerHandler(handled);
        engine.register(player, "HANDLED", new NativeStatEngine.Modifier(
                UUID.randomUUID(), "edited-flat", 20.0d,
                NativeStatEngine.ModifierType.FLAT,
                NativeStatEngine.EquipmentSlot.OTHER,
                NativeStatEngine.ModifierSource.OTHER));
        assertClose(90.0d, engine.stat(player, "HANDLED"), "handler modifier editor/base");
        assertClose(25.0d, engine.instance(player, "HANDLED").defaultBase(), "handler default base");
        if (!"90.0".equals(engine.instance(player, "HANDLED").formatFinal())) {
            throw new AssertionError("handler decimal format was not used");
        }
        engine.register(player, "HANDLED", new NativeStatEngine.Modifier(
                UUID.randomUUID(), "clamped", 50.0d,
                NativeStatEngine.ModifierType.FLAT,
                NativeStatEngine.EquipmentSlot.OTHER,
                NativeStatEngine.ModifierSource.OTHER));
        assertClose(100.0d, engine.stat(player, "HANDLED"), "handler clamp");
        if (updates.get() < 2) throw new AssertionError("stat update listeners were not fired");

        AtomicInteger bufferedUpdates = new AtomicInteger();
        NativeStatHandler buffered = new NativeStatHandler("BUFFERED");
        buffered.addUpdateListener(instance -> bufferedUpdates.incrementAndGet());
        engine.registerHandler(buffered);
        engine.bufferUpdates(player, () -> {
            engine.register(player, "BUFFERED", new NativeStatEngine.Modifier(
                    UUID.randomUUID(), "one", 1.0d,
                    NativeStatEngine.ModifierType.FLAT,
                    NativeStatEngine.EquipmentSlot.OTHER,
                    NativeStatEngine.ModifierSource.OTHER));
            engine.register(player, "BUFFERED", new NativeStatEngine.Modifier(
                    UUID.randomUUID(), "two", 2.0d,
                    NativeStatEngine.ModifierType.FLAT,
                    NativeStatEngine.EquipmentSlot.OTHER,
                    NativeStatEngine.ModifierSource.OTHER));
            if (!engine.isBufferingUpdates(player)) throw new AssertionError("buffer flag was not active inside bufferUpdates");
            if (bufferedUpdates.get() != 0) throw new AssertionError("updates leaked while buffering");
        });
        if (bufferedUpdates.get() != 1) throw new AssertionError("buffered stat updates were not compacted to one release update");

        NativeStatModifier parsedRelative = new NativeStatModifier("parse", "PARSE", "12.5%");
        if (parsedRelative.type() != NativeStatEngine.ModifierType.RELATIVE || parsedRelative.value() != 12.5d) {
            throw new AssertionError("relative modifier string parsing mismatch");
        }
        NativeStatModifier parsedScalar = new NativeStatModifier("parse", "PARSE", "8s");
        if (parsedScalar.type() != NativeStatEngine.ModifierType.ADDITIVE_MULTIPLIER || parsedScalar.value() != 8.0d) {
            throw new AssertionError("scalar modifier string parsing mismatch");
        }
        NativeStatModifier configModifier = new NativeStatModifier(Map.of(
                "key", "cfg", "stat", "PARSE", "value", 7.0d, "multiplicative", true));
        if (configModifier.type() != NativeStatEngine.ModifierType.RELATIVE) {
            throw new AssertionError("config multiplicative modifier parsing mismatch");
        }
        UUID preservedId = parsedRelative.uniqueId();
        if (!parsedRelative.add(2.5d).uniqueId().equals(preservedId)
                || !parsedRelative.multiply(2.0d).uniqueId().equals(preservedId)) {
            throw new AssertionError("stat modifier transformations did not preserve UUID identity");
        }

        NativeTemporaryStatModifier scheduled = new NativeTemporaryStatModifier(
                "scheduled", "TEMP_API", 10.0d,
                NativeStatEngine.ModifierType.FLAT,
                NativeStatEngine.EquipmentSlot.OTHER,
                NativeStatEngine.ModifierSource.OTHER);
        scheduled.register(engine, player, 20L, 200L);
        if (!scheduled.isActive() || scheduled.duration() != 20L) {
            throw new AssertionError("temporary stat modifier did not enter active state");
        }
        if (engine.instance(player, "TEMP_API").modifier(scheduled.uniqueId()) == null) {
            throw new AssertionError("temporary stat modifier did not register in stat map");
        }
        if (NativeTemporaryStatModifier.tick(219L) != 0) {
            throw new AssertionError("temporary stat modifier expired too early");
        }
        if (NativeTemporaryStatModifier.tick(220L) != 1) {
            throw new AssertionError("temporary stat modifier did not execute at due tick");
        }
        if (engine.instance(player, "TEMP_API").modifier(scheduled.uniqueId()) != null) {
            throw new AssertionError("temporary stat modifier was not removed by scheduled task");
        }
        if (!scheduled.isActive()) {
            throw new AssertionError("1.7.1 observable active state after natural expiry was not preserved");
        }
        scheduled.close();
        if (scheduled.isActive()) throw new AssertionError("close did not clear active task reference");

        NativeTemporaryStatModifier cancelled = new NativeTemporaryStatModifier(
                "cancelled", "TEMP_CANCEL", 5.0d,
                NativeStatEngine.ModifierType.FLAT,
                NativeStatEngine.EquipmentSlot.OTHER,
                NativeStatEngine.ModifierSource.OTHER);
        cancelled.register(engine, player, 10L, 300L);
        cancelled.close();
        if (NativeTemporaryStatModifier.tick(400L) != 0) {
            throw new AssertionError("closed temporary modifier still executed its timer");
        }
        if (engine.instance(player, "TEMP_CANCEL").modifier(cancelled.uniqueId()) == null) {
            throw new AssertionError("1.7.1 close() behavior should leave the registered modifier in place");
        }
        cancelled.unregister(engine, player);

        engine.onSessionClose(player);
        if (!engine.isBufferingUpdates(player)) throw new AssertionError("closed session should buffer updates");
        NativeTemporaryStatModifier.cancelAll();

        System.out.println("MYTHICLIB_NATIVE_STAT_RUNTIME=PASS");
    }

    private static void assertClose(double expected, double actual, String label) {
        if (Math.abs(expected - actual) > 1.0E-9d) {
            throw new AssertionError(label + ": expected=" + expected + " actual=" + actual);
        }
    }
}
