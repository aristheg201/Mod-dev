package io.github.aristheg201.svhub.client.nativeui

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.github.aristheg201.svhub.client.cobblemon.PokemonModelRenderer
import io.github.aristheg201.svhub.client.cobblemon.PokemonView
import io.github.aristheg201.svhub.ui.SceneCameras
import io.github.aristheg201.svhub.ui.UiDensity
import io.github.aristheg201.svhub.ui.UiRect
import io.github.aristheg201.svhub.ui.TftLayoutResolver
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.resources.language.I18n
import net.minecraft.resources.ResourceLocation
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal data class TftUnitInfo(
    val id: String,
    val name: String,
    val species: String,
    val cost: Int,
    val role: String,
    val traits: List<String>,
    val hp: Int,
    val attackDamage: Int,
    val defense: Int,
    val specialDefense: Int,
    val attackSpeed: Double,
    val range: Int,
    val manaStart: Int,
    val manaMax: Int,
    val abilityId: String,
    val abilityName: String,
    val abilityTarget: String,
    val damageType: String,
    val damage: Int,
    val heal: Int,
    val shield: Int,
    val radius: Int,
    val stunMs: Int,
    val dash: Int,
    val effects: String
)

internal data class TftTraitTierInfo(
    val threshold: Int,
    val description: String,
    val effects: String,
    val teamEffects: String
)

internal data class TftTraitInfo(
    val id: String,
    val name: String,
    val tiers: List<TftTraitTierInfo>
)

internal data class TftHoverTooltip(
    val title: String,
    val subtitle: String = "",
    val lines: List<String>,
    val accent: Int
)

class TftUiState {
    private data class CombatCounters(val targetId:String?, val casts:Int, val damageDone:Long, val healingDone:Long, val alive:Boolean)
    internal data class CombatPresentation(
        val effects: List<SceneEffectSignal>,
        val animations: List<SceneNativeAnimationSignal>
    )
    private val combatCounters = linkedMapOf<String, CombatCounters>()
    private val deadSince = linkedMapOf<String, Long>()
    private var unitCatalogRaw = ""
    private var traitCatalogRaw = ""
    private var unitCatalog: Map<String, TftUnitInfo> = emptyMap()
    private var traitCatalog: Map<String, TftTraitInfo> = emptyMap()
    private var hoverTooltip: TftHoverTooltip? = null
    val scene = PokemonSceneState()
    var selectedOrigin: String? = null
    var selectedIndex: Int? = null
    var selectedItem: Int? = null

    fun clearUnit() { selectedOrigin = null; selectedIndex = null }
    fun resetCombat() { combatCounters.clear(); deadSince.clear() }
    fun pruneCombat(activeIds:Set<String>) {
        combatCounters.keys.removeIf { it !in activeIds }
        deadSince.keys.removeIf { it !in activeIds }
    }
    fun visibleCombatUnit(instanceId:String, alive:Boolean, now:Long):Boolean {
        if (alive) { deadSince.remove(instanceId); return true }
        val since = deadSince.getOrPut(instanceId) { now }
        return now - since < FAINT_VISIBLE_MS
    }
    internal fun beginFrame() { hoverTooltip = null }
    internal fun unitInfo(id: String): TftUnitInfo? = unitCatalog[id]
    internal fun traitInfo(id: String): TftTraitInfo? = traitCatalog[id]
    internal fun offerTooltip(value: TftHoverTooltip?) { if (hoverTooltip == null && value != null) hoverTooltip = value }
    internal fun tooltip(): TftHoverTooltip? = hoverTooltip

    internal fun updateCatalogs(unitsRaw: String, traitsRaw: String) {
        if (unitsRaw != unitCatalogRaw) {
            unitCatalogRaw = unitsRaw
            unitCatalog = parseUnits(unitsRaw)
        }
        if (traitsRaw != traitCatalogRaw) {
            traitCatalogRaw = traitsRaw
            traitCatalog = parseTraits(traitsRaw)
        }
    }

    private fun parseUnits(raw: String): Map<String, TftUnitInfo> {
        if (raw.isBlank()) return emptyMap()
        val root = runCatching { JsonParser.parseString(raw).asJsonObject }.getOrNull() ?: return emptyMap()
        return root.entrySet().mapNotNull { (id, value) ->
            val obj = runCatching { value.asJsonObject }.getOrNull() ?: return@mapNotNull null
            id to TftUnitInfo(
                id = id,
                name = obj.str("name", id),
                species = obj.str("species"),
                cost = obj.int("cost", 1),
                role = obj.str("role"),
                traits = obj.str("traits").split(',').filter(String::isNotBlank),
                hp = obj.int("hp"),
                attackDamage = obj.int("attackDamage"),
                defense = obj.int("defense"),
                specialDefense = obj.int("specialDefense"),
                attackSpeed = obj.double("attackSpeed"),
                range = obj.int("range"),
                manaStart = obj.int("manaStart"),
                manaMax = obj.int("manaMax"),
                abilityId = obj.str("abilityId"),
                abilityName = obj.str("abilityName"),
                abilityTarget = obj.str("abilityTarget"),
                damageType = obj.str("damageType"),
                damage = obj.int("damage"),
                heal = obj.int("heal"),
                shield = obj.int("shield"),
                radius = obj.int("radius"),
                stunMs = obj.int("stunMs"),
                dash = obj.int("dash"),
                effects = obj.str("effects")
            )
        }.toMap()
    }

    private fun parseTraits(raw: String): Map<String, TftTraitInfo> {
        if (raw.isBlank()) return emptyMap()
        val root = runCatching { JsonParser.parseString(raw).asJsonObject }.getOrNull() ?: return emptyMap()
        return root.entrySet().mapNotNull { (id, value) ->
            val obj = runCatching { value.asJsonObject }.getOrNull() ?: return@mapNotNull null
            val tiers = obj.getAsJsonArray("tiers")?.mapNotNull tierLoop@ { rawTier ->
                val tier = runCatching { rawTier.asJsonObject }.getOrNull() ?: return@tierLoop null
                TftTraitTierInfo(
                    threshold = tier.int("threshold"),
                    description = tier.str("description"),
                    effects = tier.str("effects"),
                    teamEffects = tier.str("teamEffects")
                )
            }.orEmpty()
            id to TftTraitInfo(id, obj.str("name", id), tiers)
        }.toMap()
    }

    private fun JsonObject.str(key: String, fallback: String = "") = runCatching { get(key)?.asString ?: fallback }.getOrDefault(fallback)
    private fun JsonObject.int(key: String, fallback: Int = 0) = runCatching { get(key)?.asInt ?: fallback }.getOrDefault(fallback)
    private fun JsonObject.double(key: String, fallback: Double = 0.0) = runCatching { get(key)?.asDouble ?: fallback }.getOrDefault(fallback)

    internal fun observeCombat(
        instanceId:String,
        targetId:String?,
        casts:Int,
        damageDone:Long,
        healingDone:Long,
        alive:Boolean,
        moveId:String?,
        damageType:String?
    ):CombatPresentation{
        val next=CombatCounters(targetId?.takeIf(String::isNotBlank),casts,damageDone,healingDone,alive)
        val previous=combatCounters.put(instanceId,next)
        val source="tft:"+instanceId
        val target=next.targetId?.let { "tft:"+it }
        if(previous==null){
            val initialAnimations=if(!alive) listOf(
                SceneNativeAnimationSignal("tft:faint:"+instanceId,System.currentTimeMillis(),source,SceneNativeAnimationKind.FAINT)
            ) else emptyList()
            return CombatPresentation(emptyList(),initialAnimations)
        }

        val effects=mutableListOf<SceneEffectSignal>()
        val animations=mutableListOf<SceneNativeAnimationSignal>()
        val casted=casts>previous.casts
        val damaged=damageDone>previous.damageDone

        if(casted){
            effects+=SceneEffectSignal("tft:cast:"+instanceId,casts.toLong(),SceneEffectKind.CAST,source,target)
            animations+=SceneNativeAnimationSignal(
                id="tft:cast:"+instanceId,
                serial=casts.toLong(),
                entityId=source,
                kind=if(damageType=="physical")SceneNativeAnimationKind.PHYSICAL else SceneNativeAnimationKind.SPECIAL,
                targetEntityId=target,
                moveId=moveId
            )
        }
        if(damaged){
            effects+=SceneEffectSignal("tft:damage:"+instanceId,damageDone,SceneEffectKind.PROJECTILE,source,target)
            if(!casted){
                animations+=SceneNativeAnimationSignal(
                    id="tft:attack:"+instanceId,
                    serial=damageDone,
                    entityId=source,
                    kind=SceneNativeAnimationKind.PHYSICAL,
                    targetEntityId=target
                )
            }
            target?.let{
                animations+=SceneNativeAnimationSignal(
                    id="tft:recoil:"+instanceId,
                    serial=damageDone,
                    entityId=it,
                    kind=SceneNativeAnimationKind.RECOIL
                )
            }
        }
        if(healingDone>previous.healingDone){
            effects+=SceneEffectSignal("tft:heal:"+instanceId,healingDone,SceneEffectKind.HEAL,source,source)
            animations+=SceneNativeAnimationSignal(
                id="tft:heal:"+instanceId,
                serial=healingDone,
                entityId=source,
                kind=SceneNativeAnimationKind.STATUS,
                moveId=moveId
            )
        }
        if(previous.alive&&!alive){
            animations+=SceneNativeAnimationSignal(
                id="tft:faint:"+instanceId,
                serial=System.currentTimeMillis(),
                entityId=source,
                kind=SceneNativeAnimationKind.FAINT
            )
        }
        return CombatPresentation(effects,animations)
    }

    companion object { private const val FAINT_VISIBLE_MS=900L }
}

object TftGameRenderer {
    private data class UnitToken(
        val instanceId: String,
        val unitId: String,
        val species: String,
        val star: Int,
        val hp: Int,
        val maxHp: Int,
        val mana: Int,
        val maxMana: Int,
        val team: Int,
        val aspects: Set<String>,
        val items: List<String>,
        val targetId: String?,
        val casts: Int,
        val damageDone: Long,
        val healingDone: Long,
        val alive: Boolean
    )
    private data class BenchToken(val index: Int, val instanceId: String, val unitId: String, val species: String, val star: Int, val aspects: Set<String>, val items: List<String>)
    private data class PlayerLine(val id: String, val name: String, val hp: Int, val level: Int, val placement: Int, val eliminated: Boolean)
    private data class TraitLine(val id: String, val name: String, val count: Int, val active: Int, val next: Int, val description: String)
    private data class AugmentChoice(val id: String, val name: String, val description: String)
    private data class DraftOffer(val index: Int, val unitId: String, val species: String, val item: String, val takenBy: String, val unlocked: Boolean, val cost: Int, val x: Float, val y: Float, val aspects: Set<String>)

    data class Hooks(
        val control: (UiRect, String, Boolean, () -> Unit) -> Unit,
        val hit: (UiRect, () -> Unit) -> Unit,
        val action: (String, Map<String, String>) -> Unit,
        val back: () -> Unit
    )

    private val bg = 0xFF091215.toInt()
    private val panel = 0xFF101B1F.toInt()
    private val panel2 = 0xFF18272B.toInt()
    private val line = 0xFF2A3B3F.toInt()
    private val text = 0xFFF2F6F4.toInt()
    private val muted = 0xFF91A6A1.toInt()
    private val accent = 0xFF4CC7B2.toInt()
    private val gold = 0xFFE2BE62.toInt()
    private val danger = 0xFFE36C5C.toInt()
    private val enemy = 0xFFB95E67.toInt()

    fun render(
        gui: GuiGraphics,
        font: Font,
        area: UiRect,
        density: UiDensity,
        view: JsonObject,
        ui: TftUiState,
        mouseX: Int,
        mouseY: Int,
        hooks: Hooks
    ) {
        gui.fill(area.x, area.y, area.right, area.bottom, bg)
        val fields = view.getAsJsonObject("fields") ?: JsonObject()
        ui.beginFrame()
        ui.updateCatalogs(fields.str("unitCatalog"), fields.str("traitCatalog"))
        val phase = view.str("phase")
        val canEdit = fields.str("canEditBoard") == "true"
        val board = view.getAsJsonArray("board")
        val boardTokens = if (board == null) emptyMap() else (0 until board.size()).mapNotNull { index ->
            parseUnit(board[index].asString)?.let { index to it }
        }.toMap()
        val bench = parseBench(fields.str("bench"))
        val players = parsePlayers(fields.str("players"))
        val traits = parseTraits(fields.str("traits"))
        val itemBench = fields.str("itemBench").split(',').filter(String::isNotBlank)
        val augments = parseAugments(fields.str("augmentChoices"))
        val draft = parseDraft(fields.str("draft"))

        val resolved = TftLayoutResolver.resolve(area, density)

        renderHud(gui, font, resolved.hud, fields, phase, view.str("status"), density, hooks, mouseX, mouseY)
        resolved.traits?.let { renderTraits(gui, font, it, traits, mouseX, mouseY, ui) }
        resolved.players?.let { renderPlayers(gui, font, it, players, hooks) }
        if (phase == "draft" && draft.isNotEmpty()) {
            renderCarouselScene(gui, font, resolved.board, draft, fields, ui, hooks, mouseX, mouseY, view.str("sessionId"), view.long("revision"))
        } else {
            renderBoard(gui, font, resolved.board, boardTokens, bench, fields, phase, canEdit, ui, hooks, mouseX, mouseY, view.str("sessionId"))
        }
        renderAugmentHud(gui, font, resolved.board, fields, ui, mouseX, mouseY, hooks)
        renderFooter(gui, font, resolved.footer, density, view, fields, bench, itemBench, canEdit, ui, hooks, mouseX, mouseY)

        if (density == UiDensity.COMPACT && area.height >= 150) renderCompactChips(gui, font, area, traits, players, ui, mouseX, mouseY)
        if (augments.isNotEmpty()) renderAugmentOverlay(gui, font, resolved.board, augments, hooks)
        ui.tooltip()?.let { renderHoverTooltip(gui, font, area, it, mouseX, mouseY) }
    }

    private fun renderHud(gui: GuiGraphics, font: Font, area: UiRect, fields: JsonObject, phase: String, status: String, density: UiDensity, hooks: Hooks, mouseX: Int, mouseY: Int) {
        val h = area.height.coerceAtLeast(18)
        gui.fill(area.x, area.y, area.right, area.bottom, panel)
        hooks.control(UiRect(area.x + 4, area.y + 4, 48, h - 8), "‹", true, hooks.back)
        val round = fields.str("round", "1-1")
        val level = fields.int("level", 2)
        val xp = fields.int("xp")
        val xpNext = fields.int("xpNext")
        val hp = fields.int("hp", 100)
        val goldValue = fields.int("gold")
        val streak = fields.int("streak")
        val timer = ((fields.long("phaseEndsAt") - System.currentTimeMillis()).coerceAtLeast(0L) + 999L) / 1000L
        val title = if (density == UiDensity.COMPACT) "$round • Lv.$level • ${goldValue}g • HP $hp" else "${tr("gui.svhub.game.tft.title")}  •  $round  •  Lv.$level $xp/$xpNext XP  •  ${goldValue}g  •  HP $hp"
        gui.drawString(font, fit(font, title, area.width - 118), area.x + 58, area.y + 7, text, true)
        if (density != UiDensity.COMPACT) {
            val economy = "${tr("gui.svhub.tft.interest")}: ${fields.int("lastInterest")}  •  ${tr("gui.svhub.tft.streak")}: ${if (streak >= 0) "+$streak" else streak}"
            gui.drawString(font, fit(font, economy, area.width - 180), area.x + 58, area.y + 19, muted, false)
        }
        val phaseText = when (phase) { "planning" -> tr("gui.svhub.tft.planning"); "combat" -> tr("gui.svhub.tft.combat"); "draft" -> tr("gui.svhub.tft.draft"); "post" -> tr("gui.svhub.tft.results"); else -> phase }
        gui.drawString(font, "$phaseText ${if (timer > 0) "${timer}s" else ""}", area.right - 58, area.y + 7, if (phase == "combat") danger else gold, true)
        if (density == UiDensity.WIDE) {
            val semanticStatus = when {
                fields.str("eliminated") == "true" -> tr("gui.svhub.tft.eliminated")
                phase == "combat" && fields.str("opponent").isNotBlank() -> trf("gui.svhub.tft.vs", fields.str("opponent"))
                else -> ""
            }
            if (semanticStatus.isNotBlank()) gui.drawString(font, fit(font, semanticStatus, 250), area.right - 305, area.y + 19, muted, false)
        }
    }

    private fun renderTraits(gui: GuiGraphics, font: Font, rect: UiRect, traits: List<TraitLine>, mouseX: Int, mouseY: Int, ui: TftUiState) {
        gui.fill(rect.x, rect.y, rect.right, rect.bottom, panel)
        gui.drawString(font, tr("gui.svhub.tft.traits"), rect.x + 7, rect.y + 7, muted, true)
        var y = rect.y + 23
        traits.take(10).forEach { trait ->
            val active = trait.active > 0
            val color = if (active) accent else muted
            val traitRect = UiRect(rect.x + 5, y, (rect.width - 10).coerceAtLeast(1), 25)
            gui.fill(traitRect.x, traitRect.y, traitRect.right, traitRect.bottom, if (active) 0xFF19312E.toInt() else panel2)
            gui.fill(traitRect.x, traitRect.y, traitRect.x + 3, traitRect.bottom, color)
            if (traitRect.contains(mouseX.toDouble(), mouseY.toDouble())) ui.offerTooltip(traitTooltip(ui, trait))
            gui.drawString(font, fit(font, trait.name, rect.width - 43), rect.x + 12, y + 5, text, active)
            gui.drawString(font, trait.count.toString(), rect.right - 22, y + 5, color, true)
            val threshold = when { trait.next > 0 -> "${trait.active}/${trait.next}"; trait.active > 0 -> "${trait.active}+"; else -> "0" }
            gui.drawString(font, threshold, rect.x + 12, y + 15, muted, false)
            y += 29
            if (y + 25 > rect.bottom) return
        }
    }

    private fun renderPlayers(gui: GuiGraphics, font: Font, rect: UiRect, players: List<PlayerLine>, hooks: Hooks) {
        gui.fill(rect.x, rect.y, rect.right, rect.bottom, panel)
        gui.drawString(font, tr("gui.svhub.tft.players"), rect.x + 7, rect.y + 7, muted, true)
        var y = rect.y + 23
        players.take(8).forEachIndexed { index, p ->
            hooks.hit(UiRect(rect.x + 4, y, rect.width - 8, 22)) { hooks.action("scout", mapOf("target" to p.id)) }
            val color = if (p.eliminated) 0xFF586663.toInt() else if (p.hp <= 30) danger else text
            gui.fill(rect.x + 5, y, rect.right - 5, y + 22, panel2)
            gui.drawString(font, if (p.placement > 0) "#${p.placement}" else "${index + 1}", rect.x + 9, y + 5, if (p.eliminated) muted else gold, true)
            gui.drawString(font, fit(font, p.name, rect.width - 55), rect.x + 30, y + 5, color, false)
            gui.drawString(font, p.hp.coerceAtLeast(0).toString(), rect.right - 24, y + 5, color, true)
            y += 25
            if (y + 20 > rect.bottom) return
        }
    }

    private fun renderBoard(
        gui: GuiGraphics,
        font: Font,
        rect: UiRect,
        units: Map<Int, UnitToken>,
        bench: List<BenchToken>,
        fields: JsonObject,
        phase: String,
        canEdit: Boolean,
        ui: TftUiState,
        hooks: Hooks,
        mouseX: Int,
        mouseY: Int,
        arenaSeed: String
    ) {
        gui.fill(rect.x, rect.y, rect.right, rect.bottom, 0xFF0D171A.toInt())
        val columns = fields.int("boardColumns", 7).coerceIn(2, 12)
        val playerRows = fields.int("boardRows", 4).coerceIn(2, 8)
        val combatRows = playerRows * 2
        val formationCells = columns * playerRows
        val benchSlots = fields.int("benchSlots", 9).coerceIn(1, 24)

        val now = System.currentTimeMillis()
        val visibleUnits = if (phase == "combat") {
            units.filterValues { unit -> ui.visibleCombatUnit(unit.instanceId, unit.alive, now) }
        } else {
            units
        }

        val entities = visibleUnits.mapNotNull { (index, unit) ->
            val view = pokemonView(unit.species, unit.aspects, unit.unitId)
            PokemonSceneEntity(
                id = "tft:" + unit.instanceId,
                view = view,
                label = shortUnit(unit.unitId),
                boardX = (index % columns).toFloat(),
                boardY = (index / columns).toFloat(),
                team = unit.team,
                yaw = if (unit.team == 0) 0f else 180f,
                scale = if (unit.star >= 3) 1.03f else 0.90f,
                hp = unit.hp,
                maxHp = unit.maxHp,
                mana = unit.mana,
                maxMana = unit.maxMana,
                star = unit.star
            )
        }

        val benchEntities = bench.map { unit ->
            PokemonSceneEntity(
                id = "tft:" + unit.instanceId,
                view = pokemonView(unit.species, unit.aspects, unit.unitId),
                label = shortUnit(unit.unitId),
                boardX = unit.index * 0.75f,
                boardY = combatRows + 0.8f,
                scale = 0.65f,
                star = unit.star
            )
        }
        val activeIds=units.values.mapTo(linkedSetOf()){it.instanceId}
        val effectSignals=mutableListOf<SceneEffectSignal>()
        val nativeAnimations=mutableListOf<SceneNativeAnimationSignal>()
        if(phase=="combat"){
            units.values.forEach { unit ->
                val info=ui.unitInfo(unit.unitId)
                val presentation=ui.observeCombat(
                    unit.instanceId,
                    unit.targetId,
                    unit.casts,
                    unit.damageDone,
                    unit.healingDone,
                    unit.alive,
                    info?.abilityId,
                    info?.damageType
                )
                effectSignals+=presentation.effects
                nativeAnimations+=presentation.animations
            }
        }else{
            ui.resetCombat()
        }
        ui.pruneCombat(activeIds)

        val selectedCells = if (ui.selectedOrigin == "board" && ui.selectedIndex != null) {
            setOf(formationCells + ui.selectedIndex!!)
        } else emptySet()
        val legalCells = if (canEdit && (ui.selectedOrigin != null || ui.selectedItem != null)) {
            (formationCells until formationCells * 2).toSet()
        } else emptySet()

        val frame = PokemonScene3D.render(
            gui = gui,
            font = font,
            area = rect.inset(4),
            entities = entities + benchEntities,
            state = ui.scene,
            columns = columns,
            rows = combatRows,
            selectedCells = selectedCells,
            legalCells = legalCells,
            teamSplitRow = playerRows,
            camera = SceneCameras.TFT,
            effects = effectSignals,
            nativeAnimations = nativeAnimations,
            arenaId = fields.str("arenaId", "kanto_stadium"),
            arenaSeed = arenaSeed,
            platforms = (0 until benchSlots).map { index -> ScenePlatform(index * 0.75f, combatRows + 0.8f,
                selected = ui.selectedOrigin == "bench" && ui.selectedIndex == index) },
            extraRows = 2
        )

        val benchByIndex = bench.associateBy { it.index }
        repeat(benchSlots) { index ->
            val point = frame.layout.project(index * 0.75f, combatRows + 0.8f)
            val w = max(14, frame.layout.tileWidth * 2 / 3)
            val h = max(16, frame.layout.tileHeight * 2)
            val hit = UiRect(point.x.roundToInt() - w / 2, point.y.roundToInt() - h, w, h + 8)
            val unit = benchByIndex[index]
            if (hit.contains(mouseX.toDouble(), mouseY.toDouble())) {
                gui.fill(hit.x, hit.bottom - 2, hit.right, hit.bottom, accent)
                if (unit != null) ui.offerTooltip(unitTooltip(ui, unit.unitId, unit.star, unit.items))
            }
            if (canEdit) hooks.hit(hit) {
                when {
                    ui.selectedOrigin == "board" && ui.selectedIndex != null -> {
                        hooks.action("bench", mapOf("slot" to ui.selectedIndex.toString(), "bench" to index.toString()))
                        ui.clearUnit()
                    }
                    ui.selectedOrigin == "bench" && ui.selectedIndex != null -> {
                        if (ui.selectedIndex != index) hooks.action("swap_bench", mapOf("from" to ui.selectedIndex.toString(), "to" to index.toString()))
                        ui.clearUnit()
                    }
                    unit != null && ui.selectedItem != null -> {
                        hooks.action("equip_item", mapOf("item" to ui.selectedItem.toString(), "origin" to "bench", "index" to index.toString()))
                        ui.selectedItem = null
                    }
                    unit != null -> { ui.selectedOrigin = "bench"; ui.selectedIndex = index }
                }
            }
        }
        val tactician = fields.str("tacticianEntity")
        if (tactician.isNotBlank()) {
            val box = min(64, max(24, rect.width / 7))
            VanillaCompanionModelRenderer.render(gui, tactician, rect.right - box - 4, rect.bottom - box - 4,
                rect.right - 4, rect.bottom - 4, true)
        }

        units.entries.firstOrNull { (index, _) -> frame.layout.hitBox(index).contains(mouseX.toDouble(), mouseY.toDouble()) }
            ?.value?.let { unit ->
                ui.offerTooltip(unitTooltip(ui, unit.unitId, unit.star, unit.items, unit.hp, unit.maxHp, unit.mana, unit.maxMana))
            }

        if (canEdit) {
            for (index in formationCells until formationCells * 2) {
                val local = index - formationCells
                val token = units[index]
                hooks.hit(frame.layout.hitBox(index)) {
                    if (ui.selectedItem != null && token != null && token.team == 0) {
                        hooks.action(
                            "equip_item",
                            mapOf("item" to ui.selectedItem.toString(), "origin" to "board", "index" to local.toString())
                        )
                        ui.selectedItem = null
                    } else if (ui.selectedOrigin == "bench" && ui.selectedIndex != null) {
                        hooks.action("deploy", mapOf("bench" to ui.selectedIndex.toString(), "slot" to local.toString()))
                        ui.clearUnit()
                    } else if (ui.selectedOrigin == "board" && ui.selectedIndex != null) {
                        if (ui.selectedIndex == local) {
                            ui.clearUnit()
                        } else {
                            hooks.action("move", mapOf("from" to ui.selectedIndex.toString(), "to" to local.toString()))
                            ui.clearUnit()
                        }
                    } else if (token != null && token.team == 0) {
                        ui.selectedOrigin = "board"
                        ui.selectedIndex = local
                    }
                }
            }
        }

        gui.drawCenteredString(
            font,
            if (phase == "combat") tr("gui.svhub.tft.enemy_board") else tr("gui.svhub.tft.enemy_side"),
            rect.x + rect.width / 2,
            rect.y + 3,
            muted
        )
    }

    private fun renderUnit(gui: GuiGraphics, font: Font, cell: UiRect, unit: UnitToken, clip: UiRect) {
        val border = if (unit.team == 0) accent else enemy
        gui.fill(cell.x + 2, cell.y + 2, cell.x + 4, cell.bottom - 2, border)
        val view = pokemonView(unit.species, unit.aspects, unit.unitId)
        val modelSize = min(cell.width, cell.height + 10)
        val rendered = if (view != null && cell.width >= 34 && cell.height >= 23) {
            PokemonModelRenderer.render(gui, view, cell.x + cell.width / 2, cell.y + cell.height / 2 + 5, max(40, modelSize), 0f, 0.65f, 48f)
        } else false
        if (!rendered) gui.drawCenteredString(font, shortUnit(unit.unitId), cell.x + cell.width / 2, cell.y + cell.height / 2 - 4, text)
        val stars = "★".repeat(unit.star.coerceIn(1, 3))
        gui.drawCenteredString(font, stars, cell.x + cell.width / 2, cell.y + 2, gold)
        if (cell.width >= 24 && unit.items.isNotEmpty()) {
            val glyphs = unit.items.take(3).joinToString("") { itemGlyph(it) }
            gui.drawCenteredString(font, glyphs, cell.x + cell.width / 2, cell.bottom - 16, 0xFFE7D98B.toInt())
        }
        if (unit.maxHp > 0) {
            val barW = (cell.width - 8).coerceAtLeast(8)
            val x = cell.x + (cell.width - barW) / 2
            val y = cell.bottom - 7
            gui.fill(x, y, x + barW, y + 3, 0xFF1A2225.toInt())
            val hpW = (barW * unit.hp.coerceIn(0, unit.maxHp) / unit.maxHp).coerceAtLeast(if (unit.hp > 0) 1 else 0)
            gui.fill(x, y, x + hpW, y + 2, if (unit.team == 0) 0xFF54C97A.toInt() else 0xFFD86668.toInt())
            if (unit.maxMana > 0) {
                val manaW = (barW * unit.mana.coerceIn(0, unit.maxMana) / unit.maxMana)
                gui.fill(x, y + 3, x + manaW, y + 4, 0xFF55A9E8.toInt())
            }
        }
    }

    private fun renderFooter(gui: GuiGraphics, font: Font, rect: UiRect, density: UiDensity, view: JsonObject, fields: JsonObject, bench: List<BenchToken>, items: List<String>, canEdit: Boolean, ui: TftUiState, hooks: Hooks, mouseX: Int, mouseY: Int) {
        gui.fill(rect.x, rect.y, rect.right, rect.bottom, panel)
        val benchH = if (density == UiDensity.COMPACT) 18 else 22
        val shopY = rect.y + benchH + if (density == UiDensity.COMPACT) 1 else 3
        val shopH = (rect.bottom - shopY - 1).coerceAtLeast(12)
        val benchW = (rect.width * 2 / 3).coerceAtLeast(90)
        val buttonX = rect.x + benchW + 7
        val buttonW = (rect.right - buttonX).coerceAtLeast(44)
        hooks.control(UiRect(buttonX, rect.y + 2, buttonW / 2 - 2, benchH - 4), tr("gui.svhub.tft.reroll"), view.actionEnabled("refresh")) { hooks.action("refresh", emptyMap()) }
        hooks.control(UiRect(buttonX + buttonW / 2 + 2, rect.y + 2, buttonW / 2 - 2, benchH - 4), tr("gui.svhub.tft.buy_xp"), view.actionEnabled("buy_xp")) { hooks.action("buy_xp", emptyMap()) }

        val cards = view.getAsJsonArray("cards")
        if (cards != null && cards.size() > 0) {
            val shopSlots = fields.int("shopSlots", 5).coerceIn(1, 12)
            val gap = 3
            val cardW = ((rect.width - gap * (shopSlots - 1)) / shopSlots).coerceAtLeast(26)
            repeat(min(shopSlots, cards.size())) { index ->
                val card = cards[index].asJsonObject
                val cardRect = UiRect(rect.x + index * (cardW + gap), shopY, cardW, shopH)
                val cost = card.int("value", 1)
                gui.fill(cardRect.x, cardRect.y, cardRect.right, cardRect.bottom, panel2)
                gui.fill(cardRect.x, cardRect.y, cardRect.x + 3, cardRect.bottom, costColor(cost))
                val species = card.getAsJsonObject("meta")?.str("species").orEmpty()
                val unit = card.str("label", card.str("id"))
                if (density == UiDensity.COMPACT) {
                    gui.drawCenteredString(font, fit(font, "${shortUnit(unit)} ${cost}g", cardRect.width - 6), cardRect.x + cardRect.width / 2, cardRect.y + 4, if (cost >= 4) gold else text)
                } else {
                    val pv = pokemonView(species, card.getAsJsonObject("meta")?.str("aspects").orEmpty().split(',').filter(String::isNotBlank).toSet(), unit)
                    if (pv != null && cardRect.width >= 42 && cardRect.height >= 42) {
                        PokemonModelRenderer.render(gui, pv, cardRect.x + cardRect.width / 2, cardRect.y + cardRect.height / 2 + 6, min(54, cardRect.height), 0f, 0.6f, 35f)
                    }
                    gui.drawString(font, fit(font, unit, cardRect.width - 12), cardRect.x + 6, cardRect.y + 5, text, true)
                    gui.drawString(font, "${cost}g", cardRect.x + 6, cardRect.bottom - 11, gold, true)
                }
                if (cardRect.contains(mouseX.toDouble(), mouseY.toDouble())) {
                    ui.offerTooltip(unitTooltip(ui, unit, 1, emptyList()))
                }
                if (canEdit) hooks.hit(cardRect) { hooks.action("buy", mapOf("index" to card.str("id").substringAfter(':'))) }
            }
        }

        if (items.isNotEmpty()) {
            val itemX = rect.right - min(160, rect.width / 2)
            val itemY = rect.y - 18
            items.take(8).forEachIndexed { index, item ->
                val itemRect = UiRect(itemX + index * 18, itemY, 16, 15)
                gui.fill(itemRect.x, itemRect.y, itemRect.right, itemRect.bottom, if (ui.selectedItem == index) 0xFF544B28.toInt() else panel2)
                gui.drawCenteredString(font, itemGlyph(item), itemRect.x + 8, itemRect.y + 4, if (ui.selectedItem == index) gold else muted)
                if (itemRect.contains(mouseX.toDouble(), mouseY.toDouble())) {
                    ui.offerTooltip(TftHoverTooltip(humanize(item.substringAfter(':')), tr("gui.svhub.tft.tooltip.item"), listOf(item), gold))
                }
                hooks.hit(itemRect) { ui.selectedItem = if (ui.selectedItem == index) null else index }
            }
        }

        if (canEdit && ui.selectedOrigin != null && ui.selectedIndex != null) {
            val sellRect = UiRect(rect.right - 58, rect.y - 18, 56, 15)
            hooks.control(sellRect, tr("gui.svhub.tft.sell"), true) {
                hooks.action("sell", mapOf("origin" to ui.selectedOrigin!!, "index" to ui.selectedIndex.toString())); ui.clearUnit()
            }
        }
    }

    private fun renderAugmentHud(gui: GuiGraphics, font: Font, board: UiRect, fields: JsonObject,
        ui: TftUiState, mouseX: Int, mouseY: Int, hooks: Hooks) {
        val selected = runCatching { JsonParser.parseString(fields.str("selectedAugments", "[]")).asJsonArray }.getOrNull()
        val maxWidth = max(30, min(120, (board.width - 12) / 3))
        selected?.take(3)?.forEachIndexed { index, value ->
            val augment = value.asJsonObject
            val rect = UiRect(board.x + 4 + index * maxWidth, board.y + 3, maxWidth - 3, 17)
            gui.fill(rect.x, rect.y, rect.right, rect.bottom, panel2)
            gui.fill(rect.x, rect.bottom - 2, rect.right, rect.bottom, gold)
            gui.drawString(font, fit(font, augment.str("name"), rect.width - 6), rect.x + 3, rect.y + 4, gold, false)
            if (rect.contains(mouseX.toDouble(), mouseY.toDouble())) ui.offerTooltip(TftHoverTooltip(
                augment.str("name"), augment.str("tier"), listOf(augment.str("description"), humanize(augment.str("mechanic"))), gold))
        }
        val y = board.bottom - 17
        hooks.control(UiRect(board.x + 3, y, 20, 14), "‹", true) { hooks.action("scout", mapOf("target" to "previous")) }
        hooks.control(UiRect(board.x + 26, y, 20, 14), "›", true) { hooks.action("scout", mapOf("target" to "next")) }
        if (fields.str("scouting") == "true") {
            hooks.control(UiRect(board.x + 49, y, 48, 14), tr("gui.svhub.tft.home"), true) { hooks.action("scout", mapOf("target" to "home")) }
            gui.drawString(font, fit(font, fields.str("scoutName"), board.width / 2), board.x + 4, board.y + 24, accent, true)
        }
    }

    private fun renderAugmentOverlay(gui: GuiGraphics, font: Font, board: UiRect, choices: List<AugmentChoice>, hooks: Hooks) {
        val width = min(board.width - 16, 420)
        val height = min(board.height - 12, 112)
        val root = UiRect(board.x + (board.width - width) / 2, board.y + (board.height - height) / 2, width, height)
        gui.fill(root.x, root.y, root.right, root.bottom, 0xF20C1518.toInt())
        gui.fill(root.x, root.y, root.right, root.y + 3, gold)
        gui.drawCenteredString(font, tr("gui.svhub.tft.choose_augment"), root.x + root.width / 2, root.y + 8, text)
        val gap = 5
        val cardW = (root.width - 12 - gap * (choices.size - 1)) / choices.size.coerceAtLeast(1)
        choices.take(3).forEachIndexed { index, choice ->
            val rect = UiRect(root.x + 6 + index * (cardW + gap), root.y + 24, cardW, root.height - 30)
            gui.fill(rect.x, rect.y, rect.right, rect.bottom, panel2)
            gui.fill(rect.x, rect.y, rect.x + 3, rect.bottom, accent)
            gui.drawCenteredString(font, fit(font, choice.name, rect.width - 8), rect.x + rect.width / 2, rect.y + 8, gold)
            drawWrapped(gui, font, choice.description, rect.x + 6, rect.y + 23, rect.width - 12, 3, muted)
            hooks.hit(rect) { hooks.action("choose_augment", mapOf("id" to choice.id)) }
        }
    }

    private fun renderCarouselScene(gui: GuiGraphics, font: Font, board: UiRect, offers: List<DraftOffer>, fields: JsonObject,
        ui: TftUiState, hooks: Hooks, mouseX: Int, mouseY: Int, arenaSeed: String, revision: Long) {
        val entities = offers.filter { it.takenBy.isBlank() }.map { offer -> PokemonSceneEntity(
            id = "carousel:${offer.index}", view = pokemonView(offer.species, offer.aspects, offer.unitId),
            label = shortUnit(offer.unitId), boardX = offer.x + 5f, boardY = offer.y + 5f,
            scale = 0.82f + offer.cost * 0.025f, star = 1, motionSerial = revision
        ) }
        val frame = PokemonScene3D.render(gui, font, board.inset(4), 10, 10, entities, ui.scene,
            camera = SceneCameras.TFT, arenaId = fields.str("arenaId", "tft"), arenaSeed = "carousel:$arenaSeed", extraRows = 1)
        gui.drawCenteredString(font, tr("gui.svhub.tft.shared_draft"), board.x + board.width / 2, board.y + 5, gold)
        val pos = fields.str("carouselPosition").split(',')
        val playerX = pos.getOrNull(0)?.toFloatOrNull() ?: 0f
        val playerY = pos.getOrNull(1)?.toFloatOrNull() ?: 0f
        val tacticianPoint = frame.layout.project(playerX + 5f, playerY + 5f)
        val tacticianBox = max(22, min(48, board.width / 9))
        VanillaCompanionModelRenderer.render(gui, fields.str("tacticianEntity"), tacticianPoint.x.roundToInt() - tacticianBox / 2,
            tacticianPoint.y.roundToInt() - tacticianBox, tacticianPoint.x.roundToInt() + tacticianBox / 2,
            tacticianPoint.y.roundToInt(), true)
        offers.filter { it.takenBy.isBlank() }.forEach { offer ->
            val point = frame.layout.project(offer.x + 5f, offer.y + 5f)
            val hit = UiRect(point.x.roundToInt() - 17, point.y.roundToInt() - 30, 34, 40)
            gui.drawString(font, itemGlyph(offer.item), point.x.roundToInt() + 7, point.y.roundToInt() - 8, gold, true)
            if (hit.contains(mouseX.toDouble(), mouseY.toDouble())) ui.offerTooltip(unitTooltip(ui, offer.unitId, 1, listOf(offer.item)))
            if (offer.unlocked && fields.str("carouselPicked") != "true") hooks.hit(hit) {
                val dx = offer.x - playerX; val dy = offer.y - playerY
                val distance = kotlin.math.sqrt(dx * dx + dy * dy)
                if (distance <= 0.72f) hooks.action("carousel_pick", mapOf("index" to offer.index.toString(), "revision" to revision.toString()))
                else {
                    val step = min(0.8f, distance)
                    hooks.action("carousel_move", mapOf("x" to (playerX + dx / distance * step).toString(), "y" to (playerY + dy / distance * step).toString()))
                }
            }
        }
        if (!offers.any { it.unlocked }) gui.drawCenteredString(font, "Release wave incoming", board.x + board.width / 2, board.bottom - 18, muted)
    }

    private fun renderCompactChips(gui: GuiGraphics, font: Font, area: UiRect, traits: List<TraitLine>, players: List<PlayerLine>, ui: TftUiState, mouseX: Int, mouseY: Int) {
        val y = area.y + 29
        var x = area.x + 4
        traits.filter { it.active > 0 }.take(3).forEach { trait ->
            val value = "${trait.name} ${trait.count}"
            val w = font.width(value) + 10
            val chip = UiRect(x, y, w, 14)
            gui.fill(chip.x, chip.y, chip.right, chip.bottom, 0xFF18302D.toInt())
            gui.drawString(font, value, chip.x + 5, chip.y + 3, accent, false)
            if (chip.contains(mouseX.toDouble(), mouseY.toDouble())) ui.offerTooltip(traitTooltip(ui, trait))
            x += w + 3
        }
        val alive = players.count { !it.eliminated }
        val label = "$alive/8"
        gui.drawString(font, label, area.right - font.width(label) - 5, y + 3, muted, false)
    }

    private fun unitTooltip(
        ui: TftUiState,
        unitId: String,
        star: Int,
        items: List<String>,
        hp: Int = -1,
        maxHp: Int = -1,
        mana: Int = -1,
        maxMana: Int = -1
    ): TftHoverTooltip? {
        val info = ui.unitInfo(unitId) ?: return null
        val traitNames = info.traits.map { id -> ui.traitInfo(id)?.name ?: humanize(id) }
        val lines = mutableListOf<String>()
        if (traitNames.isNotEmpty()) lines += "${tr("gui.svhub.tft.tooltip.traits")}: ${traitNames.joinToString(" • ")}"
        lines += "${tr("gui.svhub.tft.tooltip.skill")}: ${info.abilityName.ifBlank { humanize(unitId) }}"
        val abilityParts = buildList {
            add("${tr("gui.svhub.tft.tooltip.target")}: ${humanize(info.abilityTarget)}")
            if (info.damage > 0) add("${tr("gui.svhub.tft.tooltip.damage")}: ${info.damage} ${humanize(info.damageType)}")
            if (info.heal > 0) add("${tr("gui.svhub.tft.tooltip.heal")}: ${info.heal}")
            if (info.shield > 0) add("${tr("gui.svhub.tft.tooltip.shield")}: ${info.shield}")
            if (info.radius > 0) add("${tr("gui.svhub.tft.tooltip.radius")}: ${info.radius}")
            if (info.stunMs > 0) add("${tr("gui.svhub.tft.tooltip.stun")}: ${"%.1f".format(java.util.Locale.ROOT, info.stunMs / 1000.0)}s")
            if (info.dash > 0) add("${tr("gui.svhub.tft.tooltip.dash")}: ${info.dash}")
            if (info.effects.isNotBlank()) add(humanizeEffects(info.effects))
        }
        if (abilityParts.isNotEmpty()) lines += abilityParts.joinToString(" • ")
        lines += "${tr("gui.svhub.tft.tooltip.stats")}: HP ${info.hp} • AD ${info.attackDamage} • DEF ${info.defense}/${info.specialDefense} • AS ${"%.2f".format(java.util.Locale.ROOT, info.attackSpeed)} • RNG ${info.range}"
        val manaLine = if (maxMana > 0) "$mana/$maxMana" else "${info.manaStart}/${info.manaMax}"
        lines += "${tr("gui.svhub.tft.tooltip.mana")}: $manaLine"
        if (hp >= 0 && maxHp > 0) lines += "HP: $hp/$maxHp"
        if (items.isNotEmpty()) lines += "${tr("gui.svhub.tft.tooltip.items")}: ${items.joinToString(" • ") { humanize(it.substringAfter(':')) }}"
        return TftHoverTooltip(
            title = info.name,
            subtitle = "${"★".repeat(star.coerceIn(1, 3))} • ${info.cost}g • ${humanize(info.role)}",
            lines = lines,
            accent = costColor(info.cost)
        )
    }

    private fun traitTooltip(ui: TftUiState, trait: TraitLine): TftHoverTooltip {
        val info = ui.traitInfo(trait.id)
        val lines = mutableListOf<String>()
        val progress = buildString {
            append(tr("gui.svhub.tft.tooltip.active")).append(": ").append(trait.count)
            if (trait.active > 0) append(" • ").append(tr("gui.svhub.tft.tooltip.tier")).append(" ").append(trait.active)
            if (trait.next > 0) append(" • ").append(tr("gui.svhub.tft.tooltip.next")).append(": ").append(trait.next)
        }
        lines += progress
        val tiers = info?.tiers.orEmpty()
        if (tiers.isNotEmpty()) {
            tiers.forEach { tier ->
                val marker = if (trait.count >= tier.threshold) "✓" else "○"
                lines += "$marker ${tier.threshold}: ${tier.description}"
                if (tier.teamEffects.isNotBlank()) lines += "  ${tr("gui.svhub.tft.tooltip.team")}: ${humanizeEffects(tier.teamEffects)}"
            }
        } else if (trait.description.isNotBlank()) lines += trait.description
        return TftHoverTooltip(
            title = info?.name ?: trait.name,
            subtitle = "${trait.count} ${tr("gui.svhub.tft.tooltip.units")}",
            lines = lines,
            accent = if (trait.active > 0) accent else muted
        )
    }

    private fun renderHoverTooltip(gui: GuiGraphics, font: Font, bounds: UiRect, tooltip: TftHoverTooltip, mouseX: Int, mouseY: Int) {
        val maxWidth = min(248, (bounds.width - 10).coerceAtLeast(96))
        val body = buildList {
            if (tooltip.subtitle.isNotBlank()) addAll(font.split(net.minecraft.network.chat.Component.literal(tooltip.subtitle), maxWidth - 14))
            tooltip.lines.forEach { line ->
                addAll(font.split(net.minecraft.network.chat.Component.literal(line), maxWidth - 14))
            }
        }.take(14)
        val titleWidth = font.width(tooltip.title)
        val bodyWidth = body.maxOfOrNull { line -> font.width(line) } ?: 0
        val width = min(maxWidth, max(titleWidth, bodyWidth) + 14).coerceAtLeast(96)
        val height = 22 + body.size * 10
        val minX = bounds.x + 2
        val maxX = max(minX, bounds.right - width - 2)
        var x = if (mouseX + 13 + width <= bounds.right) mouseX + 13 else mouseX - width - 13
        x = x.coerceIn(minX, maxX)
        val minY = bounds.y + 2
        val maxY = max(minY, bounds.bottom - height - 2)
        val y = (mouseY + 10).coerceIn(minY, maxY)
        gui.fill(x, y, x + width, y + height, 0xF50A1114.toInt())
        gui.fill(x, y, x + 3, y + height, tooltip.accent)
        gui.fill(x, y, x + width, y + 1, tooltip.accent)
        gui.drawString(font, fit(font, tooltip.title, width - 12), x + 8, y + 6, text, true)
        var lineY = y + 17
        body.forEach { sequence ->
            gui.drawString(font, sequence, x + 8, lineY, muted, false)
            lineY += 10
        }
    }

    private fun humanize(raw: String): String =
        raw.replace("full:", "").replace("combo:", "").replace('_', ' ').trim()
            .split(' ').filter(String::isNotBlank).joinToString(" ") { word -> word.replaceFirstChar { ch -> ch.uppercase() } }

    private fun humanizeEffects(raw: String): String = raw.split(',').filter(String::isNotBlank).joinToString(" • ") { entry ->
        val key = entry.substringBefore('=')
        val value = entry.substringAfter('=', "")
        "${humanize(key)}${if (value.isNotBlank()) " $value" else ""}"
    }

    private fun drawHex(gui: GuiGraphics, r: UiRect, fill: Int, border: Int) {
        val cut = max(2, r.width / 8)
        val quarter = max(2, r.height / 4)
        gui.fill(r.x + cut, r.y, r.right - cut, r.bottom, border)
        gui.fill(r.x, r.y + quarter, r.right, r.bottom - quarter, border)
        gui.fill(r.x + cut + 1, r.y + 1, r.right - cut - 1, r.bottom - 1, fill)
        gui.fill(r.x + 1, r.y + quarter + 1, r.right - 1, r.bottom - quarter - 1, fill)
    }

    private fun pokemonView(speciesId: String, aspects: Set<String>, fallback: String): PokemonView? {
        if (speciesId.isBlank()) return null
        val species = ResourceLocation.tryParse(speciesId)?.let(PokemonSpecies::getByIdentifier) ?: return null
        return PokemonView(
            key = "$speciesId|${aspects.sorted().joinToString(",")}",
            route = "",
            speciesId = speciesId,
            aspects = aspects,
            displayName = species.translatedName.string.ifBlank { fallback },
            dexNumber = species.nationalPokedexNumber,
            fakemon = species.resourceIdentifier.namespace != "cobblemon"
        )
    }

    private fun parseUnit(raw: String): UnitToken? {
        if (raw.isBlank()) return null
        val p = raw.split('~')
        if (p.size < 9) return null
        return UnitToken(
            p[0], p[1], p[2], p[3].toIntOrNull() ?: 1,
            p[4].toIntOrNull() ?: -1, p[5].toIntOrNull() ?: -1,
            p[6].toIntOrNull() ?: 0, p[7].toIntOrNull() ?: 0,
            p[8].toIntOrNull() ?: 0,
            p.getOrNull(9).orEmpty().split(',').filter(String::isNotBlank).toSet(),
            p.getOrNull(10).orEmpty().split(',').filter(String::isNotBlank),
            p.getOrNull(13)?.takeIf(String::isNotBlank),
            p.getOrNull(14)?.toIntOrNull() ?: 0,
            p.getOrNull(15)?.toLongOrNull() ?: 0L,
            p.getOrNull(16)?.toLongOrNull() ?: 0L,
            p.getOrNull(17) != "0"
        )
    }

    private fun parseBench(raw: String): List<BenchToken> = raw.split(';').filter(String::isNotBlank).mapNotNull { value ->
        val p = value.split('~'); if (p.size < 5) return@mapNotNull null
        BenchToken(p[0].toIntOrNull() ?: return@mapNotNull null, p[1], p[2], p[3], p[4].toIntOrNull() ?: 1, p.getOrNull(5).orEmpty().split(',').filter(String::isNotBlank).toSet(), p.getOrNull(6).orEmpty().split(',').filter(String::isNotBlank))
    }

    private fun parsePlayers(raw: String): List<PlayerLine> = raw.split(';').filter(String::isNotBlank).mapNotNull { value ->
        val p = value.split('~'); if (p.size < 6) return@mapNotNull null
        PlayerLine(p[0], p[1], p[2].toIntOrNull() ?: 0, p[3].toIntOrNull() ?: 1, p[4].toIntOrNull() ?: 0, p[5] == "1")
    }

    private fun parseTraits(raw: String): List<TraitLine> = raw.split(';').filter(String::isNotBlank).mapNotNull { value ->
        val p = value.split('~'); if (p.size < 5) return@mapNotNull null
        TraitLine(p[0], p.getOrNull(1).orEmpty().ifBlank { p[0] }, p[2].toIntOrNull() ?: 0, p[3].toIntOrNull() ?: 0, p[4].toIntOrNull() ?: 0, p.getOrNull(5).orEmpty())
    }

    private fun parseAugments(raw: String): List<AugmentChoice> = raw.split(';').filter(String::isNotBlank).mapNotNull { value ->
        val p = value.split('~'); if (p.isEmpty()) return@mapNotNull null
        AugmentChoice(p[0], p.getOrNull(1).orEmpty().ifBlank { p[0] }, p.getOrNull(2).orEmpty())
    }

    private fun parseDraft(raw: String): List<DraftOffer> = raw.split(';').filter(String::isNotBlank).mapNotNull { value ->
        val p = value.split('~'); if (p.size < 6) return@mapNotNull null
        DraftOffer(p[0].toIntOrNull() ?: return@mapNotNull null, p[1], p[2], p[3], p[4], p[5] == "1",
            p.getOrNull(6)?.toIntOrNull() ?: 1, p.getOrNull(8)?.toFloatOrNull() ?: 0f,
            p.getOrNull(9)?.toFloatOrNull() ?: 0f, p.getOrNull(10)?.split(',')?.filter(String::isNotBlank)?.toSet().orEmpty())
    }

    private fun JsonObject.actionEnabled(id: String): Boolean = getAsJsonArray("actions")?.let { arr ->
        (0 until arr.size()).map { arr[it].asJsonObject }.firstOrNull { it.str("id") == id }?.bool("enabled", true)
    } ?: false

    private fun JsonObject.str(key: String, fallback: String = ""): String = runCatching { get(key)?.asString ?: fallback }.getOrDefault(fallback)
    private fun JsonObject.int(key: String, fallback: Int = 0): Int = runCatching { get(key)?.asInt ?: fallback }.getOrDefault(fallback)
    private fun JsonObject.long(key: String, fallback: Long = 0L): Long = runCatching { get(key)?.asLong ?: fallback }.getOrDefault(fallback)
    private fun JsonObject.bool(key: String, fallback: Boolean = false): Boolean = runCatching { get(key)?.asBoolean ?: fallback }.getOrDefault(fallback)
    private fun fit(font: Font, value: String, width: Int) = font.plainSubstrByWidth(value, width.coerceAtLeast(4))
    private fun trf(key: String, vararg args: Any) = I18n.get(key, *args)
    private fun tr(key: String) = I18n.get(key)
    private fun shortUnit(id: String) = id.replace('_', ' ').split(' ').joinToString("") { it.take(2) }.take(5).uppercase()
    private fun itemGlyph(id: String) = when {
        id.startsWith("full:") -> "◆"
        id.startsWith("combo:") -> "◇"
        id.contains("sword") -> "⚔"
        id.contains("bow") -> "»"
        id.contains("rod") -> "✦"
        id.contains("vest") -> "▣"
        id.contains("cloak") -> "◇"
        id.contains("belt") -> "▰"
        id.contains("tear") -> "◆"
        id.contains("gloves") -> "✧"
        else -> "•"
    }
    private fun costColor(cost: Int) = when (cost) { 1 -> 0xFF8EA09B.toInt(); 2 -> 0xFF63BE7B.toInt(); 3 -> 0xFF5B9BE5.toInt(); 4 -> 0xFFA66DDB.toInt(); else -> 0xFFE0B44E.toInt() }

    private fun drawWrapped(gui: GuiGraphics, font: Font, value: String, x: Int, y: Int, width: Int, maxLines: Int, color: Int) {
        font.split(net.minecraft.network.chat.Component.literal(value), width).take(maxLines).forEachIndexed { index, seq ->
            gui.drawString(font, seq, x, y + index * 10, color, false)
        }
    }
}
