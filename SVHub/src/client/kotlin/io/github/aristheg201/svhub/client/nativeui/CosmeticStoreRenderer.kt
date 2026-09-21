package io.github.aristheg201.svhub.client.nativeui

import com.google.gson.JsonObject
import io.github.aristheg201.svhub.ui.*
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.resources.language.I18n
import java.util.UUID

class CosmeticStoreUi {
    var kind = "ARENA"
    var selected = ""
    val scene = PokemonSceneState()
    val requests = mutableMapOf<String, String>()
}

object CosmeticStoreRenderer {
    data class Hooks(val control: (UiRect, String, Boolean, Boolean, () -> Unit) -> Unit, val intent: (String, JsonObject) -> Unit)
    fun render(gui: GuiGraphics, font: Font, area: UiRect, state: JsonObject, ui: CosmeticStoreUi, hooks: Hooks) {
        fun text(key: String) = I18n.get("gui.svhub.store.$key")
        val balances = state.getAsJsonObject("balances") ?: JsonObject()
        gui.drawString(font, "BeastCoin  ${balances.str("BeastCoin", "—")}    HunterCoin  ${balances.str("HunterCoin", "—")}", area.x, area.y, 0xFFFFCD75.toInt(), true)
        val tabWidth = (area.width - 6) / 2
        listOf("ARENA" to "arenas", "TACTICIAN" to "tacticians").forEachIndexed { index, (kind, label) ->
            hooks.control(UiRect(area.x + index * (tabWidth + 6), area.y + 16, tabWidth, 22), text(label), true, ui.kind == kind) {
                ui.kind = kind; ui.selected = ""
            }
        }
        val offers = state.getAsJsonArray("offers")?.map { it.asJsonObject }?.filter { it.str("kind") == ui.kind }.orEmpty()
        val chosen = offers.find { it.str("id") == ui.selected } ?: offers.firstOrNull { it.bool("equipped") } ?: offers.firstOrNull() ?: return
        ui.selected = chosen.str("id")
        val listWidth = (area.width / 4).coerceIn(100, 156).coerceAtMost(area.width / 2)
        val rowHeight = ((area.height - 45) / offers.size.coerceAtLeast(1)).coerceIn(15, 27)
        offers.forEachIndexed { index, offer ->
            val label = name(offer) + if (offer.bool("equipped")) " ✓" else if (offer.bool("owned")) " •" else ""
            hooks.control(UiRect(area.x, area.y + 44 + index * rowHeight, listWidth, rowHeight - 2), label, true, offer == chosen) { ui.selected = offer.str("id") }
        }
        val stage = UiRect(area.x + listWidth + 7, area.y + 44, (area.width - listWidth - 7).coerceAtLeast(50), (area.height - 105).coerceAtLeast(45))
        val arenaId = if (ui.kind == "ARENA") chosen.str("id") else state.str("arena", "kanto_stadium")
        val arena = MinecraftArenaRegistry.definition(arenaId)
        if (arena != null) {
            val origin = SceneVec3(arena.boardOrigin.x.toDouble(), arena.boardOrigin.y.toDouble(), arena.boardOrigin.z.toDouble())
            val cell = SceneVec3(arena.cellSize.x.toDouble(), arena.cellSize.y.toDouble(), arena.cellSize.z.toDouble())
            val camera = SceneCameraFraming.board(arena.camera(ArenaCameraRole.ARENA_PREVIEW, SceneCameras.TFT), stage, origin,
                arena.boardColumns, arena.boardRows, arena.benchAnchors.map { SceneVec3(it.x.toDouble(), it.y.toDouble(), it.z.toDouble()) }, cellSize = cell)
            val tactician = if (ui.kind == "TACTICIAN") chosen else state.getAsJsonArray("offers")?.map { it.asJsonObject }?.find { it.str("kind") == "TACTICIAN" && it.str("id") == state.str("tactician") }
            val actor = tactician?.let { offer ->
                val scale = runCatching { offer.get("scale").asDouble }.getOrDefault(1.0)
                val position = if (ui.kind == "TACTICIAN") origin + SceneVec3(arena.boardColumns * cell.x / 2, arena.boardRows * cell.y / 2, 0.0)
                    else SceneVec3(arena.tacticianSpawn.x.toDouble(), arena.tacticianSpawn.y.toDouble(), arena.tacticianSpawn.z.toDouble())
                SceneTacticianNode("store:${offer.str("id")}", SceneTransform(position, scale = SceneVec3(scale, scale, scale)),
                    entityId = offer.str("entity"), pokemonSpecies = offer.str("species"), pokemonAspects = offer.str("aspects").split(',').filter(String::isNotBlank).toSet())
            }
            PokemonScene3D.render(gui, font, stage, arena.boardColumns, arena.boardRows, emptyList(), ui.scene,
                camera = camera, arenaId = arenaId, arenaSeed = "store:$arenaId", tactician = actor)
        }
        gui.drawString(font, text("preview") + " · " + name(chosen), stage.x + 6, stage.y + 5, 0xFFF1F5EE.toInt(), true)
        val owned = chosen.bool("owned")
        val equipped = chosen.bool("equipped")
        val label = when { equipped -> text("equipped"); owned -> text("owned"); else -> chosen.str("price") + " " + chosen.str("currency") }
        gui.drawString(font, label, stage.x + 5, stage.bottom + 7, if (owned) 0xFF79DDB9.toInt() else 0xFFFFCD75.toInt(), true)
        val buttonLabel = if (equipped) text("equipped") else if (owned) text("equip") else I18n.get("gui.svhub.store.buy", chosen.str("price"), chosen.str("currency"))
        hooks.control(UiRect(stage.x, stage.bottom + 23, stage.width, 26), buttonLabel, !equipped, equipped) {
            val key = ui.kind + ":" + ui.selected
            hooks.intent(if (owned) "equip" else "buy", JsonObject().apply {
                addProperty("kind", ui.kind); addProperty("id", ui.selected)
                addProperty("requestId", ui.requests.getOrPut(key) { UUID.randomUUID().toString() })
            })
        }
    }
    private fun name(offer: JsonObject) = if (offer.str("kind") == "ARENA") I18n.get("gui.svhub.store.arena.${offer.str("id")}") else offer.str("name")
    private fun JsonObject.str(key: String, fallback: String = "") = runCatching { get(key)?.asString ?: fallback }.getOrDefault(fallback)
    private fun JsonObject.bool(key: String) = runCatching { get(key)?.asBoolean == true }.getOrDefault(false)
}
