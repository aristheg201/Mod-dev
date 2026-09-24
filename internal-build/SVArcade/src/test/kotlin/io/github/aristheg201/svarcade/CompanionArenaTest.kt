package io.github.aristheg201.svarcade

import com.google.gson.JsonObject
import io.github.aristheg201.svarcade.companion.CompanionArena
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CompanionArenaTest {
    @Test
    fun `arena is server owned and produces bounded combat state`() {
        val player = UUID.fromString("d18de084-42ae-4c7e-8dd8-28e177d5c28b")
        assertContains(CompanionArena.start(player, "wolf"), "Đối thủ")
        repeat(20) {
            val state = JsonObject().also { CompanionArena.appendState(player, it) }.getAsJsonObject("arena")
            assertNotNull(state)
            assertTrue(state.get("playerHp").asInt >= 0)
            assertTrue(state.get("enemyHp").asInt >= 0)
            if (!state.get("finished").asBoolean) CompanionArena.act(player, if (it % 3 == 2) "skill" else "attack")
        }
        CompanionArena.clear(player)
    }
}
