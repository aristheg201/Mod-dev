package io.github.aristheg201.svhub.client.nativeui

import com.google.gson.JsonObject
import io.github.aristheg201.svhub.ui.UiRect
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import kotlin.math.roundToInt

class GachaRouletteState {
    var requestId: String = ""
        private set
    var startedAt: Long = 0L
        private set
    var animating: Boolean = false
        private set

    fun observe(state: JsonObject, animate: Boolean) {
        val id = runCatching {
            state.getAsJsonObject("lastRoll")?.get("requestId")?.asString.orEmpty()
        }.getOrDefault("")
        if (id.isBlank() || id == requestId) return
        requestId = id
        startedAt = System.currentTimeMillis()
        animating = animate
    }

    fun finish() { animating = false }
}

object GachaRouletteRenderer {
    fun render(
        gui: GuiGraphics,
        font: Font,
        area: UiRect,
        state: JsonObject,
        ui: GachaRouletteState
    ): Int {
        val last = runCatching { state.getAsJsonObject("lastRoll") }.getOrNull()
        val strip = runCatching { state.getAsJsonArray("strip") }.getOrNull()
        val rolling = runCatching { state.get("rolling")?.asBoolean ?: false }.getOrDefault(false)

        if (last == null || strip == null || strip.size() == 0) {
            if (!rolling) return 0
            val rect = UiRect(area.x, area.y + 22, area.width, 34)
            gui.fill(rect.x, rect.y, rect.right, rect.bottom, PANEL)
            gui.fill(rect.x, rect.y, rect.x + 4, rect.bottom, GOLD)
            gui.drawCenteredString(font, "…", rect.x + rect.width / 2, rect.y + 13, GOLD)
            return 66
        }

        val roll = UiRect(area.x, area.y + 20, area.width, 58)
        val itemW = (area.width / 4).coerceIn(68, 112)
        val target = 32.coerceAtMost(strip.size() - 1)
        var t = if (ui.animating) {
            ((System.currentTimeMillis() - ui.startedAt).toDouble() / DURATION_MS).coerceIn(0.0, 1.0)
        } else 1.0
        if (t >= 1.0) { ui.finish(); t = 1.0 }
        val u = 1.0 - t
        val eased = 1.0 - u * u * u * u * u
        val index = 2.0 + (target - 2.0) * eased
        val center = roll.x + roll.width / 2

        gui.fill(roll.x, roll.y, roll.right, roll.bottom, BACKGROUND)
        gui.enableScissor(roll.x, roll.y, roll.right, roll.bottom)
        try {
        repeat(strip.size()) { i ->
            val entry = strip[i].asJsonObject
            val x = (center + (i - index) * itemW - itemW / 2.0).roundToInt()
            if (x + itemW < roll.x || x > roll.right) return@repeat
            val rarity = entry.str("rarity", "common")
            val color = rarityColor(rarity)
            gui.fill(x + 2, roll.y + 4, x + itemW - 2, roll.bottom - 4, PANEL)
            gui.fill(x + 2, roll.y + 4, x + itemW - 2, roll.y + 8, color)
            gui.drawCenteredString(
                font,
                font.plainSubstrByWidth(entry.str("name", entry.str("id")), itemW - 10),
                x + itemW / 2,
                roll.y + 19,
                TEXT
            )
            gui.drawCenteredString(font, rarity.uppercase(), x + itemW / 2, roll.y + 35, color)
        }
        } finally { gui.disableScissor() }
        gui.fill(center - 1, roll.y - 2, center + 1, roll.bottom + 2, GOLD)

        if (!ui.animating) {
            val winner = last.str("winnerName", last.str("winnerId"))
            gui.drawCenteredString(
                font,
                font.plainSubstrByWidth(winner, area.width - 12),
                center,
                roll.bottom + 6,
                rarityColor(last.str("rarity"))
            )
        }
        return 94
    }

    private fun rarityColor(rarity: String): Int = when (rarity.lowercase()) {
        "legendary" -> 0xFFFFC857.toInt()
        "mythic" -> 0xFFF26DF9.toInt()
        "epic" -> 0xFFB68BE0.toInt()
        "rare" -> 0xFF60A5E8.toInt()
        "uncommon" -> 0xFF80B56B.toInt()
        else -> 0xFF92A5A1.toInt()
    }

    private fun JsonObject.str(key: String, fallback: String = ""): String =
        runCatching { get(key)?.asString ?: fallback }.getOrDefault(fallback)

    private const val DURATION_MS = 5_200.0
    private const val BACKGROUND = 0xFF0C1518.toInt()
    private const val PANEL = 0xFF18272B.toInt()
    private const val TEXT = 0xFFF1F5F3.toInt()
    private const val GOLD = 0xFFE2BE62.toInt()
}
