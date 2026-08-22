package vn.svframe.lively.event;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class StoryDirectorToneTest {
    @Test
    void peacefulSuppressesCrimeAndBoostsFestivalWhileDarkDoesTheOpposite() {
        var crime = proposal(WorldEventEngine.Category.CRIME, .60D);
        var festival = proposal(WorldEventEngine.Category.FESTIVAL, .60D);

        var peacefulCrime = LivingWorldDirectorService.applyTone("peaceful", crime);
        var peacefulFestival = LivingWorldDirectorService.applyTone("peaceful", festival);
        var darkCrime = LivingWorldDirectorService.applyTone("dark", crime);
        var darkFestival = LivingWorldDirectorService.applyTone("dark", festival);

        assertTrue(peacefulCrime.intensity() < crime.intensity());
        assertTrue(peacefulFestival.intensity() > festival.intensity());
        assertTrue(darkCrime.intensity() > crime.intensity());
        assertTrue(darkFestival.intensity() < festival.intensity());
        assertEquals("dark", darkCrime.facts().get("story_tone"));
    }

    @Test
    void balancedLeavesIntensityUnchanged() {
        var proposal = proposal(WorldEventEngine.Category.MYSTERY, .53D);
        assertEquals(.53D, LivingWorldDirectorService.applyTone("balanced", proposal).intensity(), 0.000001D);
    }

    private static WorldEventEngine.EventProposal proposal(WorldEventEngine.Category category, double intensity) {
        return new WorldEventEngine.EventProposal(category, "test", null, Set.of(), intensity,
                Duration.ofMinutes(30), Map.of());
    }
}
