package io.github.aristheg201.cobblemonworld.npc;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TrainerLevelScalingTest {
    @Test void earlyAndEndgameTrainersBothMatchTheChallenger() {
        assertEquals(12, TrainerLevelScaling.level(70, 70, 12, 0));
        assertEquals(82, TrainerLevelScaling.level(15, 15, 82, 0));
        assertEquals(9, TrainerLevelScaling.level(67, 70, 12, 0));
    }
    @Test void levelsStayWithinCobblemonLimits() {
        assertEquals(1, TrainerLevelScaling.level(67, 70, 1, 0));
        assertEquals(100, TrainerLevelScaling.level(70, 70, 100, 5));
        assertEquals(25, TrainerLevelScaling.level(70, 70, 23, 2));
    }
}
