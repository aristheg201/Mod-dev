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
import io.github.aristheg201.svhub.ui.SceneTransform
import io.github.aristheg201.svhub.ui.SceneVec3
import io.github.aristheg201.svhub.ui.SceneItemModelNode
import io.github.aristheg201.svhub.ui.SceneTacticianNode
import io.github.aristheg201.svhub.ui.SceneCameraPreset
import io.github.aristheg201.svhub.ui.SVHubSceneCamera
import io.github.aristheg201.svhub.ui.SceneCameraFraming
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.resources.language.I18n
import net.minecraft.resources.ResourceLocation
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.sounds.SoundEvents
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal data class TftUnitInfo(
    val id: String,
    val name: String,
    val species: String,
    val scale: Double,
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

enum class TacticianPresentationState { IDLE,WALK,RUN,EMOTE,ROUND_START,VICTORY,DEFEAT,CAROUSEL_MOVEMENT,PICKUP_REACTION }

class TftUiState {
    data class TacticianPose(val point:ArenaPoint,val state:String,val yaw:Float)
    var tacticianDestination: Pair<Double,Double>? = null
    var tacticianLastIntentAt: Long = 0L
    var tacticianLastPosition: String? = null
    private data class CombatCounters(val targetId:String?, val casts:Int, val damageDone:Long, val healingDone:Long, val alive:Boolean)
    internal data class CombatPresentation(
        val effects: List<SceneEffectSignal>,
        val animations: List<SceneNativeAnimationSignal>
    )
    private val combatCounters = linkedMapOf<String, CombatCounters>()
    private val deadSince = linkedMapOf<String, Long>()
    private var unitCatalogRaw = ""
    private var traitCatalogRaw = ""
    private var itemCatalogRaw = ""
    private var unitCatalog: Map<String, TftUnitInfo> = emptyMap()
    private var traitCatalog: Map<String, TftTraitInfo> = emptyMap()
    private var itemStacks: Map<String, ItemStack> = emptyMap()
    private var itemNames: Map<String,String> = emptyMap()
    private var itemDetails:Map<String,List<String>> = emptyMap()
    private var itemRecipes: Map<String, String> = emptyMap()
    private var hoverTooltip: TftHoverTooltip? = null
    private var lastItemEventSerial:Long?=null
    private var lastAcquisitionSerial = -1L
    private var acquisitionUntil = 0L
    internal fun acquisitionVisible(serial: Long): Boolean {
        if (serial != lastAcquisitionSerial) { lastAcquisitionSerial = serial; acquisitionUntil = System.currentTimeMillis() + 7000L }
        return serial > 0 && System.currentTimeMillis() < acquisitionUntil
    }
    val scene = PokemonSceneState()
    var selectedOrigin: String? = null
    var selectedIndex: Int? = null
    var selectedItem: Int? = null
    var selectedItemIdentity: String? = null
    private var itemDragging = false
    data class ItemTarget(val origin: String, val index: Int, val instanceId: String)
    var itemTarget: ItemTarget? = null
    var itemPage = 0
    var setBrowserOpen = false
    var setBrowserTab = "UNITS"
    var setBrowserPage = 0
    internal fun catalogUnits():List<TftUnitInfo> = unitCatalog.values.sortedWith(compareBy<TftUnitInfo>{it.cost}.thenBy{it.name})
    internal fun catalogTraits():List<TftTraitInfo> = traitCatalog.values.sortedBy{it.name}
    var carouselDestination: ArenaPoint? = null
    var carouselOffer: Int? = null
    var carouselLastIntentAt = 0L
    var carouselLastPosition: String? = null
    private var tacticianPoint:ArenaPoint?=null
    private var tacticianAt=System.currentTimeMillis()
    private var tacticianState=TacticianPresentationState.IDLE
    private var tacticianStateSince=tacticianAt
    private var tacticianYaw=0f
    private var cameraDestination:SceneCameraPreset?=null
    private var cameraStart:SVHubSceneCamera?=null
    private var cameraCurrent:SVHubSceneCamera?=null
    private var cameraStartedAt=0L
    private var framedSource: SceneCameraPreset?=null
    private var framedArena: MinecraftArenaDefinition?=null
    private var framedSize: Pair<Int,Int>?=null
    private var framedPreset: SceneCameraPreset?=null
    fun framedCamera(destination:SceneCameraPreset,arena:MinecraftArenaDefinition?,viewport:UiRect):SceneCameraPreset {
        if(arena == null) return camera(destination)
        val size=viewport.width to viewport.height
        if(framedSource!=destination || framedArena !== arena || framedSize!=size) {
            framedSource=destination;framedArena=arena;framedSize=size
            framedPreset=SceneCameraFraming.board(destination,viewport,SceneVec3(arena.boardOrigin.x.toDouble(),arena.boardOrigin.y.toDouble(),arena.boardOrigin.z.toDouble()),arena.boardColumns,arena.boardRows,
                arena.framingAnchors().map { SceneVec3(it.x.toDouble(),it.y.toDouble(),it.z.toDouble()) },cellSize=SceneVec3(arena.cellSize.x.toDouble(),arena.cellSize.y.toDouble(),arena.cellSize.z.toDouble()))
        }
        return camera(checkNotNull(framedPreset))
    }
    fun camera(destination:SceneCameraPreset,now:Long=System.currentTimeMillis()):SceneCameraPreset {
        val target=SVHubSceneCamera(destination.position,destination.target,destination.fov,destination.near,destination.far)
        if(cameraDestination != destination) {
            cameraStart=cameraCurrent ?: target;cameraStartedAt=now;cameraDestination=destination
        }
        val t=if(destination.transitionMs==0L) 1.0 else ((now-cameraStartedAt).toDouble()/destination.transitionMs).coerceIn(0.0,1.0)
        val sampled=(cameraStart ?: target).interpolate(target,t*t*(3-2*t))
        cameraCurrent=sampled
        return destination.copy(position=sampled.position,target=sampled.target,fov=sampled.fov,near=sampled.near,far=sampled.far)
    }
    internal fun tacticianState()=tacticianState
    fun updateItems(raw: String) {
        if (raw == itemCatalogRaw) return
        itemCatalogRaw = raw
        val root = runCatching { JsonParser.parseString(raw).asJsonObject }.getOrNull()
        itemNames=root?.entrySet()?.associate { (id,value) -> id to (value.asJsonObject.get("name")?.asString ?: id) }.orEmpty()
        itemStacks = root?.entrySet()?.mapNotNull { (id, value) ->
            val rawStack = runCatching { value.asJsonObject.get("stack")?.asString }.getOrNull() ?: return@mapNotNull null
            val stackId = ResourceLocation.tryParse(rawStack) ?: return@mapNotNull null
            val item = BuiltInRegistries.ITEM.getOptional(stackId).orElse(null) ?: return@mapNotNull null
            id to ItemStack(item)
        }?.toMap().orEmpty()
        itemRecipes=root?.entrySet()?.mapNotNull{(id,value)->
            val obj=runCatching{value.asJsonObject}.getOrNull()?:return@mapNotNull null
            val components=obj.get("components")?.asString.orEmpty().split(',').filter(String::isNotBlank)
            if(components.size==2)components.sorted().joinToString("+") to id else null
        }?.toMap().orEmpty()
        itemDetails=root?.entrySet()?.associate{(id,value)->
            val obj=value.asJsonObject;val lines=mutableListOf<String>()
            obj.get("effects")?.asString?.takeIf(String::isNotBlank)?.let{lines+=it}
            obj.get("components")?.asString?.takeIf(String::isNotBlank)?.let{lines+="Recipe: ${it.split(',').joinToString(" + ",transform=::itemName)}"}
            if(obj.get("kind")?.asString=="component")itemRecipes.filterKeys{key->id in key.split('+')}.forEach{(key,result)->lines+="${key.split('+').joinToString(" + ",transform=::itemName)} → ${itemName(result)}"}
            id to lines
        }.orEmpty()
    }
    fun itemStack(id: String) = itemStacks[id.substringAfter(':').substringBefore('+')]
    fun itemName(id:String)=itemNames[id.substringAfter(':').substringBefore('+')] ?: id
    fun itemDetails(id:String)=itemDetails[id.substringAfter(':').substringBefore('+')].orEmpty()
    fun recipe(first: String, second: String): String? =
        if (first.startsWith("full:") || second.startsWith("full:")) null else itemRecipes[listOf(first, second).sorted().joinToString("+")]
    fun beginItemDrag(index: Int, identity: String) {
        clearUnit()
        itemTarget = null
        selectedItem = index
        selectedItemIdentity = identity
        itemDragging = true
    }
    fun isItemDragging(): Boolean = itemDragging && selectedItem != null && selectedItemIdentity != null
    fun clearItem() {
        selectedItem = null
        selectedItemIdentity = null
        itemTarget = null
        itemDragging = false
    }
    private var lastStarUpSerial: Long? = null
    fun observeStarUp(serial: Long, encoded: String): SceneEffectSignal? {
        val previous = lastStarUpSerial
        lastStarUpSerial = maxOf(previous ?: serial, serial)
        if (previous == null || serial <= previous) return null
        val event = encoded.split('~')
        if (event.size != 4 || event[0] != "STAR_UP" || event[3].isBlank()) return null
        return SceneEffectSignal("tft:star-up:$serial",serial,SceneEffectKind.BURST,"tft:${event[3]}","tft:${event[3]}")
    }
    fun observeItemEvent(serial:Long,encoded:String):SceneEffectSignal?{
        val previous=lastItemEventSerial
        lastItemEventSerial=maxOf(previous?:serial,serial)
        if(previous==null||serial<=previous||encoded.isBlank())return null
        val instanceId=encoded.substringAfterLast(':').takeIf(String::isNotBlank)?:return null
        return SceneEffectSignal("tft:item:$serial",serial,SceneEffectKind.BURST,"tft:$instanceId","tft:$instanceId")
    }
    fun tactician(target:ArenaPoint,bounds:ArenaRegion,requested:String,now:Long=System.currentTimeMillis()):TacticianPose {
        val safe=bounds.clamp(target)
        val previous=tacticianPoint?:safe
        val elapsed=(now-tacticianAt).coerceAtLeast(1)
        val distance=kotlin.math.hypot((safe.x-previous.x).toDouble(),(safe.y-previous.y).toDouble()).toFloat()
        val requestedState=runCatching{TacticianPresentationState.valueOf(requested.uppercase())}.getOrDefault(TacticianPresentationState.IDLE)
        val speed=if(distance>2f)4f else 2f
        val step=(elapsed/1000f*speed).coerceAtMost(distance)
        val next=if(distance<=.001f)safe else bounds.clamp(ArenaPoint(previous.x+(safe.x-previous.x)/distance*step,previous.y+(safe.y-previous.y)/distance*step,safe.z))
        val movement=when{distance>2f->TacticianPresentationState.RUN;distance>.05f->TacticianPresentationState.WALK;else->null}
        if(distance>.01f) tacticianYaw=Math.toDegrees(kotlin.math.atan2((safe.y-previous.y).toDouble(),(safe.x-previous.x).toDouble())).toFloat()
        val holdMs=when(tacticianState){TacticianPresentationState.EMOTE->1200L;TacticianPresentationState.ROUND_START->900L;TacticianPresentationState.PICKUP_REACTION->900L;TacticianPresentationState.VICTORY,TacticianPresentationState.DEFEAT->Long.MAX_VALUE;else->0L}
        val nextState=when{
            tacticianState in setOf(TacticianPresentationState.VICTORY,TacticianPresentationState.DEFEAT)->tacticianState
            movement!=null&&requestedState !in setOf(TacticianPresentationState.VICTORY,TacticianPresentationState.DEFEAT)->movement
            now-tacticianStateSince<holdMs->tacticianState
            else->requestedState
        }
        if(nextState!=tacticianState){tacticianState=nextState;tacticianStateSince=now}
        tacticianPoint=next
        tacticianAt=now
        return TacticianPose(next,tacticianState.name,tacticianYaw)
    }

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
                scale = obj.double("scale", 1.0),
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
        val alive: Boolean,
        val scale: Float
    )
    private data class BenchToken(val index: Int, val instanceId: String, val unitId: String, val species: String, val star: Int, val aspects: Set<String>, val items: List<String>, val scale: Float)
    private data class PlayerLine(val id: String, val name: String, val hp: Int, val level: Int, val placement: Int, val eliminated: Boolean)
    private data class TraitLine(val id: String, val name: String, val count: Int, val active: Int, val next: Int, val description: String)
    private data class AugmentChoice(val id: String, val name: String, val description: String, val tier: String)
    private data class DraftOffer(val index: Int, val unitId: String, val species: String, val item: String, val takenBy: String, val unlocked: Boolean, val cost: Int, val x: Float, val y: Float, val aspects: Set<String>, val scale: Float)

    data class Hooks(
        val control: (UiRect, String, Boolean, () -> Unit) -> Unit,
        val hit: (UiRect, () -> Unit) -> Unit,
        val sceneInput: ((Double, Double) -> Boolean) -> Unit,
        val dropInput: ((Double, Double) -> Boolean) -> Unit,
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
        hooks: Hooks,
        sceneOnly: Boolean = false
    ) {
        gui.fill(area.x,area.y,area.right,area.bottom,bg)
        val fields=view.getAsJsonObject("fields")?:JsonObject()
        ui.beginFrame()
        ui.updateCatalogs(fields.str("unitCatalog"),fields.str("traitCatalog"))
        ui.updateItems(fields.str("itemCatalog","{}"))
        val phase=view.str("phase")
        if(phase!="draft"){ui.carouselDestination=null;ui.carouselOffer=null;ui.carouselLastPosition=null}
        val canEdit=fields.str("canEditBoard")=="true"
        val capabilities=fields.str("capabilities").split(',').filter(String::isNotBlank).toSet()
        val board=view.getAsJsonArray("board")
        val boardTokens=if(board==null)emptyMap() else (0 until board.size()).mapNotNull{index->parseUnit(board[index].asString)?.let{index to it}}.toMap()
        val bench=parseBench(fields.str("bench"))
        val players=parsePlayers(fields.str("players"))
        val traits=parseTraits(fields.str("traits"))
        val itemBench=fields.str("itemBench").split(',').filter(String::isNotBlank)
        if(ui.selectedItem!=null&&(itemBench.getOrNull(ui.selectedItem!!)!=ui.selectedItemIdentity||"CAN_EQUIP_ITEM" !in capabilities))ui.clearItem()
        val augments=parseAugments(fields.str("augmentChoices"))
        val draft=parseDraft(fields.str("draft"))
        val hasLoot=fields.str("pveLoot").split(',').any(String::isNotBlank)
        val phasePresentation=(phase=="combat"&&fields.bool("pveActive"))||hasLoot
        val resolved=TftLayoutResolver.resolve(area,density,phasePresentation,fields.bool("bossRound"))
        val passive=Hooks(control={_,_,_,_->},hit={_,_->},sceneInput={_->},dropInput={_->},action={_,_->},back={})

        if(sceneOnly){
            val stage=area.inset(if(area.width>=300)4 else 2)
            if(phase=="draft"&&draft.isNotEmpty()) renderCarouselScene(gui,font,stage,draft,fields,ui,passive,mouseX,mouseY,view.str("sessionId"),view.long("revision"))
            else renderBoard(gui,font,stage,boardTokens,bench,itemBench,fields,phase,false,ui,passive,mouseX,mouseY,view.str("sessionId"),false)
            return
        }

        if(augments.isNotEmpty()){
            val stage=area.inset(if(density==UiDensity.COMPACT)2 else 5)
            renderBoard(gui,font,stage,boardTokens,bench,itemBench,fields,phase,false,ui,passive,mouseX,mouseY,view.str("sessionId"),false)
            renderAugmentOverlay(gui,font,area,augments,hooks,mouseX,mouseY)
            return
        }

        if(phase=="draft"&&draft.isNotEmpty()){
            val header=UiRect(area.x,area.y,area.width,if(density==UiDensity.COMPACT)22 else 30)
            gui.fill(header.x,header.y,header.right,header.bottom,panel)
            gui.fill(header.x,header.bottom-2,header.right,header.bottom,gold)
            hooks.control(UiRect(header.x+4,header.y+4,48,(header.height-8).coerceAtLeast(14)),"‹",true,hooks.back)
            gui.drawCenteredString(font,fit(font,tr("gui.svhub.tft.shared_draft"),header.width-120),header.x+header.width/2,header.y+7,text)
            val stage=UiRect(area.x+4,header.bottom+4,(area.width-8).coerceAtLeast(60),(area.bottom-header.bottom-8).coerceAtLeast(40))
            renderCarouselScene(gui,font,stage,draft,fields,ui,hooks,mouseX,mouseY,view.str("sessionId"),view.long("revision"))
            return
        }

        renderBoard(gui,font,resolved.board,boardTokens,bench,itemBench,fields,phase,canEdit,ui,hooks,mouseX,mouseY,view.str("sessionId"))
        resolved.phaseBanner?.let { banner ->
            gui.fill(banner.x,banner.y,banner.right,banner.bottom,panel)
            val titleH=if(phasePresentation && banner.height>=30)16 else 0
            if(titleH>0){
                val title = tr(if(hasLoot) "gui.svhub.tft.loot_ready" else if(fields.bool("bossRound")) "gui.svhub.tft.boss_round" else "gui.svhub.tft.pve_round")
                val count = fields.str("pveLoot").split(',').count(String::isNotBlank)
                val label = title + " · " + fields.str("round") + if(hasLoot) " · $count" else ""
                gui.fill(banner.x,banner.y,banner.x+3,banner.y+titleH,if(fields.bool("bossRound"))danger else gold)
                gui.drawCenteredString(font,fit(font,label,banner.width-12),banner.x+banner.width/2,banner.y+4,text)
            }
            renderAugmentHud(gui,font,UiRect(banner.x,banner.y+titleH,banner.width,banner.height-titleH),fields,ui,mouseX,mouseY,hooks)
        }
        renderHud(gui,font,resolved.hud,fields,phase,view.str("status"),density,hooks,mouseX,mouseY)
        resolved.traits?.let{renderTraits(gui,font,it,traits,mouseX,mouseY,ui,hooks)}
        resolved.players?.let{renderPlayers(gui,font,it,players,hooks)}
        resolved.itemRail?.let{renderItemRail(gui,font,it,itemBench,capabilities,ui,hooks,mouseX,mouseY)}
        renderFooter(gui,font,resolved.footer,density,view,fields,bench,canEdit,"CAN_BUY_UNIT" in capabilities,"CAN_SELL" in capabilities,ui,hooks,mouseX,mouseY)
        if(density==UiDensity.COMPACT&&area.height>=150)renderCompactChips(gui,font,resolved.board,traits,players,ui,mouseX,mouseY)
        if(ui.setBrowserOpen) renderSetBrowser(gui,font,area,ui,hooks,mouseX,mouseY)
        if(ui.isItemDragging())renderDraggedItem(gui,ui,itemBench,mouseX,mouseY)
        ui.tooltip()?.let{renderHoverTooltip(gui,font,area,it,mouseX,mouseY)}
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
        val title = if (density == UiDensity.COMPACT) "$round • Lv.$level • ${goldValue}g • HP $hp" else "${tr("gui.svhub.game.tft.title")}  •  $round"
        val phaseWidth=if(density==UiDensity.COMPACT)60 else 100
        gui.drawString(font, fit(font, title, area.width - phaseWidth - 70), area.x + 58, area.y + 7, text, true)
        if (density != UiDensity.COMPACT) {
            val economy = "${goldValue}g  •  HP $hp  •  ${tr("gui.svhub.tft.interest")}: ${fields.int("lastInterest")}  •  ${tr("gui.svhub.tft.streak")}: ${if (streak >= 0) "+$streak" else streak}"
            gui.drawString(font, fit(font, economy, area.width - 180), area.x + 58, area.y + 19, muted, false)
        }
        val phaseText = when (phase) { "planning" -> tr("gui.svhub.tft.planning"); "combat" -> tr("gui.svhub.tft.combat"); "draft" -> tr("gui.svhub.tft.draft"); "post" -> tr("gui.svhub.tft.results"); else -> phase }
        gui.drawString(font, fit(font,"$phaseText ${if (timer > 0) "${timer}s" else ""}",phaseWidth-6), area.right - phaseWidth, area.y + 7, if (phase == "combat") danger else gold, true)
        if (density == UiDensity.WIDE) {
            val semanticStatus = when {
                fields.str("eliminated") == "true" -> tr("gui.svhub.tft.eliminated")
                phase == "combat" && fields.str("opponent").isNotBlank() -> trf("gui.svhub.tft.vs", fields.str("opponent"))
                else -> ""
            }
            val statusWidth=(area.width-430).coerceAtMost(250)
            if (semanticStatus.isNotBlank() && statusWidth>=70 && !fields.bool("pveActive"))
                gui.drawString(font, fit(font, semanticStatus, statusWidth), area.right-statusWidth-6, area.y+19, muted, false)
        }
    }

    private fun renderTraits(
        gui: GuiGraphics, font: Font, rect: UiRect, traits: List<TraitLine>,
        mouseX: Int, mouseY: Int, ui: TftUiState, hooks: Hooks
    ) {
        gui.fill(rect.x, rect.y, rect.right, rect.y+20,0xC0101B1F.toInt())
        gui.drawString(font, tr("gui.svhub.tft.traits"), rect.x + 7, rect.y + 7, muted, true)
        val allRect=UiRect(rect.right-34,rect.y+3,30,14)
        hooks.control(allRect,tr("gui.svhub.tft.set"),true){
            ui.setBrowserOpen=true
            ui.setBrowserTab="UNITS"
            ui.setBrowserPage=0
        }
        if(traits.isEmpty()){
            gui.drawCenteredString(font,"—",rect.x+rect.width/2,rect.y+28,muted)
            return
        }
        var y = rect.y + 23
        traits.take(10).forEach { trait ->
            val active = trait.active > 0
            val color = if (active) accent else muted
            val traitRect = UiRect(rect.x + 5, y, (rect.width - 10).coerceAtLeast(1), 25)
            gui.fill(traitRect.x, traitRect.y, traitRect.right, traitRect.bottom, if (active) 0xFF19312E.toInt() else panel2)
            gui.fill(traitRect.x, traitRect.y, traitRect.x + 3, traitRect.bottom, color)
            if (traitRect.contains(mouseX.toDouble(), mouseY.toDouble())) ui.offerTooltip(traitTooltip(ui, trait))
            gui.drawString(font, fit(font, trait.name, rect.width - 24), rect.x + 12, y + 5, text, active)
            gui.drawString(font, trait.count.toString(), rect.right - 22, y + 15, color, true)
            val threshold = when { trait.next > 0 -> "${trait.active}/${trait.next}"; trait.active > 0 -> "${trait.active}+"; else -> "0" }
            gui.drawString(font, threshold, rect.x + 12, y + 15, muted, false)
            y += 29
            if (y + 25 > rect.bottom) return
        }
    }

    private fun renderSetBrowser(gui:GuiGraphics,font:Font,area:UiRect,ui:TftUiState,hooks:Hooks,mouseX:Int,mouseY:Int){
        val margin=(min(area.width,area.height)*.06f).toInt().coerceIn(8,26)
        val panelRect=UiRect(area.x+margin,area.y+margin,(area.width-margin*2).coerceAtLeast(180),(area.height-margin*2).coerceAtLeast(120))
        gui.fill(area.x,area.y,area.right,area.bottom,0xB8000000.toInt())
        gui.fill(panelRect.x,panelRect.y,panelRect.right,panelRect.bottom,0xFA0C1519.toInt())
        gui.fill(panelRect.x,panelRect.y,panelRect.right,panelRect.y+3,gold)
        gui.drawString(font,tr("gui.svhub.tft.set_browser"),panelRect.x+10,panelRect.y+9,text,true)
        hooks.control(UiRect(panelRect.right-30,panelRect.y+5,22,18),"×",true){ui.setBrowserOpen=false}
        val tabY=panelRect.y+30
        hooks.control(UiRect(panelRect.x+10,tabY,70,18),tr("gui.svhub.tft.units"),true){
            ui.setBrowserTab="UNITS";ui.setBrowserPage=0
        }
        hooks.control(UiRect(panelRect.x+84,tabY,70,18),tr("gui.svhub.tft.traits"),true){
            ui.setBrowserTab="TRAITS";ui.setBrowserPage=0
        }
        val body=UiRect(panelRect.x+10,tabY+24,panelRect.width-20,panelRect.height-62)
        val rows=((body.height-24)/22).coerceAtLeast(1)
        val columns=if(body.width>=520)2 else 1
        val pageSize=(rows*columns).coerceAtLeast(1)
        if(ui.setBrowserTab=="TRAITS"){
            val entries=ui.catalogTraits()
            val pages=((entries.size+pageSize-1)/pageSize).coerceAtLeast(1)
            ui.setBrowserPage=ui.setBrowserPage.coerceIn(0,pages-1)
            entries.drop(ui.setBrowserPage*pageSize).take(pageSize).forEachIndexed{offset,trait->
                val col=offset/rows;val row=offset%rows
                val w=(body.width-(columns-1)*6)/columns
                val r=UiRect(body.x+col*(w+6),body.y+row*22,w,19)
                gui.fill(r.x,r.y,r.right,r.bottom,panel2)
                gui.fill(r.x,r.y,r.x+3,r.bottom,accent)
                gui.drawString(font,fit(font,trait.name,r.width-12),r.x+8,r.y+5,text,false)
                if(r.contains(mouseX.toDouble(),mouseY.toDouble())){
                    ui.offerTooltip(TftHoverTooltip(trait.name,tr("gui.svhub.tft.tooltip.trait"),
                        trait.tiers.map{tier->"${tier.threshold}: ${tier.description}".trim()},accent))
                }
            }
            renderBrowserPager(gui,font,panelRect,ui,hooks,pages)
        }else{
            val entries=ui.catalogUnits()
            val pages=((entries.size+pageSize-1)/pageSize).coerceAtLeast(1)
            ui.setBrowserPage=ui.setBrowserPage.coerceIn(0,pages-1)
            entries.drop(ui.setBrowserPage*pageSize).take(pageSize).forEachIndexed{offset,unit->
                val col=offset/rows;val row=offset%rows
                val w=(body.width-(columns-1)*6)/columns
                val r=UiRect(body.x+col*(w+6),body.y+row*22,w,19)
                gui.fill(r.x,r.y,r.right,r.bottom,panel2)
                gui.fill(r.x,r.y,r.x+3,r.bottom,costColor(unit.cost))
                gui.drawString(font,fit(font,"${unit.cost}g  ${unit.name}",r.width-16),r.x+8,r.y+5,if(unit.cost>=4)gold else text,false)
                if(r.contains(mouseX.toDouble(),mouseY.toDouble()))ui.offerTooltip(unitTooltip(ui,unit.id,1,emptyList()))
            }
            renderBrowserPager(gui,font,panelRect,ui,hooks,pages)
        }
    }

    private fun renderBrowserPager(gui:GuiGraphics,font:Font,panelRect:UiRect,ui:TftUiState,hooks:Hooks,pages:Int){
        val y=panelRect.bottom-24
        hooks.control(UiRect(panelRect.x+10,y,22,17),"‹",ui.setBrowserPage>0){ui.setBrowserPage--}
        hooks.control(UiRect(panelRect.right-32,y,22,17),"›",ui.setBrowserPage+1<pages){ui.setBrowserPage++}
        gui.drawCenteredString(font,"${ui.setBrowserPage+1}/$pages",panelRect.x+panelRect.width/2,y+5,muted)
    }

    private fun renderPlayers(gui: GuiGraphics, font: Font, rect: UiRect, players: List<PlayerLine>, hooks: Hooks) {
        gui.fill(rect.x, rect.y, rect.right, rect.y+20,0xC0101B1F.toInt())
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
        tray: List<String>,
        fields: JsonObject,
        phase: String,
        canEdit: Boolean,
        ui: TftUiState,
        hooks: Hooks,
        mouseX: Int,
        mouseY: Int,
        arenaSeed: String,
        showBoardLabel: Boolean = true
    ) {
        gui.fill(rect.x, rect.y, rect.right, rect.bottom, 0xFF0D171A.toInt())
        val columns = fields.int("boardColumns", 7).coerceIn(2, 12)
        val playerRows = fields.int("boardRows", 4).coerceIn(2, 8)
        val combatRows = playerRows * 2
        val formationCells = columns * playerRows
        val benchSlots = fields.int("benchSlots", 9).coerceIn(1, 24)
        val arenaId=fields.str("arenaId","tft")
        val arena=MinecraftArenaRegistry.definition(arenaId)
        val cameraRole=when{
            phase=="draft"->ArenaCameraRole.CAROUSEL
            fields.bool("scouting")->ArenaCameraRole.SCOUTING
            fields.str("result").equals("Victory",true)->ArenaCameraRole.VICTORY
            fields.bool("eliminated")->ArenaCameraRole.DEFEAT
            phase=="combat"&&fields.bool("bossRound")->ArenaCameraRole.BOSS_INTRO
            phase=="combat"&&fields.bool("pveActive")->ArenaCameraRole.PVE_INTRO
            phase=="combat"->ArenaCameraRole.COMBAT
            else->ArenaCameraRole.PREPARATION
        }
        val presentation=ArenaPresentationRuntime.frame(arenaId,cameraRole,SceneCameras.TFT,phase,fields.str("result"))
        val authoredCamera=ui.framedCamera(presentation?.camera?:arena?.camera(cameraRole,SceneCameras.TFT)?:SceneCameras.TFT,arena,rect.inset(4))

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
                boardX = arena?.boardAnchor(index)?.x ?: (index % columns).toFloat(),
                boardY = arena?.boardAnchor(index)?.y ?: (index / columns).toFloat(),
                elevation = arena?.boardAnchor(index)?.z ?: 0f,
                team = unit.team,
                yaw = if (unit.team == 0) 0f else 180f,
                scale = unit.scale * (if (unit.star >= 3) 1.06f else 1f),
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
                boardX = arena?.benchAnchor(unit.index)?.x ?: unit.index * 0.75f,
                boardY = arena?.benchAnchor(unit.index)?.y ?: combatRows + 0.8f,
                elevation = arena?.benchAnchor(unit.index)?.z ?: 0f,
                yaw = 180f,
                scale = unit.scale * 0.65f,
                star = unit.star
            )
        }
        val activeIds=units.values.mapTo(linkedSetOf()){it.instanceId}
        val effectSignals=mutableListOf<SceneEffectSignal>()
        ui.observeStarUp(fields.long("acquisitionSerial"),fields.str("acquisitionEvent"))?.let { effectSignals += it }
        ui.observeItemEvent(fields.long("itemEventSerial"),fields.str("lastItemEvent"))?.let {
            effectSignals += it
            Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2f))
        }
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

        val tacticianNode=arena?.let { definition ->
            val position=fields.str("tacticianPosition").split(',')
            val u=position.getOrNull(0)?.toDoubleOrNull()?.coerceIn(0.0,1.0) ?: .5
            val v=position.getOrNull(1)?.toDoubleOrNull()?.coerceIn(0.0,1.0) ?: .5
            val bounds=definition.tacticianMovementBounds
            val target=ArenaPoint(
                (bounds.minX+(bounds.maxX-bounds.minX)*u).toFloat(),
                (bounds.minY+(bounds.maxY-bounds.minY)*v).toFloat(),
                definition.tacticianSpawn.z
            )
            val pose=ui.tactician(target,bounds,fields.str("tacticianState","IDLE"))
            val tacticianScale=fields.double("tacticianScale",1.0).coerceIn(.2,3.0)
            val pokemonSpecies=fields.str("tacticianSpecies")
            val pokemonAspects=fields.str("tacticianAspects").split(',').filter(String::isNotBlank).toSet()
            SceneTacticianNode(
                id="tft:tactician",
                transform=SceneTransform(SceneVec3(pose.point.x.toDouble(),pose.point.y.toDouble(),pose.point.z.toDouble()),SceneVec3(0.0,0.0,pose.yaw.toDouble()),SceneVec3(tacticianScale,tacticianScale,tacticianScale)),
                entityId=fields.str("tacticianEntity"),
                animation=pose.state,
                pokemonSpecies=pokemonSpecies,
                pokemonAspects=pokemonAspects,
                visible=fields.str("tacticianEntity").isNotBlank()||pokemonSpecies.isNotBlank()
            )
        }
        val pveLootTokens=fields.str("pveLoot").split(',').filter(String::isNotBlank)
        val pveLootNodes=if(pveLootTokens.isNotEmpty()) {
            val anchors=arena?.lootAnchors.orEmpty().ifEmpty { listOf(ArenaPoint(3.5f,4f,0f)) }
            pveLootTokens.take(12).mapIndexedNotNull { index,token ->
                val itemId=pveLootModelId(ui,token) ?: return@mapIndexedNotNull null
                val base=anchors[index%anchors.size]
                val ring=index/anchors.size
                val dx=((ring%3)-1)*.32
                val dy=((ring/3)%3-1)*.24
                val yaw=((System.nanoTime()/35_000_000L+index*29)%360L).toDouble()
                SceneItemModelNode(
                    "tft:pve-loot:"+fields.long("pveLootSerial")+":"+index,
                    SceneTransform(
                        SceneVec3((base.x+dx).toDouble(),(base.y+dy).toDouble(),base.z.toDouble()+.38+kotlin.math.sin(System.nanoTime()/700_000_000.0+index)*.08),
                        SceneVec3(0.0,0.0,yaw),
                        SceneVec3(.78,.78,.78)
                    ),
                    itemId
                )
            }
        } else emptyList()
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
            camera = authoredCamera,
            effects = effectSignals,
            nativeAnimations = nativeAnimations,
            arenaId = arenaId,
            arenaSeed = arenaSeed,
            platforms = (0 until benchSlots).map { index -> val anchor=arena?.benchAnchor(index);ScenePlatform(anchor?.x?:index * 0.75f, anchor?.y?:combatRows + 0.8f,
                selected = ui.selectedOrigin == "bench" && ui.selectedIndex == index) },
            extraRows = 2,
            sceneItems = pveLootNodes,
            tactician = tacticianNode,
            showUnitOverlays = showBoardLabel
        )

        if (!showBoardLabel) return

        presentation?.let { p ->
            MinecraftArenaRenderer.renderPresentation(gui,frame.layout,p,phase)
            p.interactionRegions.forEach { region ->
                hooks.sceneInput { x, y ->
                    val camera = frame.layout.perspective
                    val point = camera?.boardIntersection(x,y,arena?.boardOrigin?.z?.toDouble() ?: 0.0)
                    val contains = point != null && point.x >= region.bounds.minX && point.x < region.bounds.maxX && point.y >= region.bounds.minY && point.y < region.bounds.maxY
                    if (contains && region.action.isNotBlank()) hooks.action(region.action,emptyMap())
                    contains
                }
            }
        }

        if(arena!=null && fields.bool("tacticianCanMove")) {
            val bounds=arena.tacticianMovementBounds
            val raw=fields.str("tacticianPosition")
            val parts=raw.split(',')
            val currentU=parts.getOrNull(0)?.toDoubleOrNull()?.coerceIn(0.0,1.0) ?: .5
            val currentV=parts.getOrNull(1)?.toDoubleOrNull()?.coerceIn(0.0,1.0) ?: .5
            val destination=ui.tacticianDestination
            val now=System.currentTimeMillis()
            if(destination!=null && now-ui.tacticianLastIntentAt>=150 &&
                (ui.tacticianLastPosition!=raw || now-ui.tacticianLastIntentAt>=550)) {
                val du=destination.first-currentU
                val dv=destination.second-currentV
                val distance=kotlin.math.hypot(du,dv)
                if(distance<=.012) {
                    ui.tacticianDestination=null
                } else {
                    val step=min(.21,distance)
                    hooks.action("tactician_move",mapOf(
                        "u" to (currentU+du/distance*step).coerceIn(0.0,1.0).toString(),
                        "v" to (currentV+dv/distance*step).coerceIn(0.0,1.0).toString()
                    ))
                    ui.tacticianLastIntentAt=now
                    ui.tacticianLastPosition=raw
                }
            }
            hooks.sceneInput { x,y ->
                val point=frame.layout.perspective?.boardIntersection(x,y,arena.boardOrigin.z.toDouble())
                    ?: return@sceneInput false
                if(point.x<bounds.minX || point.x>bounds.maxX || point.y<bounds.minY || point.y>bounds.maxY) return@sceneInput false
                val u=((point.x-bounds.minX)/(bounds.maxX-bounds.minX)).coerceIn(0.0,1.0)
                val v=((point.y-bounds.minY)/(bounds.maxY-bounds.minY)).coerceIn(0.0,1.0)
                ui.tacticianDestination=u to v
                true
            }
        } else if(phase=="draft" || fields.bool("scouting")) {
            ui.tacticianDestination=null
        }

        val benchByIndex = bench.associateBy { it.index }
        repeat(benchSlots) { index ->
            val anchor=arena?.benchAnchor(index);val point = frame.layout.project(anchor?.x?:index * 0.75f, anchor?.y?:combatRows + 0.8f, anchor?.z ?: 0f) ?: return@repeat
            val w = max(14, frame.layout.tileWidth * 2 / 3)
            val h = max(16, frame.layout.tileHeight * 2)
            val hit = UiRect(point.x.roundToInt() - w / 2, point.y.roundToInt() - h, w, h + 8)
            val unit = benchByIndex[index]
            if (hit.contains(mouseX.toDouble(), mouseY.toDouble())) {
                gui.fill(hit.x, hit.bottom - 2, hit.right, hit.bottom, accent)
                if (unit != null) ui.offerTooltip(unitTooltip(ui, unit.unitId, unit.star, unit.items))
            }
            fun containsBench(x: Double, y: Double): Boolean {
                val position = anchor ?: ArenaPoint(index * .75f, combatRows + .8f)
                val projected = frame.layout.perspective?.boardIntersection(x, y, position.z.toDouble())
                return if (frame.layout.perspective != null) {
                    projected != null && kotlin.math.abs(projected.x-position.x)<=.4 && kotlin.math.abs(projected.y-position.y)<=.4
                } else hit.contains(x, y)
            }
            fun selectBench() {
                when {
                    ui.selectedOrigin == "board" && ui.selectedIndex != null -> {
                        hooks.action("bench", mapOf("slot" to ui.selectedIndex.toString(), "bench" to index.toString()))
                        ui.clearUnit()
                    }
                    ui.selectedOrigin == "bench" && ui.selectedIndex != null -> {
                        if (ui.selectedIndex != index) hooks.action("swap_bench", mapOf("from" to ui.selectedIndex.toString(), "to" to index.toString()))
                        ui.clearUnit()
                    }
                    unit != null -> { ui.selectedOrigin = "bench"; ui.selectedIndex = index }
                }
            }
            if (canEdit) hooks.sceneInput { x,y ->
                val inside=containsBench(x,y)
                if(inside) selectBench()
                inside
            }
            if (canEdit && unit != null && ui.isItemDragging()) hooks.dropInput { x,y ->
                if(!containsBench(x,y)) false else submitItemDrop(ui,tray,"bench",index,unit.instanceId,hooks)
            }
        }
        fun equippedIcons(instanceId:String, items:List<String>) {
            if(items.isEmpty()) return
            val point=frame.entityHeads["tft:$instanceId"] ?: return
            gui.pose().pushPose()
            try {
                gui.pose().translate((point.x-items.size*5).toDouble(),(point.y+6).toDouble(),0.0)
                gui.pose().scale(.6f,.6f,1f)
                items.take(3).forEachIndexed { slot,item -> ui.itemStack(item)?.let { gui.renderItem(it,slot*17,0) } }
            } finally { gui.pose().popPose() }
        }
        gui.enableScissor(rect.x,rect.y,rect.right,rect.bottom)
        try {
            visibleUnits.values.forEach { equippedIcons(it.instanceId,it.items) }
            bench.forEach { equippedIcons(it.instanceId,it.items) }
        } finally { gui.disableScissor() }
        val hovered = frame.layout.pick(mouseX.toDouble(), mouseY.toDouble())
        hovered?.let(units::get)?.let { unit ->
                ui.offerTooltip(unitTooltip(ui, unit.unitId, unit.star, unit.items, unit.hp, unit.maxHp, unit.mana, unit.maxMana))
            }
        if (hovered != null && canEdit) {
            val hoveredUnit=units[hovered]
            val validItemTarget=ui.isItemDragging() && hovered>=formationCells && hoveredUnit?.team==0
            MinecraftArenaRenderer.renderCellHighlight(gui,frame.layout,hovered,
                if(validItemTarget) gold else if(hovered>=formationCells) accent else danger,validItemTarget)
        }

        if (canEdit) {
            hooks.sceneInput { x, y ->
                val index = frame.layout.pick(x,y) ?: return@sceneInput false
                if (index !in formationCells until formationCells * 2) return@sceneInput false
                val local = index - formationCells
                val token = units[index]
                    if (ui.selectedOrigin == "bench" && ui.selectedIndex != null) {
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
                true
            }
        }
        if(canEdit && ui.isItemDragging()) hooks.dropInput { x,y ->
            val index=frame.layout.pick(x,y) ?: return@dropInput false
            if(index !in formationCells until formationCells*2) return@dropInput false
            val token=units[index] ?: return@dropInput false
            if(token.team!=0) return@dropInput false
            submitItemDrop(ui,tray,"board",index-formationCells,token.instanceId,hooks)
        }

        if(showBoardLabel) {
            gui.drawCenteredString(
                font,
                if (phase == "combat") tr("gui.svhub.tft.enemy_board") else tr("gui.svhub.tft.enemy_side"),
                rect.x + rect.width / 2,
                rect.y + 3,
                muted
            )
        }
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

    private fun renderFooter(gui: GuiGraphics, font: Font, rect: UiRect, density: UiDensity, view: JsonObject, fields: JsonObject, bench: List<BenchToken>, canEdit: Boolean, canBuy:Boolean,canSell:Boolean,ui: TftUiState, hooks: Hooks, mouseX: Int, mouseY: Int) {
        gui.fill(rect.x, rect.y, rect.right, rect.bottom, panel)
        val benchH = if (density == UiDensity.COMPACT) 18 else 22
        val shopY = rect.y + benchH + if (density == UiDensity.COMPACT) 1 else 3
        val shopH = (rect.bottom - shopY - 1).coerceAtLeast(12)
        val benchW = (rect.width / 2).coerceAtLeast(90)
        val buttonX = rect.x + benchW + 7
        val buttonW = (rect.right - buttonX).coerceAtLeast(44)
        val progressWidth=(benchW-12).coerceAtLeast(20)
        gui.drawString(font,"Lv.${fields.int("level",2)}  •  ${fields.int("xp")}/${fields.int("xpNext")} XP",rect.x+5,rect.y+3,text,false)
        gui.fill(rect.x+5,rect.y+benchH-5,rect.x+5+progressWidth,rect.y+benchH-3,line)
        val progress=(fields.int("xp").toFloat()/fields.int("xpNext",1).coerceAtLeast(1)).coerceIn(0f,1f)
        gui.fill(rect.x+5,rect.y+benchH-5,rect.x+5+(progressWidth*progress).toInt(),rect.y+benchH-3,accent)
        val buttonCell = buttonW / 3
        hooks.control(UiRect(buttonX, rect.y + 2, buttonCell - 2, benchH - 4), tr("gui.svhub.tft.reroll"), view.actionEnabled("refresh")) { hooks.action("refresh", emptyMap()) }
        hooks.control(UiRect(buttonX + buttonCell, rect.y + 2, buttonCell - 2, benchH - 4), tr("gui.svhub.tft.buy_xp"), view.actionEnabled("buy_xp")) { hooks.action("buy_xp", emptyMap()) }
        val locked = fields.bool("shopLocked")
        hooks.control(UiRect(buttonX + buttonCell * 2, rect.y + 2, buttonW - buttonCell * 2 - 2, benchH - 4), tr(if (locked) "gui.svhub.tft.shop_locked" else "gui.svhub.tft.shop_lock"), canBuy) {
            hooks.action("shop_lock", mapOf("locked" to (!locked).toString()))
        }

        val cards = view.getAsJsonArray("cards")
        if (cards != null && cards.size() > 0) {
            val shopSlots = fields.int("shopSlots", 5).coerceIn(1, 12)
            val gap = 3
            val cardW = ((rect.width - gap * (shopSlots - 1)) / shopSlots).coerceAtLeast(26)
            repeat(min(shopSlots, cards.size())) { index ->
                val card = cards[index].asJsonObject
                val cardRect = UiRect(rect.x + index * (cardW + gap), shopY, cardW, shopH)
                val cost = card.int("value", 1)
                val purchaseStar = card.getAsJsonObject("meta")?.int("star", 1) ?: 1
                val elite = card.getAsJsonObject("meta")?.str("elite") == "true"
                gui.fill(cardRect.x, cardRect.y, cardRect.right, cardRect.bottom, panel2)
                gui.fill(cardRect.x, cardRect.y, cardRect.x + 3, cardRect.bottom, costColor(cost))
                val species = card.getAsJsonObject("meta")?.str("species").orEmpty()
                val unit = card.str("label", card.str("id"))
                if (density == UiDensity.COMPACT) {
                    gui.drawCenteredString(font, fit(font, "${shortUnit(unit)} ${cost}g", cardRect.width - 6), cardRect.x + cardRect.width / 2, cardRect.y + 4, if (cost >= 4) gold else text)
                } else {
                    val pv = pokemonView(species, card.getAsJsonObject("meta")?.str("aspects").orEmpty().split(',').filter(String::isNotBlank).toSet(), unit)
                    if (pv != null && cardRect.width >= 42 && cardRect.height >= 32) {
                        PokemonModelRenderer.renderPreview(gui,pv,"tft:shop:$index",UiRect(cardRect.x+8,cardRect.y+15,cardRect.width-16,cardRect.height-27))
                    }
                    gui.drawString(font, fit(font, ui.unitInfo(unit)?.name ?: humanize(unit), cardRect.width - 12), cardRect.x + 6, cardRect.y + 5, text, true)
                    gui.drawString(font, "${cost}g" + if (elite) "  2?" else "", cardRect.x + 6, cardRect.bottom - 11, gold, true)
                }
                if (cardRect.contains(mouseX.toDouble(), mouseY.toDouble())) {
                    val tooltip = unitTooltip(ui, unit, purchaseStar, emptyList())
                    ui.offerTooltip(if (elite && tooltip != null) tooltip.copy(lines = tooltip.lines + tr("gui.svhub.tft.elite_bonus")) else tooltip)
                }
                if (canBuy && card.getAsJsonObject("meta")?.str("enabled") == "true") {
                    hooks.hit(cardRect) { hooks.action("buy", mapOf("index" to card.str("id").substringAfter(':'), "offerId" to card.getAsJsonObject("meta").str("offerId"))) }
                }
            }
        }

        if (canSell && ui.selectedOrigin != null && ui.selectedIndex != null) {
            val sellRect = UiRect(rect.right - 58, rect.y - 18, 56, 15)
            hooks.control(sellRect, tr("gui.svhub.tft.sell"), true) {
                hooks.action("sell", mapOf("origin" to ui.selectedOrigin!!, "index" to ui.selectedIndex.toString())); ui.clearUnit()
            }
        }
    }

    private fun renderItemRail(
        gui: GuiGraphics, font: Font, rect: UiRect, items: List<String>, capabilities: Set<String>,
        ui: TftUiState, hooks: Hooks, mouseX: Int, mouseY: Int
    ) {
        gui.fill(rect.x,rect.y,rect.right,rect.bottom,0xE8101B1F.toInt())
        gui.fill(rect.x,rect.y,rect.x+2,rect.bottom,gold)
        gui.drawString(font,fit(font,tr("gui.svhub.tft.items"),rect.width-12),rect.x+7,rect.y+6,text,true)
        if(items.isEmpty()){gui.drawCenteredString(font,"—",rect.x+rect.width/2,rect.y+25,muted);return}
        val columns=if(rect.width>=72)2 else 1
        val cell=20
        val rows=((rect.height-42)/cell).coerceAtLeast(1)
        val pageSize=(rows*columns).coerceAtLeast(1)
        val pages=(items.size+pageSize-1)/pageSize
        ui.itemPage=ui.itemPage.coerceIn(0,pages-1)
        val first=ui.itemPage*pageSize
        items.drop(first).take(pageSize).forEachIndexed { offset,item ->
            val index=first+offset
            val x=rect.x+6+(offset%columns)*cell
            val y=rect.y+20+(offset/columns)*cell
            val itemRect=UiRect(x,y,18,18)
            val selected=ui.selectedItem==index&&ui.isItemDragging()
            gui.fill(itemRect.x,itemRect.y,itemRect.right,itemRect.bottom,if(selected)0xFF544B28.toInt() else panel2)
            gui.fill(itemRect.x,itemRect.y,itemRect.right,itemRect.y+1,if(selected)gold else line)
            val stack=ui.itemStack(item)
            if(stack!=null&&!stack.isEmpty)gui.renderItem(stack,itemRect.x+1,itemRect.y+1)
            else gui.drawCenteredString(font,itemGlyph(item),itemRect.x+9,itemRect.y+5,if(selected)gold else muted)
            if(itemRect.contains(mouseX.toDouble(),mouseY.toDouble()))
                ui.offerTooltip(TftHoverTooltip(ui.itemName(item),tr("gui.svhub.tft.tooltip.item"),ui.itemDetails(item),gold))
            if("CAN_EQUIP_ITEM" in capabilities)hooks.hit(itemRect){ui.beginItemDrag(index,item)}
        }
        if(pages>1){
            val y=rect.bottom-18
            hooks.control(UiRect(rect.x+5,y,18,14),"‹",ui.itemPage>0){ui.itemPage--}
            hooks.control(UiRect(rect.right-23,y,18,14),"›",ui.itemPage+1<pages){ui.itemPage++}
            gui.drawCenteredString(font,"${ui.itemPage+1}/$pages",rect.x+rect.width/2,y+3,muted)
        }
    }

    private fun renderDraggedItem(gui:GuiGraphics,ui:TftUiState,tray:List<String>,mouseX:Int,mouseY:Int){
        val index=ui.selectedItem?:return
        val identity=ui.selectedItemIdentity?:return
        if(tray.getOrNull(index)!=identity)return
        gui.pose().pushPose()
        try{
            gui.pose().translate(0.0,0.0,450.0)
            gui.fill(mouseX-10,mouseY-10,mouseX+10,mouseY+10,0xC00A1114.toInt())
            gui.fill(mouseX-10,mouseY-10,mouseX+10,mouseY-8,gold)
            ui.itemStack(identity)?.let{gui.renderItem(it,mouseX-8,mouseY-8)}
        }finally{gui.pose().popPose()}
    }

    private fun submitItemDrop(ui:TftUiState,tray:List<String>,origin:String,index:Int,instanceId:String,hooks:Hooks):Boolean{
        val itemIndex=ui.selectedItem?:return false
        val itemId=ui.selectedItemIdentity?:return false
        if(!ui.isItemDragging()||tray.getOrNull(itemIndex)!=itemId)return false
        hooks.action("equip_item",mapOf("item" to itemIndex.toString(),"origin" to origin,"index" to index.toString(),
            "instanceId" to instanceId,"itemId" to itemId))
        return true
    }

    private fun renderAugmentHud(gui: GuiGraphics, font: Font, board: UiRect, fields: JsonObject,
        ui: TftUiState, mouseX: Int, mouseY: Int, hooks: Hooks) {
        val selected = runCatching { JsonParser.parseString(fields.str("selectedAugments", "[]")).asJsonArray }.getOrNull()
        val scouting=fields.bool("scouting")
        val buttonsW=if(scouting)104 else 54
        val maxWidth = max(12, min(120, (board.width - buttonsW - 8) / 3))
        val event = fields.str("acquisitionEvent").split('~')
        val pending = fields.str("pendingItems").split(',').count(String::isNotBlank)
        val feedback = if (ui.acquisitionVisible(fields.long("acquisitionSerial")) && event.size >= 3) {
            if (event[0].endsWith("AUTO_SELL")) trf("gui.svhub.tft.auto_sold", humanize(event[1]), event[2]) +
                event.getOrNull(3)?.takeIf(String::isNotBlank)?.let { " · " + trf("gui.svhub.tft.item_received", it.split(',').joinToString { id -> ui.itemName(id) }) }.orEmpty()
            else trf("gui.svhub.tft.star_up", humanize(event[1]), event[2])
        } else if (pending > 0) trf("gui.svhub.tft.pending_items", pending) else ""
        if (feedback.isNotBlank()) {
            val region = UiRect(board.x + 4, board.y + 2, (board.width - buttonsW - 8).coerceAtLeast(1), board.height - 4)
            gui.drawString(font, fit(font, feedback, region.width), region.x, region.y + 4, gold, false)
            if (region.contains(mouseX.toDouble(), mouseY.toDouble())) ui.offerTooltip(TftHoverTooltip(feedback, lines = listOf(trf("gui.svhub.tft.pending_items", pending)), accent = gold))
        }
        if (feedback.isBlank()) selected?.take(3)?.forEachIndexed { index, value ->
            val augment = value.asJsonObject
            val rect = UiRect(board.x + 4 + index * maxWidth, board.y + 2, maxWidth - 3, (board.height-4).coerceAtMost(17))
            gui.fill(rect.x, rect.y, rect.right, rect.bottom, panel2)
            gui.fill(rect.x, rect.bottom - 2, rect.right, rect.bottom, gold)
            gui.drawString(font, fit(font, augment.str("name"), rect.width - 6), rect.x + 3, rect.y + 4, gold, false)
            if (rect.contains(mouseX.toDouble(), mouseY.toDouble())) ui.offerTooltip(TftHoverTooltip(
                augment.str("name"), augment.str("tier"), listOf(augment.str("description"), humanize(augment.str("mechanic"))), gold))
        }
        val y = board.y + (board.height-14)/2
        val x = board.right-buttonsW
        hooks.control(UiRect(x, y, 20, 14), "‹", true) { hooks.action("scout", mapOf("target" to "previous")) }
        hooks.control(UiRect(x + 23, y, 20, 14), "›", true) { hooks.action("scout", mapOf("target" to "next")) }
        if (fields.str("scouting") == "true") {
            hooks.control(UiRect(x + 46, y, 48, 14), tr("gui.svhub.tft.home"), true) { hooks.action("scout", mapOf("target" to "home")) }
        }
    }

    private fun renderAugmentOverlay(
        gui:GuiGraphics,font:Font,area:UiRect,choices:List<AugmentChoice>,hooks:Hooks,mouseX:Int,mouseY:Int
    ) {
        gui.fill(area.x,area.y,area.right,area.bottom,0xD4000000.toInt())
        val width=min(area.width-24,720).coerceAtLeast(190)
        val height=min(area.height-24,286).coerceAtLeast(118)
        val root=UiRect(area.x+(area.width-width)/2,area.y+(area.height-height)/2,width,height)
        gui.fill(root.x,root.y,root.right,root.bottom,0xF2071014.toInt())
        gui.fill(root.x,root.y,root.right,root.y+3,gold)
        gui.drawCenteredString(font,tr("gui.svhub.tft.choose_augment"),root.x+root.width/2,root.y+11,text)
        val visible=choices.take(3)
        val gap=9
        val cardW=((root.width-22-gap*(visible.size-1))/visible.size.coerceAtLeast(1)).coerceAtLeast(52)
        val cardTop=root.y+31
        val cardH=(root.bottom-cardTop-9).coerceAtLeast(72)
        visible.forEachIndexed{index,choice->
            val base=UiRect(root.x+11+index*(cardW+gap),cardTop,cardW,cardH)
            val hovered=base.contains(mouseX.toDouble(),mouseY.toDouble())
            val edge=if(hovered)0xFFEBD57A.toInt() else accent
            gui.fillGradient(base.x,base.y,base.right,base.bottom,if(hovered)0xFF3C6668.toInt() else 0xFF304F56.toInt(),panel2)
            gui.fill(base.x,base.y,base.x+3,base.bottom,edge)
            val iconSize=if(cardH>=150)32 else 20
            gui.pose().pushPose()
            try {
                gui.pose().translate((base.x+(base.width-iconSize)/2).toDouble(),(base.y+10).toDouble(),0.0)
                gui.pose().scale(iconSize/16f,iconSize/16f,1f)
                gui.renderItem(ItemStack(Items.ENCHANTED_BOOK),0,0)
            } finally { gui.pose().popPose() }
            val nameY=base.y+iconSize+17
            gui.drawCenteredString(font,fit(font,choice.name,base.width-12),base.x+base.width/2,nameY,gold)
            val tierColor=when(choice.tier){"Prismatic"->0xFFC98AF2.toInt();"Gold"->gold;else->accent}
            gui.drawCenteredString(font,fit(font,choice.tier,base.width-12),base.x+base.width/2,nameY+11,tierColor)
            drawWrapped(gui,font,choice.description,base.x+8,nameY+24,base.width-16,((base.bottom-nameY-56)/10).coerceIn(1,6),muted)
            NativeControlRenderer.draw(gui,font,UiRect(base.x+8,base.bottom-27,base.width-16,20),
                tr("gui.svhub.tft.augment.confirm"),mouseX,mouseY,active=hovered)
            hooks.hit(base){hooks.action("choose_augment",mapOf("id" to choice.id))}
        }
    }

    private fun pveLootModelId(ui:TftUiState,token:String):String? = when(token) {
        "loot:gold"->"minecraft:gold_ingot"
        "loot:xp"->"minecraft:experience_bottle"
        "loot:reroll"->"minecraft:emerald"
        "loot:unit"->"minecraft:egg"
        "loot:special"->"minecraft:chest"
        else->ui.itemStack(token)?.let { BuiltInRegistries.ITEM.getKey(it.item).toString() }
    }


    private fun renderCarouselScene(gui: GuiGraphics, font: Font, board: UiRect, offers: List<DraftOffer>, fields: JsonObject,
        ui: TftUiState, hooks: Hooks, mouseX: Int, mouseY: Int, arenaSeed: String, revision: Long) {
        val arenaId=fields.str("carouselArenaId", "carousel_convergence")
        val arena=MinecraftArenaRegistry.definition(arenaId)
        val center=arena?.carouselCenter?:ArenaPoint(0f,0f)
        val entities = offers.filter { it.takenBy.isBlank() }.map { offer -> PokemonSceneEntity(
            id = "carousel:${offer.index}", view = pokemonView(offer.species, offer.aspects, offer.unitId),
            label = shortUnit(offer.unitId), boardX = offer.x + center.x, boardY = offer.y + center.y,
            scale = offer.scale * (0.82f + offer.cost * 0.025f), star = 1, elevation = center.z
        ) }
        val pos = fields.str("carouselPosition").split(',')
        val playerX = pos.getOrNull(0)?.toFloatOrNull() ?: 0f
        val playerY = pos.getOrNull(1)?.toFloatOrNull() ?: 0f
        val target=ArenaPoint(playerX+center.x,playerY+center.y,center.z)
        val radius=fields.str("carouselMovementRadius").toFloatOrNull() ?: 5.6f
        val carouselBounds=ArenaRegion(center.x-radius,center.y-radius,center.x+radius,center.y+radius)
        val tactician=ui.tactician(target,carouselBounds,fields.str("tacticianState","CAROUSEL_MOVEMENT"))
        val time=System.nanoTime()/1_000_000_000.0
        val items=buildList {
            fields.str("carouselCenterDecoration").takeIf(String::isNotBlank)?.let { decoration ->
                add(SceneItemModelNode("carousel:center",SceneTransform(SceneVec3(center.x.toDouble(),center.y.toDouble(),center.z.toDouble()+.35),SceneVec3(0.0,0.0,time*18%360),SceneVec3(1.15,1.15,1.15)),decoration))
            }
            offers.filter { it.takenBy.isBlank() }.mapNotNullTo(this) { offer ->
                val stack=ui.itemStack(offer.item) ?: return@mapNotNullTo null
                SceneItemModelNode("carousel:item:"+offer.index,SceneTransform(SceneVec3((offer.x+center.x).toDouble(),(offer.y+center.y).toDouble(),center.z+1.3+kotlin.math.sin(time*2+offer.index)*.08),SceneVec3(0.0,0.0,time*35%360),SceneVec3(.8,.8,.8)),BuiltInRegistries.ITEM.getKey(stack.item).toString())
            }
        }
        val tacticianScale=fields.double("tacticianScale",1.0).coerceIn(.2,3.0)
        val tacticianSpecies=fields.str("tacticianSpecies")
        val actor=SceneTacticianNode(
            id="tft:tactician",
            transform=SceneTransform(SceneVec3(tactician.point.x.toDouble(),tactician.point.y.toDouble(),tactician.point.z.toDouble()),SceneVec3(0.0,0.0,tactician.yaw.toDouble()),SceneVec3(tacticianScale,tacticianScale,tacticianScale)),
            entityId=fields.str("tacticianEntity"),
            animation=tactician.state,
            pokemonSpecies=tacticianSpecies,
            pokemonAspects=fields.str("tacticianAspects").split(',').filter(String::isNotBlank).toSet(),
            visible=fields.str("tacticianEntity").isNotBlank()||tacticianSpecies.isNotBlank()
        )
        val sceneArea=UiRect(board.x+4,board.y+4,(board.width-8).coerceAtLeast(1),(board.height-28).coerceAtLeast(1))
        val authored=arena?.camera(ArenaCameraRole.CAROUSEL,SceneCameras.TFT)?:SceneCameras.TFT
        val framed=if(arena!=null)SceneCameraFraming.board(authored,sceneArea,
            SceneVec3(arena.boardOrigin.x.toDouble(),arena.boardOrigin.y.toDouble(),arena.boardOrigin.z.toDouble()),
            arena.boardColumns,arena.boardRows,emptyList(),widthFraction=.84,
            cellSize=SceneVec3(arena.cellSize.x.toDouble(),arena.cellSize.y.toDouble(),arena.cellSize.z.toDouble())) else authored
        val frame = PokemonScene3D.render(gui, font, sceneArea, arena?.boardColumns ?: 12, arena?.boardRows ?: 12, entities, ui.scene,
            camera = ui.camera(framed), arenaId = arenaId, arenaSeed = "carousel:$arenaSeed",sceneItems=items,tactician=actor)
        val now=System.currentTimeMillis()
        val unlocked=now>=fields.long("carouselUnlockAt") && !fields.bool("carouselPicked")
        val selectedOffer=ui.carouselOffer?.let { id -> offers.firstOrNull { it.index==id && it.takenBy.isBlank() } }
        if(ui.carouselOffer != null && selectedOffer == null || fields.bool("carouselPicked")) { ui.carouselDestination=null;ui.carouselOffer=null }
        val destination=selectedOffer?.let { ArenaPoint(it.x,it.y) } ?: ui.carouselDestination
        if(destination != null && unlocked && now-ui.carouselLastIntentAt>=160 &&
            (ui.carouselLastPosition!=fields.str("carouselPosition") || now-ui.carouselLastIntentAt>=600)) {
            val dx=destination.x-playerX;val dy=destination.y-playerY
            val distance=kotlin.math.hypot(dx.toDouble(),dy.toDouble()).toFloat()
            val pickup=fields.str("carouselPickupRadius").toFloatOrNull() ?: .72f
            if(selectedOffer != null && distance<=pickup) hooks.action("carousel_pick",mapOf("index" to selectedOffer.index.toString(),"revision" to revision.toString()))
            else if(distance>.03f) {
                val maxMove=fields.str("carouselMaxMove").toFloatOrNull() ?: .8f
                val step=min(maxMove*.98f,distance)
                hooks.action("carousel_move",mapOf("x" to (playerX+dx/distance*step).toString(),"y" to (playerY+dy/distance*step).toString()))
            } else ui.carouselDestination=null
            ui.carouselLastIntentAt=now;ui.carouselLastPosition=fields.str("carouselPosition")
        }
        if(unlocked) hooks.sceneInput { x,y ->
            val point=frame.layout.perspective?.boardIntersection(x,y,center.z.toDouble()) ?: return@sceneInput false
            val dx=(point.x-center.x).toFloat();val dy=(point.y-center.y).toFloat()
            if(kotlin.math.hypot(dx.toDouble(),dy.toDouble())>radius) return@sceneInput false
            ui.carouselDestination=ArenaPoint(dx,dy);ui.carouselOffer=null;true
        }
        offers.filter { it.takenBy.isBlank() }.forEach { offer ->
            fun hit(x:Double,y:Double):Boolean {
                val point=frame.layout.perspective?.boardIntersection(x,y,center.z.toDouble()) ?: return false
                return kotlin.math.hypot(point.x-offer.x-center.x,point.y-offer.y-center.y)<=.6
            }
            if(hit(mouseX.toDouble(),mouseY.toDouble())) ui.offerTooltip(unitTooltip(ui,offer.unitId,1,listOf(offer.item)))
            if(offer.unlocked && !fields.bool("carouselPicked")) hooks.sceneInput { x,y ->
                if(!hit(x,y)) false else { ui.carouselOffer=offer.index;ui.carouselDestination=ArenaPoint(offer.x,offer.y);true }
            }
        }
        gui.drawCenteredString(font, fit(font,tr(if(unlocked) "gui.svhub.tft.carousel.move" else "gui.svhub.tft.carousel.locked"),board.width-8), board.x + board.width / 2, board.bottom - 18, muted)
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
        val species = PokemonModelRenderer.resolveSpecies(speciesId) ?: return null
        val canonicalId = species.resourceIdentifier.toString()
        return PokemonView(
            key = "$canonicalId|${aspects.sorted().joinToString(",")}",
            route = "",
            speciesId = canonicalId,
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
            p.getOrNull(17) != "0",
            p.getOrNull(18)?.toFloatOrNull()?.coerceIn(.1f,8f) ?: 1f
        )
    }

    private fun parseBench(raw: String): List<BenchToken> = raw.split(';').filter(String::isNotBlank).mapNotNull { value ->
        val p = value.split('~'); if (p.size < 5) return@mapNotNull null
        BenchToken(p[0].toIntOrNull() ?: return@mapNotNull null, p[1], p[2], p[3], p[4].toIntOrNull() ?: 1, p.getOrNull(5).orEmpty().split(',').filter(String::isNotBlank).toSet(), p.getOrNull(6).orEmpty().split(',').filter(String::isNotBlank), p.getOrNull(9)?.toFloatOrNull()?.coerceIn(.1f,8f) ?: 1f)
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
        AugmentChoice(p[0], p.getOrNull(1).orEmpty().ifBlank { p[0] }, p.getOrNull(2).orEmpty(), p.getOrNull(5).orEmpty().ifBlank { "Gold" })
    }

    private fun parseDraft(raw: String): List<DraftOffer> = raw.split(';').filter(String::isNotBlank).mapNotNull { value ->
        val p = value.split('~'); if (p.size < 6) return@mapNotNull null
        DraftOffer(p[0].toIntOrNull() ?: return@mapNotNull null, p[1], p[2], p[3], p[4], p[5] == "1",
            p.getOrNull(6)?.toIntOrNull() ?: 1, p.getOrNull(8)?.toFloatOrNull() ?: 0f,
            p.getOrNull(9)?.toFloatOrNull() ?: 0f, p.getOrNull(10)?.split(',')?.filter(String::isNotBlank)?.toSet().orEmpty(),
            p.getOrNull(11)?.toFloatOrNull()?.coerceIn(.1f,8f) ?: 1f)
    }

    private fun JsonObject.actionEnabled(id: String): Boolean = getAsJsonArray("actions")?.let { arr ->
        (0 until arr.size()).map { arr[it].asJsonObject }.firstOrNull { it.str("id") == id }?.bool("enabled", true)
    } ?: false

    private fun JsonObject.str(key: String, fallback: String = ""): String = runCatching { get(key)?.asString ?: fallback }.getOrDefault(fallback)
    private fun JsonObject.int(key: String, fallback: Int = 0): Int = runCatching { get(key)?.asInt ?: fallback }.getOrDefault(fallback)
    private fun JsonObject.double(key: String, fallback: Double = 0.0): Double = runCatching { get(key)?.asDouble ?: fallback }.getOrDefault(fallback)
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
