package io.github.aristheg201.svhub.native.game.tft

import io.github.aristheg201.svhub.native.game.NativeBotDifficulty
import io.github.aristheg201.svhub.native.game.NativeGameView
import io.github.aristheg201.svhub.native.game.TftBotPlanner
import kotlin.test.Test
import kotlin.test.assertEquals

class TftBotAugmentPreferenceTest {
    private fun view(strategy: String) = NativeGameView(
        sessionId = "s",
        gameId = "tft",
        title = "TFT",
        phase = "planning",
        boardWidth = 7,
        boardHeight = 8,
        fields = mapOf(
            "gold" to "0",
            "hp" to "100",
            "unitCap" to "1",
            "boardCount" to "1",
            "bench" to "",
            "botStrategy" to strategy,
            "xpNext" to "10",
            "level" to "4",
            "streak" to "0",
            "augmentChoices" to listOf(
                "generic~Generic~Generic value~100~economy~Gold",
                "titan~Titan Awakening~MonsterVerse value~90~monsterverse,combat~Gold"
            ).joinToString(";")
        )
    )

    @Test
    fun `hard bot consumes server augment tags while normal bot still follows base weight`() {
        val strategy = listOf(
            "strategy", "", "", "", "", "", "monsterverse,combat", "", "", "", "", ""
        ).joinToString("~")
        val normal = TftBotPlanner.plan(view(strategy), NativeBotDifficulty.NORMAL)
            .candidates.filter { it.action == "choose_augment" }
        val hard = TftBotPlanner.plan(view(strategy), NativeBotDifficulty.HARD)
            .candidates.filter { it.action == "choose_augment" }

        assertEquals("generic", normal.first().args["id"])
        assertEquals("titan", hard.first().args["id"])
    }
}
