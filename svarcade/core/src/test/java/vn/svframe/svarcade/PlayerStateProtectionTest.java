package vn.svframe.svarcade;

import java.util.*;
import org.junit.jupiter.api.Test;
import vn.svframe.svarcade.runtime.*;
import static org.junit.jupiter.api.Assertions.*;

class PlayerStateProtectionTest {
    @Test void exactCapturedFieldsRestoreIdempotentlyAndFailuresRemainRetryable() {
        ThreadGuard thread = new ThreadGuard(); UUID player = UUID.randomUUID(); List<PlayerStateSnapshot> restored = new ArrayList<>();
        PlayerStateProtection.Bridge bridge = new PlayerStateProtection.Bridge() {
            @Override public PlayerStateSnapshot capture(UUID id, EnumSet<PlayerStateSnapshot.Field> fields) {
                return new PlayerStateSnapshot(1, fields, new PlayerStateSnapshot.Position("minecraft:overworld", 1, 2, 3, 4, 5), null, List.of(), -1, List.of());
            }
            @Override public void restore(UUID id, PlayerStateSnapshot snapshot) { restored.add(snapshot); }
        };
        PlayerStateProtection protection = new PlayerStateProtection(thread, bridge, 8);
        var fields = EnumSet.of(PlayerStateSnapshot.Field.POSITION); PlayerStateSnapshot saved = protection.capture(player, fields);
        assertTrue(protection.protectedPlayer(player)); assertTrue(protection.restore(player)); assertEquals(List.of(saved), restored); assertFalse(protection.restore(player));
    }

    @Test void recoveredSnapshotIsRestoredWithoutRecapturingLiveState() {
        ThreadGuard thread = new ThreadGuard(); UUID player = UUID.randomUUID(); int[] captures = {0}; int[] restores = {0};
        PlayerStateProtection.Bridge bridge = new PlayerStateProtection.Bridge() {
            @Override public PlayerStateSnapshot capture(UUID id, EnumSet<PlayerStateSnapshot.Field> fields) { captures[0]++; throw new AssertionError(); }
            @Override public void restore(UUID id, PlayerStateSnapshot snapshot) { restores[0]++; }
        };
        PlayerStateProtection protection = new PlayerStateProtection(thread, bridge, 8);
        PlayerStateSnapshot saved = new PlayerStateSnapshot(1, EnumSet.of(PlayerStateSnapshot.Field.MODE), null,
                new PlayerStateSnapshot.Mode("survival", false, false, 0.1f, 0.05f), List.of(), -1, List.of());
        protection.recover(player, saved); assertTrue(protection.restore(player)); assertEquals(0, captures[0]); assertEquals(1, restores[0]);
    }
}
