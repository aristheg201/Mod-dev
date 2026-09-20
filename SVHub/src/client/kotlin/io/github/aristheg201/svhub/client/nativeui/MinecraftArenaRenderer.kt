package io.github.aristheg201.svhub.client.nativeui

import com.google.gson.Gson
import com.google.gson.JsonObject
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import io.github.aristheg201.svhub.ui.SceneCameraPreset
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class ArenaTileRole { FLOOR, ALLY, ENEMY, PATH }
enum class ArenaSurfaceMode { CHECKER, GRID, TRACK, TACTICAL, TERRAIN }

data class MinecraftArenaProp(
    val item: String,
    val x: Float,
    val y: Float,
    val scale: Float = 0.7f
)

data class ArenaPoint(val x: Float, val y: Float, val z: Float = 0f)
data class ArenaRegion(val minX: Float, val minY: Float, val maxX: Float, val maxY: Float) {
    fun clamp(point: ArenaPoint) = ArenaPoint(point.x.coerceIn(minX, maxX), point.y.coerceIn(minY, maxY), point.z)
    val center get() = ArenaPoint((minX + maxX) / 2f, (minY + maxY) / 2f)
}
data class ArenaCameraSet(val spectator: ArenaPoint, val scouting: ArenaPoint, val carousel: ArenaPoint)
data class ArenaInteractionRegion(val id: String, val bounds: ArenaRegion, val action: String)
enum class ArenaCameraRole { NORMAL, SPECTATOR, SCOUTING, CAROUSEL }

data class ArenaPresentationFrame(
    val arenaId:String,
    val camera:SceneCameraPreset,
    val lighting:String,
    val ambientVfx:String,
    val music:String,
    val semanticVfx:String,
    val lootAnchors:List<ArenaPoint>,
    val interactionRegions:List<ArenaInteractionRegion>
)

object ArenaPresentationRuntime {
    private var activeArena:String?=null
    private var activeMusic:String=""
    private var activeMusicInstance:SimpleSoundInstance?=null
    private var lastSemantic:String=""
    fun frame(arenaId:String,role:ArenaCameraRole,fallback:SceneCameraPreset,phase:String,result:String?):ArenaPresentationFrame?{
        val definition=MinecraftArenaRegistry.definition(arenaId)?:return null
        val semantic=when{
            result.equals("Victory",true)->definition.victoryVfx
            result.equals("Defeat",true)->definition.defeatVfx
            phase.equals("combat",true)->definition.combatStartVfx
            else->""
        }
        activeArena=arenaId
        syncMusic(definition.music)
        if(semantic.isNotBlank())lastSemantic=semantic
        return ArenaPresentationFrame(arenaId,definition.camera(role,fallback),definition.lighting,definition.ambientVfx,activeMusic,semantic,definition.lootAnchors,definition.interactionRegions)
    }
    fun leave(arenaId:String){if(activeArena==arenaId){
        activeMusicInstance?.let{Minecraft.getInstance().soundManager.stop(it)}
        activeMusicInstance=null;activeArena=null;activeMusic="";lastSemantic=""
    }}
    private fun syncMusic(requested:String){
        if(requested==activeMusic)return
        activeMusicInstance?.let{Minecraft.getInstance().soundManager.stop(it)}
        activeMusicInstance=null
        activeMusic=requested
        val id=ResourceLocation.tryParse(requested)?:return
        val sound=SimpleSoundInstance(id,SoundSource.MUSIC,.65f,1f,RandomSource.create(),true,0,SoundInstance.Attenuation.NONE,0.0,0.0,0.0,true)
        activeMusicInstance=sound
        Minecraft.getInstance().soundManager.play(sound)
    }
    fun stopAll(){
        activeMusicInstance?.let{Minecraft.getInstance().soundManager.stop(it)}
        activeMusicInstance=null;activeMusic="";activeArena=null;lastSemantic=""
    }
    fun interaction(arenaId:String,x:Float,y:Float)=MinecraftArenaRegistry.definition(arenaId)?.interactionAt(x,y)
    fun music()=activeMusic
    fun semanticVfx()=lastSemantic
}

data class MinecraftArenaDefinition(
    val style: String = "terrain",
    val surface: String = "",
    val floor: List<String> = emptyList(),
    val floorAlt: List<String> = emptyList(),
    val ally: List<String> = emptyList(),
    val enemy: List<String> = emptyList(),
    val path: List<String> = emptyList(),
    val props: List<MinecraftArenaProp> = emptyList(),
    val floorColor: Int = 0xFF173530.toInt(),
    val floorAltColor: Int = 0xFF132C29.toInt(),
    val allyColor: Int = 0xFF173530.toInt(),
    val enemyColor: Int = 0xFF302126.toInt(),
    val pathColor: Int = 0xFF4A463E.toInt(),
    val pathAccentColor: Int = 0xFF756F62.toInt(),
    val gridColor: Int = 0xFF29403F.toInt(),
    val borderColor: Int = 0xFF2A3433.toInt(),
    val edgeColor: Int = 0xFF101719.toInt(),
    val depthColor: Int = 0xFF101719.toInt(),
    val depth: Int = 2,
    val detailEvery: Int = 0,
    val pathDetailEvery: Int = 0
    ,val boardColumns: Int = 7
    ,val boardRows: Int = 8
    ,val boardOrigin: ArenaPoint = ArenaPoint(0f, 0f)
    ,val boardAnchors: List<ArenaPoint> = emptyList()
    ,val benchAnchors: List<ArenaPoint> = emptyList()
    ,val itemBenchAnchors: List<ArenaPoint> = emptyList()
    ,val tacticianSpawn: ArenaPoint = ArenaPoint(6f, 6f)
    ,val tacticianMovementBounds: ArenaRegion = ArenaRegion(-1f, -1f, 7f, 8f)
    ,val humanSpawn: ArenaPoint = ArenaPoint(3f, 7f)
    ,val opponentSpawn: ArenaPoint = ArenaPoint(3f, 0f)
    ,val cameras: ArenaCameraSet = ArenaCameraSet(ArenaPoint(3f,9f,12f), ArenaPoint(3f,8f,10f), ArenaPoint(3f,10f,13f))
    ,val carouselCenter: ArenaPoint = ArenaPoint(3f, 3.5f)
    ,val arenaBounds: ArenaRegion = ArenaRegion(-1f, -1f, 7f, 8f)
    ,val lighting: String = "default"
    ,val ambientVfx: String = ""
    ,val music: String = ""
    ,val lootAnchors: List<ArenaPoint> = emptyList()
    ,val combatStartVfx: String = ""
    ,val victoryVfx: String = ""
    ,val defeatVfx: String = ""
    ,val interactionRegions: List<ArenaInteractionRegion> = emptyList()
) {
    fun boardAnchor(index:Int):ArenaPoint = boardAnchors.getOrNull(index) ?: ArenaPoint(boardOrigin.x+(index%boardColumns),boardOrigin.y+(index/boardColumns),boardOrigin.z)
    fun benchAnchor(index:Int):ArenaPoint = benchAnchors.getOrNull(index) ?: ArenaPoint(boardOrigin.x+index*.75f,boardOrigin.y+boardRows+.8f,boardOrigin.z)
    fun itemAnchor(index:Int):ArenaPoint = itemBenchAnchors.getOrNull(index) ?: ArenaPoint(boardOrigin.x+index*.6f,boardOrigin.y+boardRows+1.6f,boardOrigin.z)
    fun camera(role:ArenaCameraRole,fallback:SceneCameraPreset):SceneCameraPreset {
        val point=when(role){ArenaCameraRole.NORMAL->cameras.spectator;ArenaCameraRole.SPECTATOR->cameras.spectator;ArenaCameraRole.SCOUTING->cameras.scouting;ArenaCameraRole.CAROUSEL->cameras.carousel}
        val span=max(1f,arenaBounds.maxY-arenaBounds.minY);val height=(point.z/span).coerceIn(.5f,1.25f)
        return fallback.copy(id="${fallback.id}:${role.name.lowercase()}",tileScale=height,verticalScale=(point.y/span).coerceIn(.5f,1.25f),originBiasY=(point.y/(span+point.y.coerceAtLeast(0f))).coerceIn(0f,1f),pitch=(18f+point.z*2f).coerceIn(0f,75f))
    }
    fun interactionAt(x:Float,y:Float):ArenaInteractionRegion?=interactionRegions.firstOrNull{x in it.bounds.minX..it.bounds.maxX&&y in it.bounds.minY..it.bounds.maxY}
    fun color(role: ArenaTileRole, alternate: Boolean): Int = when (role) {
        ArenaTileRole.PATH -> pathColor
        ArenaTileRole.ALLY -> allyColor
        ArenaTileRole.ENEMY -> enemyColor
        ArenaTileRole.FLOOR -> if (alternate) floorAltColor else floorColor
    }

    fun palette(role: ArenaTileRole, alternate: Boolean): List<String> = when (role) {
        ArenaTileRole.PATH -> path.ifEmpty { if (alternate) floorAlt else floor }
        ArenaTileRole.ALLY -> ally.ifEmpty { if (alternate) floorAlt else floor }
        ArenaTileRole.ENEMY -> enemy.ifEmpty { if (alternate) floorAlt else floor }
        ArenaTileRole.FLOOR -> if (alternate) floorAlt.ifEmpty { floor } else floor
    }

    fun detailCadence(role: ArenaTileRole): Int =
        if (role == ArenaTileRole.PATH) pathDetailEvery else detailEvery

    fun surfaceMode(): ArenaSurfaceMode {
        val raw = surface.lowercase()
        if (raw.isNotBlank()) return runCatching { ArenaSurfaceMode.valueOf(raw.uppercase()) }.getOrDefault(ArenaSurfaceMode.TERRAIN)
        return when (style) {
            "stone_table" -> ArenaSurfaceMode.CHECKER
            "bamboo_court" -> ArenaSurfaceMode.GRID
            "festival_board" -> ArenaSurfaceMode.TRACK
            "split_battleground" -> ArenaSurfaceMode.TACTICAL
            else -> ArenaSurfaceMode.TERRAIN
        }
    }
}

object MinecraftArenaRegistry {
    private val gson = Gson()
    private val cache = ConcurrentHashMap<String, MinecraftArenaDefinition?>()

    fun definition(arenaId: String): MinecraftArenaDefinition? =
        cache.computeIfAbsent(arenaId, ::load)

    fun clear() { cache.clear(); MinecraftArenaRenderer.clearCompiledScenes() }

    private fun load(arenaId: String): MinecraftArenaDefinition? {
        if (!arenaId.matches(Regex("^[a-z0-9_.-]{1,64}$"))) return null
        val id = ResourceLocation.fromNamespaceAndPath("svhub", "arenas/$arenaId.json")
        val resource = Minecraft.getInstance().resourceManager.getResource(id).orElse(null) ?: return null
        return runCatching {
            resource.open().bufferedReader().use { reader ->
                parse(gson.fromJson(reader, JsonObject::class.java) ?: JsonObject())
            }
        }.getOrNull()
    }

    fun parse(root: JsonObject): MinecraftArenaDefinition {
        val metadata = root.getAsJsonObject("metadata") ?: JsonObject()
        val camera = metadata.getAsJsonObject("camera") ?: JsonObject()
        val bounds = region(metadata, "arenaBounds", region(metadata, "tacticianRegion", ArenaRegion(-1f,-1f,7f,8f)))
        return MinecraftArenaDefinition(
            style = string(root, "style", "terrain").take(32),
            surface = string(root, "surface", "").take(24),
            floor = strings(root, "floor"),
            floorAlt = strings(root, "floorAlt"),
            ally = strings(root, "ally"),
            enemy = strings(root, "enemy"),
            path = strings(root, "path"),
            props = root.getAsJsonArray("props")?.mapNotNull { raw ->
                val obj = runCatching { raw.asJsonObject }.getOrNull() ?: return@mapNotNull null
                val item = runCatching { obj.get("item")?.asString.orEmpty() }.getOrDefault("")
                if (item.isBlank()) return@mapNotNull null
                MinecraftArenaProp(
                    item = item,
                    x = runCatching { obj.get("x")?.asFloat ?: 0f }.getOrDefault(0f),
                    y = runCatching { obj.get("y")?.asFloat ?: 0f }.getOrDefault(0f),
                    scale = runCatching { obj.get("scale")?.asFloat ?: 0.7f }.getOrDefault(0.7f).coerceIn(0.25f, 1.8f)
                )
            }.orEmpty().take(40),
            floorColor = color(root, "floorColor", 0xFF173530.toInt()),
            floorAltColor = color(root, "floorAltColor", 0xFF132C29.toInt()),
            allyColor = color(root, "allyColor", 0xFF173530.toInt()),
            enemyColor = color(root, "enemyColor", 0xFF302126.toInt()),
            pathColor = color(root, "pathColor", 0xFF4A463E.toInt()),
            pathAccentColor = color(root, "pathAccentColor", 0xFF756F62.toInt()),
            gridColor = color(root, "gridColor", 0xFF29403F.toInt()),
            borderColor = color(root, "borderColor", 0xFF2A3433.toInt()),
            edgeColor = color(root, "edgeColor", 0xFF101719.toInt()),
            depthColor = color(root, "depthColor", 0xFF101719.toInt()),
            depth = int(root, "depth", 2).coerceIn(0, 10),
            detailEvery = int(root, "detailEvery", 0).coerceIn(0, 32),
            pathDetailEvery = int(root, "pathDetailEvery", 0).coerceIn(0, 32),
            boardColumns = int(metadata, "boardColumns", 7).coerceIn(2, 16),
            boardRows = int(metadata, "boardRows", 8).coerceIn(2, 16),
            boardOrigin = point(metadata, "boardOrigin", ArenaPoint(0f,0f)),
            boardAnchors = points(metadata, "boardAnchors"),
            benchAnchors = points(metadata, "benchAnchors"),
            itemBenchAnchors = points(metadata, "itemBenchAnchors"),
            tacticianSpawn = point(metadata, "tacticianSpawn", region(metadata, "tacticianRegion", bounds).center),
            tacticianMovementBounds = region(metadata, "tacticianRegion", bounds),
            humanSpawn = point(metadata, "humanSpawn", ArenaPoint(3f,7f)),
            opponentSpawn = point(metadata, "opponentSpawn", ArenaPoint(3f,0f)),
            cameras = ArenaCameraSet(point(camera,"spectator",ArenaPoint(3f,9f,12f)), point(camera,"scouting",ArenaPoint(3f,8f,10f)), point(camera,"carousel",ArenaPoint(3f,10f,13f))),
            carouselCenter = point(metadata, "carouselCenter", ArenaPoint(3f,3.5f)),
            arenaBounds = bounds,
            lighting = string(metadata,"lighting","default"), ambientVfx = string(metadata,"ambientVfx",""), music = string(metadata,"music",""),
            lootAnchors = points(metadata,"lootAnchors"), combatStartVfx = string(metadata,"combatStartVfx",""),
            victoryVfx = string(metadata,"victoryVfx",""), defeatVfx = string(metadata,"defeatVfx",""),
            interactionRegions = metadata.getAsJsonArray("interactionRegions")?.mapNotNull { raw -> runCatching {
                val obj=raw.asJsonObject; ArenaInteractionRegion(string(obj,"id",""),region(obj,"bounds",bounds),string(obj,"action",""))
            }.getOrNull()?.takeIf { it.id.isNotBlank() && it.action.isNotBlank() } }.orEmpty()
        )
    }

    private fun point(root: JsonObject, key: String, fallback: ArenaPoint): ArenaPoint {
        val a=root.getAsJsonArray(key) ?: return fallback
        return ArenaPoint(runCatching { a[0].asFloat }.getOrDefault(fallback.x),runCatching { a[1].asFloat }.getOrDefault(fallback.y),runCatching { a[2].asFloat }.getOrDefault(fallback.z))
    }
    private fun points(root:JsonObject,key:String)=root.getAsJsonArray(key)?.mapNotNull { raw ->
        runCatching { val a=raw.asJsonArray;ArenaPoint(a[0].asFloat,a[1].asFloat,runCatching { a[2].asFloat }.getOrDefault(0f)) }.getOrNull()
    }.orEmpty()
    private fun region(root:JsonObject,key:String,fallback:ArenaRegion):ArenaRegion {
        val a=root.getAsJsonArray(key)?:return fallback
        return runCatching { ArenaRegion(a[0].asFloat,a[1].asFloat,a[2].asFloat,a[3].asFloat) }.getOrDefault(fallback)
    }

    private fun strings(root: JsonObject, key: String): List<String> =
        root.getAsJsonArray(key)?.mapNotNull { value ->
            runCatching { value.asString.trim() }.getOrNull()
                ?.takeIf { it.matches(Regex("^[a-z0-9_.-]+:[a-z0-9_./-]+$")) }
        }.orEmpty().take(16)

    private fun string(root: JsonObject, key: String, fallback: String): String =
        runCatching { root.get(key)?.asString ?: fallback }.getOrDefault(fallback)

    private fun int(root: JsonObject, key: String, fallback: Int): Int =
        runCatching { root.get(key)?.asInt ?: fallback }.getOrDefault(fallback)

    private fun color(root: JsonObject, key: String, fallback: Int): Int {
        val raw = runCatching { root.get(key)?.asString.orEmpty() }.getOrDefault("").removePrefix("#")
        val rgb = raw.toLongOrNull(16) ?: return fallback
        return when (raw.length) {
            6 -> (0xFF000000L or rgb).toInt()
            8 -> rgb.toInt()
            else -> fallback
        }
    }
}

object MinecraftArenaRenderer {
    internal data class CompiledProp(val stack:ItemStack,val x:Int,val y:Int,val pixels:Int,val depth:Double)
    internal data class CompiledArenaScene(val props:List<CompiledProp>)
    private data class SceneKey(val definition:Int,val columns:Int,val rows:Int,val originX:Int,val originY:Int,val tileWidth:Int,val tileHeight:Int)
    private val compiledScenes=ConcurrentHashMap<SceneKey,CompiledArenaScene>()
    private val itemStacks=ConcurrentHashMap<String,ItemStack?>()

    fun clearCompiledScenes(){compiledScenes.clear();itemStacks.clear()}
    internal fun compiledScene(layout:PokemonSceneLayout,theme:MinecraftArenaDefinition):CompiledArenaScene{
        val key=SceneKey(System.identityHashCode(theme),layout.columns,layout.rows,layout.originX.roundToInt(),layout.originY.roundToInt(),layout.tileWidth,layout.tileHeight)
        return compiledScenes.computeIfAbsent(key){
            val props=theme.props.mapIndexedNotNull{index,prop->
                val stack=resolveStack(prop.item)?:return@mapIndexedNotNull null
                val point=layout.project(prop.x,prop.y)
                val size=(min(28,max(12,layout.tileWidth))*prop.scale).roundToInt().coerceIn(8,38)
                CompiledProp(stack,point.x.roundToInt(),point.y.roundToInt()-size/3,size,30.0+point.y/8.0+index*.01)
            }.sortedBy(CompiledProp::y)
            CompiledArenaScene(props)
        }
    }
    fun renderPresentation(gui:GuiGraphics,layout:PokemonSceneLayout,frame:ArenaPresentationFrame,phase:String){
        val lightingAlpha=when(frame.lighting.lowercase()){"dark","night"->56;"bright","day"->10;"dramatic"->34;else->18}
        if(lightingAlpha>0){
            val a=layout.project(-.7f,-.7f);val b=layout.project(layout.columns-.3f,layout.rows-.3f)
            val left=minOf(a.x,b.x).roundToInt();val right=maxOf(a.x,b.x).roundToInt()
            val top=minOf(a.y,b.y).roundToInt();val bottom=maxOf(a.y,b.y).roundToInt()
            if(right>left&&bottom>top)gui.fill(left,top,right,bottom,(lightingAlpha shl 24))
        }
        if(frame.ambientVfx.isNotBlank()){
            val hash=frame.ambientVfx.hashCode()
            repeat(6){i->
                val x=Math.floorMod(hash+i*31,layout.columns.coerceAtLeast(1)).toFloat()
                val y=Math.floorMod(hash/31+i*17,layout.rows.coerceAtLeast(1)).toFloat()
                val p=layout.project(x,y);gui.fill(p.x.roundToInt()-1,p.y.roundToInt()-1,p.x.roundToInt()+2,p.y.roundToInt()+2,0x88FFFFFF.toInt())
            }
        }
        if(frame.semanticVfx.isNotBlank()){
            val center=layout.project((layout.columns-1)/2f,(layout.rows-1)/2f)
            val pulse=if(phase.equals("combat",true))10 else 14
            drawDiamondOutline(gui,center.x.roundToInt(),center.y.roundToInt(),pulse*2,pulse,0xCCFFFFFF.toInt())
        }
        frame.lootAnchors.forEachIndexed{index,anchor->
            val p=layout.project(anchor.x,anchor.y)
            val size=if(index%2==0)5 else 4
            fillDiamond(gui,p.x.roundToInt(),p.y.roundToInt(),size*2,size,0xB8E2BE62.toInt())
        }
    }

    fun interactionRect(layout:PokemonSceneLayout,region:ArenaInteractionRegion):io.github.aristheg201.svhub.ui.UiRect{
        val points=listOf(
            layout.project(region.bounds.minX,region.bounds.minY),layout.project(region.bounds.maxX,region.bounds.minY),
            layout.project(region.bounds.minX,region.bounds.maxY),layout.project(region.bounds.maxX,region.bounds.maxY)
        )
        val left=points.minOf{it.x}.roundToInt();val right=points.maxOf{it.x}.roundToInt()
        val top=points.minOf{it.y}.roundToInt();val bottom=points.maxOf{it.y}.roundToInt()
        return io.github.aristheg201.svhub.ui.UiRect(left,top,(right-left).coerceAtLeast(1),(bottom-top).coerceAtLeast(1))
    }

    /**
     * Draws one continuous Minecraft-like platform before the gameplay overlay.
     * This deliberately replaces the old "one floating block per cell" look.
     */
    fun renderFoundation(
        gui: GuiGraphics,
        layout: PokemonSceneLayout,
        theme: MinecraftArenaDefinition,
        teamSplitRow: Int?
    ) {
        val outer = quad(layout, -0.88f, -0.88f, layout.columns - 0.12f, layout.rows - 0.12f)
        val inner = quad(layout, -0.58f, -0.58f, layout.columns - 0.42f, layout.rows - 0.42f)
        val depth = max(2, theme.depth)

        for (offset in depth downTo 1) fillQuad(gui, outer.map { ScenePoint(it.x, it.y + offset) }, darken(theme.edgeColor, 0.72f + offset * 0.018f))
        fillQuad(gui, outer, theme.borderColor)
        fillQuad(gui, inner, theme.floorColor)

        if (theme.surfaceMode() == ArenaSurfaceMode.TACTICAL && teamSplitRow != null) {
            val split = teamSplitRow.toFloat() - 0.5f
            fillQuad(gui, quad(layout, -0.5f, -0.5f, layout.columns - 0.5f, split), withAlpha(theme.enemyColor, 210))
            fillQuad(gui, quad(layout, -0.5f, split, layout.columns - 0.5f, layout.rows - 0.5f), withAlpha(theme.allyColor, 210))
            val a = layout.project(-0.55f, split)
            val b = layout.project(layout.columns - 0.45f, split)
            drawLine(gui, a, b, withAlpha(theme.pathAccentColor, 180), 2)
        }

        when (theme.surfaceMode()) {
            ArenaSurfaceMode.GRID -> renderIntersectionGrid(gui, layout, theme)
            ArenaSurfaceMode.TACTICAL -> renderDiamondGrid(gui, layout, theme)
            else -> Unit
        }
    }

    fun renderCheckerCell(
        gui: GuiGraphics,
        layout: PokemonSceneLayout,
        theme: MinecraftArenaDefinition,
        index: Int,
        alternate: Boolean
    ) {
        val p = layout.center(index)
        val fill = if (alternate) theme.floorAltColor else theme.floorColor
        fillDiamond(gui, p.x.roundToInt(), p.y.roundToInt(), layout.tileWidth, layout.tileHeight, fill)
        drawDiamondOutline(gui, p.x.roundToInt(), p.y.roundToInt(), layout.tileWidth, layout.tileHeight, withAlpha(theme.gridColor, 190))
    }

    fun renderTrackCell(
        gui: GuiGraphics,
        layout: PokemonSceneLayout,
        theme: MinecraftArenaDefinition,
        index: Int,
        alternate: Boolean
    ) {
        val p = layout.center(index)
        val width = max(8, (layout.tileWidth * 0.78f).roundToInt())
        val height = max(5, (layout.tileHeight * 0.76f).roundToInt())
        val fill = if (alternate) theme.floorAltColor else theme.pathAccentColor
        fillDiamond(gui, p.x.roundToInt(), p.y.roundToInt(), width, height, fill)
        drawDiamondOutline(gui, p.x.roundToInt(), p.y.roundToInt(), width, height, withAlpha(theme.gridColor, 210))
    }

    fun renderPathRoute(
        gui: GuiGraphics,
        layout: PokemonSceneLayout,
        theme: MinecraftArenaDefinition,
        route: List<Int>
    ) {
        if (route.isEmpty()) return
        val points = route.filter { it in 0 until layout.columns * layout.rows }.map(layout::center)
        if (points.isEmpty()) return
        val outer = max(5, (layout.tileHeight * 0.72f).roundToInt())
        val inner = max(3, outer - 3)
        points.zipWithNext().forEach { (a, b) ->
            drawLine(gui, a, b, theme.pathColor, outer)
            drawLine(gui, a, b, theme.pathAccentColor, inner)
        }
        points.forEach { p ->
            fillDiamond(gui, p.x.roundToInt(), p.y.roundToInt(), max(8, layout.tileWidth / 2), max(5, layout.tileHeight / 2), theme.pathAccentColor)
        }
    }

    fun renderCellHighlight(
        gui: GuiGraphics,
        layout: PokemonSceneLayout,
        index: Int,
        color: Int,
        strong: Boolean
    ) {
        val p = layout.center(index)
        val w = if (strong) max(10, (layout.tileWidth * 0.88f).roundToInt()) else max(9, (layout.tileWidth * 0.72f).roundToInt())
        val h = if (strong) max(6, (layout.tileHeight * 0.90f).roundToInt()) else max(5, (layout.tileHeight * 0.70f).roundToInt())
        fillDiamond(gui, p.x.roundToInt(), p.y.roundToInt(), w, h, withAlpha(color, if (strong) 185 else 120))
        drawDiamondOutline(gui, p.x.roundToInt(), p.y.roundToInt(), w, h, color)
    }

    /**
     * Sparse environmental detail only. Never use this as the board surface.
     */
    fun renderTile(
        gui: GuiGraphics,
        layout: PokemonSceneLayout,
        theme: MinecraftArenaDefinition,
        index: Int,
        role: ArenaTileRole,
        alternate: Boolean,
        seed: String
    ) {
        val cadence = theme.detailCadence(role)
        if (cadence <= 0 || role == ArenaTileRole.PATH) return
        val hash = seed.hashCode() * 31 + index * 131 + role.ordinal * 17
        if (Math.floorMod(hash, cadence) != 0) return
        val palette = theme.palette(role, alternate)
        if (palette.isEmpty()) return
        val item = palette[Math.floorMod(hash ushr 3, palette.size)]
        val point = layout.center(index)
        val size = min(11, max(6, min(layout.tileWidth, layout.tileHeight * 2) / 2))
        renderItem(gui, item, point.x.roundToInt(), point.y.roundToInt() - max(1, layout.tileHeight / 8), size, 10.0)
    }

    fun renderProps(
        gui: GuiGraphics,
        layout: PokemonSceneLayout,
        theme: MinecraftArenaDefinition,
        seed: String
    ) {
        compiledScene(layout,theme).props.forEach{prop->renderStack(gui,prop.stack,prop.x,prop.y,prop.pixels,prop.depth)}
    }

    private fun renderIntersectionGrid(gui: GuiGraphics, layout: PokemonSceneLayout, theme: MinecraftArenaDefinition) {
        val color = withAlpha(theme.gridColor, 210)
        for (x in 0 until layout.columns) {
            drawLine(gui, layout.project(x.toFloat(), 0f), layout.project(x.toFloat(), (layout.rows - 1).toFloat()), color, 1)
        }
        for (y in 0 until layout.rows) {
            drawLine(gui, layout.project(0f, y.toFloat()), layout.project((layout.columns - 1).toFloat(), y.toFloat()), color, 1)
        }
    }

    private fun renderDiamondGrid(gui: GuiGraphics, layout: PokemonSceneLayout, theme: MinecraftArenaDefinition) {
        val color = withAlpha(theme.gridColor, 92)
        repeat(layout.columns * layout.rows) { index ->
            val p = layout.center(index)
            drawDiamondOutline(gui, p.x.roundToInt(), p.y.roundToInt(), layout.tileWidth, layout.tileHeight, color)
        }
    }

    private fun quad(layout: PokemonSceneLayout, x0: Float, y0: Float, x1: Float, y1: Float): List<ScenePoint> =
        listOf(layout.project(x0, y0), layout.project(x1, y0), layout.project(x1, y1), layout.project(x0, y1))

    private fun fillQuad(gui: GuiGraphics, points: List<ScenePoint>, color: Int) {
        if (points.size != 4) return
        val minY = points.minOf { it.y }.roundToInt()
        val maxY = points.maxOf { it.y }.roundToInt()
        for (y in minY..maxY) {
            val intersections = mutableListOf<Float>()
            for (i in points.indices) {
                val a = points[i]
                val b = points[(i + 1) % points.size]
                if (a.y == b.y) continue
                val low = min(a.y, b.y)
                val high = max(a.y, b.y)
                if (y.toFloat() < low || y.toFloat() >= high) continue
                val t = (y - a.y) / (b.y - a.y)
                intersections += a.x + (b.x - a.x) * t
            }
            intersections.sort()
            var i = 0
            while (i + 1 < intersections.size) {
                gui.fill(intersections[i].roundToInt(), y, intersections[i + 1].roundToInt() + 1, y + 1, color)
                i += 2
            }
        }
    }

    private fun drawLine(gui: GuiGraphics, a: ScenePoint, b: ScenePoint, color: Int, thickness: Int) {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val steps = max(1, max(abs(dx), abs(dy)).roundToInt())
        val r = max(0, thickness / 2)
        for (i in 0..steps) {
            val t = i.toFloat() / steps
            val x = (a.x + dx * t).roundToInt()
            val y = (a.y + dy * t).roundToInt()
            gui.fill(x - r, y - r, x + r + 1, y + r + 1, color)
        }
    }

    private fun fillDiamond(gui: GuiGraphics, cx: Int, cy: Int, width: Int, height: Int, color: Int) {
        val halfW = max(2, width / 2)
        val halfH = max(2, height / 2)
        for (dy in -halfH..halfH) {
            val ratio = 1f - abs(dy).toFloat() / halfH.toFloat()
            val hw = max(1, (halfW * ratio).roundToInt())
            gui.fill(cx - hw, cy + dy, cx + hw + 1, cy + dy + 1, color)
        }
    }

    private fun drawDiamondOutline(gui: GuiGraphics, cx: Int, cy: Int, width: Int, height: Int, color: Int) {
        val left = ScenePoint((cx - width / 2).toFloat(), cy.toFloat())
        val top = ScenePoint(cx.toFloat(), (cy - height / 2).toFloat())
        val right = ScenePoint((cx + width / 2).toFloat(), cy.toFloat())
        val bottom = ScenePoint(cx.toFloat(), (cy + height / 2).toFloat())
        drawLine(gui, left, top, color, 1)
        drawLine(gui, top, right, color, 1)
        drawLine(gui, right, bottom, color, 1)
        drawLine(gui, bottom, left, color, 1)
    }

    private fun renderItem(gui: GuiGraphics, itemId: String, centerX: Int, centerY: Int, pixels: Int, depth: Double) {
        val stack=resolveStack(itemId)?:return
        renderStack(gui,stack,centerX,centerY,pixels,depth)
    }

    private fun resolveStack(itemId:String):ItemStack?=itemStacks.computeIfAbsent(itemId){key->
        val id=ResourceLocation.tryParse(key)?:return@computeIfAbsent null
        val item=BuiltInRegistries.ITEM.getOptional(id).orElse(null)?:return@computeIfAbsent null
        if(item===Items.AIR)return@computeIfAbsent null
        ItemStack(item).takeUnless(ItemStack::isEmpty)
    }

    private fun renderStack(gui:GuiGraphics,stack:ItemStack,centerX:Int,centerY:Int,pixels:Int,depth:Double){
        val scale = (pixels / 16f).coerceIn(0.35f, 2.2f)
        val pose = gui.pose()
        pose.pushPose()
        pose.translate(centerX - 8.0 * scale, centerY - 8.0 * scale, depth)
        pose.scale(scale, scale, 1f)
        gui.renderItem(stack, 0, 0)
        pose.popPose()
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        (alpha.coerceIn(0, 255) shl 24) or (color and 0x00FFFFFF)

    private fun darken(color: Int, factor: Float): Int {
        val f = factor.coerceIn(0f, 1f)
        val a = color ushr 24 and 0xFF
        val r = ((color ushr 16 and 0xFF) * f).roundToInt()
        val g = ((color ushr 8 and 0xFF) * f).roundToInt()
        val b = ((color and 0xFF) * f).roundToInt()
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }
}
