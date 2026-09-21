package io.github.aristheg201.svhub.ui

import org.junit.jupiter.api.Test
import kotlin.test.*

class TftPresentationPolicyTest {
    private val boss = TftPresentationPolicy.Input("match-a", "3-7", "combat", boss = true, pve = true)
    private val loot = boss.copy(phase = "post", hasLoot = true, lootSerial = 7L)

    @Test fun normalPlanningAndCombatDoNotBecomeExclusive() {
        val policy = TftPresentationPolicy()
        for (phase in listOf("planning", "combat", "post")) {
            assertEquals(TftPresentationPolicy.Owner.GAMEPLAY, policy.resolve(boss.copy(phase = phase, boss = false), 100L))
        }
    }

    @Test fun bossIntroExpiresWithoutRestartingEveryFrame() {
        val policy = TftPresentationPolicy(2_000L)
        assertEquals(TftPresentationPolicy.Owner.BOSS_INTRO, policy.resolve(boss, 100L))
        assertEquals(TftPresentationPolicy.Owner.BOSS_INTRO, policy.resolve(boss, 2_099L))
        assertEquals(TftPresentationPolicy.Owner.GAMEPLAY, policy.resolve(boss, 2_100L))
        assertEquals(TftPresentationPolicy.Owner.GAMEPLAY, policy.resolve(boss, 4_100L))
    }

    @Test fun bossCanBeSkippedWithoutResigningOrChangingTheInput() {
        val policy = TftPresentationPolicy()
        policy.resolve(boss, 100L)
        policy.dismiss()
        assertEquals(TftPresentationPolicy.Owner.GAMEPLAY, policy.resolve(boss, 101L))
        assertEquals("combat", boss.phase)
    }

    @Test fun newBossRoundGetsItsOwnIntro() {
        val policy = TftPresentationPolicy()
        policy.resolve(boss, 100L); policy.dismiss()
        assertEquals(TftPresentationPolicy.Owner.BOSS_INTRO, policy.resolve(boss.copy(round = "6-7"), 101L))
    }

    @Test fun rewardOwnsTheScreenUntilDismissalOrAuthoritativePhaseChange() {
        val policy = TftPresentationPolicy()
        assertEquals(TftPresentationPolicy.Owner.PVE_REWARD, policy.resolve(loot, 100L))
        assertEquals(TftPresentationPolicy.Owner.PVE_REWARD, policy.resolve(loot, 8_000L))
        policy.dismiss()
        assertEquals(TftPresentationPolicy.Owner.GAMEPLAY, policy.resolve(loot, 8_001L))
        assertEquals(TftPresentationPolicy.Owner.PVE_REWARD, policy.resolve(loot.copy(lootSerial = 8L), 8_002L))
        assertEquals(TftPresentationPolicy.Owner.GAMEPLAY, policy.resolve(loot.copy(phase = "planning"), 8_003L))
    }

    @Test fun recursiveBackdropDoesNotStealOwnershipOrResetTheTimer() {
        val policy = TftPresentationPolicy()
        policy.resolve(boss, 100L)
        assertEquals(TftPresentationPolicy.Owner.SCENE, policy.resolve(boss.copy(sceneOnly = true), 1_900L))
        assertEquals(TftPresentationPolicy.Owner.BOSS_INTRO, policy.owner)
        assertEquals(TftPresentationPolicy.Owner.GAMEPLAY, policy.resolve(boss, 2_100L))
    }

    @Test fun resultsAugmentsAndCarouselHavePriorityOverBossAndReward() {
        val policy = TftPresentationPolicy()
        assertEquals(TftPresentationPolicy.Owner.RESULT, policy.resolve(loot.copy(finished = true, hasAugments = true), 100L))
        assertEquals(TftPresentationPolicy.Owner.AUGMENT, policy.resolve(loot.copy(hasAugments = true), 100L))
        assertEquals(TftPresentationPolicy.Owner.CAROUSEL, policy.resolve(loot.copy(phase = "draft"), 100L))
    }

    @Test fun enteringAnotherMatchDoesNotInheritDismissedPresentation() {
        val policy = TftPresentationPolicy()
        policy.resolve(loot, 100L); policy.dismiss()
        assertEquals(TftPresentationPolicy.Owner.PVE_REWARD, policy.resolve(loot.copy(sessionId = "match-b"), 101L))
    }

    @Test fun nonPveCombatCannotShowBossIntroFromStaleMetadata() {
        val policy = TftPresentationPolicy()
        assertEquals(TftPresentationPolicy.Owner.GAMEPLAY, policy.resolve(boss.copy(pve = false), 100L))
    }

    @Test fun exclusiveSafeAreasRemainBoundedAndDisjoint() {
        for (w in 80..1280 step 17) for (h in 70..720 step 13) {
            val area = UiRect(13, 29, w, h)
            val layout = TftExclusiveLayout.resolve(area)
            val rects = listOf(layout.header, layout.body, layout.continueButton)
            rects.forEach { r ->
                assertTrue(r.width > 0 && r.height > 0)
                assertTrue(r.x >= area.x && r.y >= area.y && r.right <= area.right && r.bottom <= area.bottom)
            }
            assertTrue(layout.header.bottom < layout.body.y)
            assertTrue(layout.body.bottom < layout.continueButton.y)
        }
    }
}
