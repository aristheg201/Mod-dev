package io.github.aristheg201.svhub.client.nativeui

import com.google.gson.JsonObject
import com.mojang.math.Axis
import io.github.aristheg201.svhub.client.cobblemon.PokemonModelRenderer
import io.github.aristheg201.svhub.client.cobblemon.PokemonView
import io.github.aristheg201.svhub.ui.UiRect
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import kotlin.math.max
import kotlin.math.min

object CardTable3DRenderer {
    fun supports(gameId: String): Boolean = gameId == "uno" || gameId == "pokecards"

    fun render(
        gui: GuiGraphics,
        font: Font,
        area: UiRect,
        view: JsonObject,
        mouseX: Int,
        mouseY: Int,
        onCard: (UiRect, JsonObject) -> Unit
    ) {
        val gameId = view.str("gameId")
        if (!supports(gameId)) return

        val table = UiRect(
            area.x + max(8, area.width / 16),
            area.y + 4,
            (area.width - max(16, area.width / 8)).coerceAtLeast(80),
            (area.height - 10).coerceAtLeast(70)
        )
        drawTable(gui, table, gameId)

        val cards = view.getAsJsonArray("cards") ?: return
        val count = min(cards.size(), 6)
        if (count <= 0) return

        val cardW = (table.width / max(4, count + 1)).coerceIn(50, 92)
        val cardH = (cardW * 1.28f).toInt().coerceIn(62, 118)
        val total = cardW * count
        val startX = table.x + (table.width - total) / 2
        val baseY = table.bottom - cardH - 10

        repeat(count) { index ->
            val card = cards[index].asJsonObject
            val x = startX + index * cardW
            val baseRect = UiRect(x + 3, baseY, cardW - 6, cardH)
            val hovered = baseRect.contains(mouseX.toDouble(), mouseY.toDouble())
            val lift = if (hovered) 7 else 0
            val rect = UiRect(baseRect.x, baseRect.y - lift, baseRect.width, baseRect.height)
            val angle = ((index - (count - 1) / 2f) * 3.5f).coerceIn(-10f, 10f)

            val pose = gui.pose()
            pose.pushPose()
            val cx = rect.x + rect.width / 2f
            val cy = rect.y + rect.height / 2f
            pose.translate(cx.toDouble(), cy.toDouble(), (20 + index * 2).toDouble())
            pose.mulPose(Axis.ZP.rotationDegrees(angle))
            pose.translate(-cx.toDouble(), -cy.toDouble(), 0.0)

            val accent = if (gameId == "uno") unoColor(card.str("accent")) else typeColor(card.str("accent"))
            gui.fill(rect.x, rect.y, rect.right, rect.bottom, if (hovered) CARD_HOVER else CARD)
            gui.fill(rect.x, rect.y, rect.right, rect.y + 4, accent)
            gui.fill(rect.x, rect.y, rect.x + 1, rect.bottom, BORDER)
            gui.fill(rect.right - 1, rect.y, rect.right, rect.bottom, BORDER)
            gui.fill(rect.x, rect.bottom - 1, rect.right, rect.bottom, BORDER)

            if (gameId == "pokecards") {
                renderPokemonCard(gui, font, rect, card, index)
            } else {
                val label = font.plainSubstrByWidth(card.str("label", card.str("id")), rect.width - 10)
                gui.drawCenteredString(font, label, rect.x + rect.width / 2, rect.y + rect.height / 2 - 4, TEXT)
                val subtitle = font.plainSubstrByWidth(card.str("subtitle"), rect.width - 10)
                if (subtitle.isNotBlank()) gui.drawCenteredString(font, subtitle, rect.x + rect.width / 2, rect.bottom - 15, MUTED)
            }
            pose.popPose()
            onCard(rect, card)
        }

        val fields = view.getAsJsonObject("fields")
        if (gameId == "uno") {
            val active = fields?.str("activeColor").orEmpty()
            val top = fields?.str("top").orEmpty()
            if (top.isNotBlank()) {
                gui.drawCenteredString(font, top, table.x + table.width / 2, table.y + 16, TEXT)
                if (active.isNotBlank()) gui.drawCenteredString(font, active.uppercase(), table.x + table.width / 2, table.y + 29, unoColor(active))
            }
        } else {
            val mine = fields?.str("yourScore").orEmpty()
            val theirs = fields?.str("opponentScore").orEmpty()
            if (mine.isNotBlank() || theirs.isNotBlank()) {
                gui.drawCenteredString(font, "$mine — $theirs", table.x + table.width / 2, table.y + 18, GOLD)
            }
        }
    }

    private fun renderPokemonCard(gui: GuiGraphics, font: Font, rect: UiRect, card: JsonObject, index: Int) {
        val meta = card.getAsJsonObject("meta")
        val rawSpecies = meta?.str("species").orEmpty()
        val species = when {
            rawSpecies.isBlank() -> ""
            ':' in rawSpecies -> rawSpecies
            else -> "cobblemon:$rawSpecies"
        }
        if (species.isNotBlank()) {
            val view = PokemonView(
                key = "svhub-card:$species",
                route = "",
                speciesId = species,
                aspects = emptySet(),
                displayName = card.str("label", rawSpecies),
                dexNumber = 0,
                fakemon = false
            )
            PokemonModelRenderer.renderScene(
                gui = gui,
                view = view,
                instanceId = "pokecards:$index:${card.str("id")}",
                centerX = rect.x + rect.width / 2,
                centerY = rect.y + rect.height / 2 + 5,
                size = (rect.width * 0.95f).toInt().coerceIn(38, 82),
                yaw = 175f,
                zoom = 0.82f,
                pitch = 25f,
                depth = 1300.0 + index
            )
        }
        val label = font.plainSubstrByWidth(card.str("label", card.str("id")), rect.width - 8)
        gui.drawCenteredString(font, label, rect.x + rect.width / 2, rect.y + 7, TEXT)
        val subtitle = font.plainSubstrByWidth(card.str("subtitle"), rect.width - 8)
        if (subtitle.isNotBlank()) gui.drawCenteredString(font, subtitle, rect.x + rect.width / 2, rect.bottom - 14, MUTED)
    }

    private fun drawTable(gui: GuiGraphics, rect: UiRect, gameId: String) {
        val cx = rect.x + rect.width / 2
        val cy = rect.y + rect.height / 2
        val halfW = rect.width / 2
        val halfH = rect.height / 2
        val bands = max(10, min(48, rect.height))
        repeat(bands) { band ->
            val y0 = rect.y + band * rect.height / bands
            val y1 = rect.y + (band + 1) * rect.height / bands
            val mid = (y0 + y1) * 0.5
            val ratio = 1.0 - kotlin.math.abs(mid - cy) / halfH.coerceAtLeast(1).toDouble()
            val width = max(2, (halfW * ratio).toInt())
            gui.fill(cx - width - 2, y0, cx + width + 2, max(y0 + 1, y1), TABLE_EDGE)
            gui.fill(cx - width, y0, cx + width, max(y0 + 1, y1), if (gameId == "uno") TABLE_UNO else TABLE_DRAFT)
        }
    }

    private fun unoColor(value: String): Int = when (value.lowercase()) {
        "red" -> 0xFFE35C5C.toInt()
        "yellow" -> 0xFFE2BE62.toInt()
        "green" -> 0xFF67B578.toInt()
        "blue" -> 0xFF60A5E8.toInt()
        else -> GOLD
    }

    private fun typeColor(value: String): Int = when (value.lowercase()) {
        "fire" -> 0xFFE36C5C.toInt()
        "water" -> 0xFF60A5E8.toInt()
        "grass" -> 0xFF80B56B.toInt()
        "electric" -> 0xFFE2BE62.toInt()
        "psychic", "fairy" -> 0xFFB68BE0.toInt()
        "dark", "ghost" -> 0xFF7B708B.toInt()
        else -> 0xFF4CC7B2.toInt()
    }

    private fun JsonObject.str(key: String, fallback: String = ""): String =
        runCatching { get(key)?.asString ?: fallback }.getOrDefault(fallback)

    private const val TABLE_EDGE = 0xFF253438.toInt()
    private const val TABLE_UNO = 0xFF17302D.toInt()
    private const val TABLE_DRAFT = 0xFF182632.toInt()
    private const val CARD = 0xFF111C20.toInt()
    private const val CARD_HOVER = 0xFF21363B.toInt()
    private const val BORDER = 0xFF31484D.toInt()
    private const val TEXT = 0xFFF2F6F4.toInt()
    private const val MUTED = 0xFF91A6A1.toInt()
    private const val GOLD = 0xFFE2BE62.toInt()
}
