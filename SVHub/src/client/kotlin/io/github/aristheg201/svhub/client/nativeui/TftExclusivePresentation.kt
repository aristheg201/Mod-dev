package io.github.aristheg201.svhub.client.nativeui

import com.google.gson.JsonObject
import io.github.aristheg201.svhub.ui.TftExclusiveLayout
import io.github.aristheg201.svhub.ui.TftPresentationPolicy
import io.github.aristheg201.svhub.ui.UiDensity
import io.github.aristheg201.svhub.ui.UiRect
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.resources.language.I18n
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.slf4j.LoggerFactory
import java.util.WeakHashMap

/** Owns boss introductions/reward presentation before gameplay registers any HUD or input. */
object TftExclusivePresentation {
    private class State {
        val policy = TftPresentationPolicy()
        var loggedOwner: TftPresentationPolicy.Owner? = null
        var lootKey = ""
        var page = 0
    }
    private val states = WeakHashMap<TftUiState, State>()
    private val logger = LoggerFactory.getLogger("SVHub/Presentation")
    private val text = 0xFFF2F6F4.toInt()
    private val gold = 0xFFE2BE62.toInt()
    private val panel = 0xE6101B1F.toInt()
    private val passive = TftGameRenderer.Hooks(
        control = { _, _, _, _ -> }, hit = { _, _ -> }, sceneInput = { _ -> },
        dropInput = { _ -> }, action = { _, _ -> }, back = { }
    )

    fun renderIfOwned(
        gui: GuiGraphics, font: Font, area: UiRect, density: UiDensity,
        view: JsonObject, ui: TftUiState, mouseX: Int, mouseY: Int,
        hooks: TftGameRenderer.Hooks, sceneOnly: Boolean
    ): Boolean {
        if (sceneOnly) return false
        val fields = view.getAsJsonObject("fields") ?: return false
        val loot = fields.string("pveLoot").split(',').filter(String::isNotBlank)
        val state = states.getOrPut(ui) { State() }
        val input = TftPresentationPolicy.Input(
            sessionId = view.string("sessionId"), round = fields.string("round"), phase = view.string("phase"),
            boss = fields.string("bossRound") == "true", pve = fields.string("pveActive") == "true",
            hasLoot = loot.isNotEmpty(), lootSerial = fields.string("pveLootSerial").toLongOrNull() ?: 0L,
            hasAugments = fields.string("augmentChoices").isNotBlank(), finished = view.string("finished") == "true"
        )
        val owner = state.policy.resolve(input, System.nanoTime() / 1_000_000L)
        val owned = owner == TftPresentationPolicy.Owner.BOSS_INTRO || owner == TftPresentationPolicy.Owner.PVE_REWARD
        if (owner != state.loggedOwner) {
            state.loggedOwner = owner
            if (owned) logger.info("Exclusive TFT presentation {} for round {}; gameplay HUD/input omitted", owner, input.round)
        }
        if (!owned) return false

        ui.clearItem()
        ui.clearUnit()
        // sceneOnly's early return draws the scene but never shop, bench HUD,
        // traits, players, item rail or normal controls. All hooks are inert.
        TftGameRenderer.render(gui, font, area, density, view, ui, mouseX, mouseY, passive, sceneOnly = true)
        val layout = TftExclusiveLayout.resolve(area)
        val title = I18n.get(if (owner == TftPresentationPolicy.Owner.BOSS_INTRO) "gui.svhub.tft.boss_round" else "gui.svhub.tft.loot_ready")
        gui.fill(layout.header.x, layout.header.y, layout.header.right, layout.header.bottom, panel)
        gui.fill(layout.header.x, layout.header.bottom - 2, layout.header.right, layout.header.bottom, gold)
        gui.drawCenteredString(font, fit(font, "$title  ${input.round}", layout.header.width - 8), layout.header.x + layout.header.width / 2, layout.header.y + 6, text)

        if (owner == TftPresentationPolicy.Owner.PVE_REWARD) {
            val key = "${input.sessionId}:${input.round}:${input.lootSerial}"
            if (state.lootKey != key) { state.lootKey = key; state.page = 0 }
            renderLoot(gui, font, layout, loot, ui, state, hooks, mouseX, mouseY, area)
        }
        hooks.control(layout.continueButton, I18n.get("gui.svhub.result.continue"), true) {
            // Rewards have already settled on the server. This does not grant,
            // claim, sell, resign or send any authoritative gameplay action.
            state.policy.dismiss()
        }
        return true
    }

    private fun renderLoot(
        gui: GuiGraphics, font: Font, layout: TftExclusiveLayout, loot: List<String>,
        ui: TftUiState, state: State, hooks: TftGameRenderer.Hooks,
        mouseX: Int, mouseY: Int, viewport: UiRect
    ) {
        val area = layout.body
        val columns = (area.width / 96).coerceIn(1, 4)
        val rows = (area.height / 46).coerceIn(1, 3)
        val pageSize = columns * rows
        val pages = (loot.size + pageSize - 1) / pageSize
        state.page = state.page.coerceIn(0, (pages - 1).coerceAtLeast(0))
        val cellWidth = area.width / columns
        val cellHeight = area.height / rows
        var tooltip: List<String>? = null
        loot.drop(state.page * pageSize).take(pageSize).forEachIndexed { index, token ->
            val rect = UiRect(area.x + index % columns * cellWidth, area.y + index / columns * cellHeight,
                (cellWidth - 3).coerceAtLeast(1), (cellHeight - 3).coerceAtLeast(1))
            val id = token.removePrefix("full:")
            val stack = when {
                token.startsWith("gold:") -> ItemStack(Items.GOLD_INGOT)
                token.startsWith("xp:") -> ItemStack(Items.EXPERIENCE_BOTTLE)
                token.startsWith("free_reroll:") -> ItemStack(Items.PAPER)
                else -> ui.itemStack(id) ?: ItemStack.EMPTY
            }
            val label = when {
                token.startsWith("gold:") -> "+${token.substringAfter(':')} G"
                token.startsWith("xp:") -> "+${token.substringAfter(':')} XP"
                token.startsWith("free_reroll:") -> "${I18n.get("gui.svhub.tft.reroll")} +${token.substringAfter(':')}"
                token.startsWith("unit:") -> ui.unitInfo(token.substringAfter(':'))?.name ?: I18n.get("gui.svhub.result.reward.loot_count")
                !stack.isEmpty -> ui.itemName(id)
                else -> I18n.get("gui.svhub.result.reward.loot_count")
            }
            gui.fill(rect.x, rect.y, rect.right, rect.bottom, panel)
            gui.fill(rect.x, rect.y, rect.x + 2, rect.bottom, gold)
            if (rect.height >= 36) {
                if (!stack.isEmpty) gui.renderItem(stack, rect.x + rect.width / 2 - 8, rect.y + 5)
                else NativePixelArt.icon(gui, "gacha", rect.x + rect.width / 2 - 8, rect.y + 5, 16, gold)
                gui.drawCenteredString(font, fit(font, label, rect.width - 8), rect.x + rect.width / 2, rect.bottom - 12, text)
            } else if (rect.height >= 12) {
                gui.drawCenteredString(font, fit(font, label, rect.width - 8), rect.x + rect.width / 2, rect.y + 2, text)
            }
            if (rect.contains(mouseX.toDouble(), mouseY.toDouble())) tooltip = listOf(label) + ui.itemDetails(id).take(5)
        }
        if (pages > 1) {
            val button = layout.continueButton
            val left = UiRect(viewport.x + 4, button.y, (button.x - viewport.x - 8).coerceAtLeast(1), button.height)
            val right = UiRect(button.right + 4, button.y, (viewport.right - button.right - 8).coerceAtLeast(1), button.height)
            hooks.control(left, "<", state.page > 0) { state.page-- }
            hooks.control(right, "> ${state.page + 1}/$pages", state.page + 1 < pages) { state.page++ }
        }
        tooltip?.let { lines ->
            val width = minOf(viewport.width - 8, (lines.maxOfOrNull(font::width) ?: 40) + 12)
            val height = minOf(viewport.height - 8, lines.size * 11 + 8)
            val x = (mouseX + 10).coerceIn(viewport.x + 4, viewport.right - width - 4)
            val y = (mouseY + 10).coerceIn(viewport.y + 4, viewport.bottom - height - 4)
            gui.fill(x, y, x + width, y + height, 0xFA081115.toInt())
            lines.take((height - 8) / 11).forEachIndexed { index, line ->
                gui.drawString(font, fit(font, line, width - 10), x + 5, y + 4 + index * 11, text, false)
            }
        }
    }

    private fun fit(font: Font, value: String, width: Int): String = font.plainSubstrByWidth(value, width.coerceAtLeast(0))
    private fun JsonObject.string(key: String): String = get(key)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
}
