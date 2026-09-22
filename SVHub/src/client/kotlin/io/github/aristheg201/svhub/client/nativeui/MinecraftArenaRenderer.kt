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
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import io.github.aristheg201.svhub.ui.SceneCameraPreset
import io.github.aristheg201.svhub.ui.SceneVec3
import io.github.aristheg201.svhub.ui.SceneTransform
import io.github.aristheg201.svhub.ui.SceneNode
import io.github.aristheg201.svhub.ui.SceneMeshNode
import io.github.aristheg201.svhub.ui.SceneBlockModelNode
import io.github.aristheg201.svhub.ui.SceneItemModelNode
import io.github.aristheg201.svhub.ui.SceneInteractionSurface
import io.github.aristheg201.svhub.ui.SVHubScene
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
    val scale: Float = 0.7f,
    val z:Float=0f,
    val yaw:Float=0f,
    val pitch:Float=0f,
    val roll:Float=0f
)

data class ArenaPoint(val x: Float, val y: Float, val z: Float = 0f) {
    init { require(x.isFinite() && y.isFinite() && z.isFinite()) { "Arena coordinates must be finite" } }
}
data class ArenaRegion(val minX: Float, val minY: Float, val maxX: Float, val maxY: Float) {
    init { require(listOf(minX,minY,maxX,maxY).all(Float::isFinite) && minX<maxX && minY<maxY) { "Invalid arena region" } }
    fun clamp(point: ArenaPoint) = ArenaPoint(point.x.coerceIn(minX, maxX), point.y.coerceIn(minY, maxY), point.z)
    val center get() = ArenaPoint((minX + maxX) / 2f, (minY + maxY) / 2f)
}
data class ArenaCameraSet(val spectator: ArenaPoint, val scouting: ArenaPoint, val carousel: ArenaPoint)
data class ArenaInteractionRegion(val id: String, val bounds: ArenaRegion, val action: String)
enum class ArenaCameraRole { NORMAL, PREPARATION, COMBAT, SPECTATOR, SCOUTING, CAROUSEL, PVE_INTRO, BOSS_INTRO, VICTORY, DEFEAT, ARENA_PREVIEW }

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
    val id: String = "inline",
    val definitionRevision: String = "0",
    val geometry: List<SceneMeshNode> = emptyList(),
    val cameraPresets: Map<ArenaCameraRole, SceneCameraPreset> = emptyMap(),
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
    val backgroundColor: Int = 0xFF162637.toInt(),
    val depth: Int = 2,
    val detailEvery: Int = 0,
    val pathDetailEvery: Int = 0
    ,val boardColumns: Int = 7
    ,val boardRows: Int = 8
    ,val boardOrigin: ArenaPoint = ArenaPoint(0f, 0f)
    ,val cellSize: ArenaPoint = ArenaPoint(1f,1f,1f)
    ,val battlefieldBounds: ArenaRegion? = null
    ,val environmentBounds: ArenaRegion? = null
    ,val texturedBattlefield: Boolean = false
    ,val benchMaterial: String = "minecraft:stone_bricks"
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
    val logicalBoardBounds get()=ArenaRegion(boardOrigin.x-cellSize.x*.5f,boardOrigin.y-cellSize.y*.5f,
        boardOrigin.x+(boardColumns-.5f)*cellSize.x,boardOrigin.y+(boardRows-.5f)*cellSize.y)
    init {
        if(texturedBattlefield) {
            val field=requireNotNull(battlefieldBounds) { "Textured arenas require battlefieldBounds" }
            val environment=requireNotNull(environmentBounds) { "Textured arenas require environmentBounds" }
            val board=logicalBoardBounds
            require(field.minX<=board.minX-.5f && field.minY<=board.minY-.5f && field.maxX>=board.maxX+.5f && field.maxY>=board.maxY+.5f) { "Battlefield must leave safety margin around logical cells" }
            require(environment.minX<=field.minX && environment.minY<=field.minY && environment.maxX>=field.maxX && environment.maxY>=field.maxY)
            geometry.forEach { node ->
                val bounds=io.github.aristheg201.svhub.ui.SceneBounds.enclosing(node.corners())
                if(bounds.max.z>boardOrigin.z+.15) require(bounds.max.x<field.minX || bounds.min.x>field.maxX || bounds.max.y<field.minY || bounds.min.y>field.maxY) {
                    "Scenery ${node.id} encroaches on battlefield safety margin"
                }
            }
        }
    }
    /**
     * Legacy non-TFT arenas predate authoritative board metadata. Their bundled
     * definitions used the generic 7x8 defaults, while the live games expose
     * different logical sizes (Chess 8x8, Xiangqi 9x10, TD data-defined).
     *
     * If an arena does not author explicit per-cell anchors, the game view owns
     * the logical board dimensions. This keeps mesh generation, projection and
     * picking on the exact same grid. Authored-path arenas such as Ludo retain
     * their own anchor topology.
     */
    fun forBoardDimensions(columns:Int,rows:Int):MinecraftArenaDefinition {
        val cols=columns.coerceIn(2,64)
        val rowsSafe=rows.coerceIn(2,64)
        if(boardAnchors.isNotEmpty() || (boardColumns==cols && boardRows==rowsSafe)) return this
        return copy(boardColumns=cols,boardRows=rowsSafe)
    }

    fun boardAnchor(index:Int):ArenaPoint = boardAnchors.getOrNull(index) ?: ArenaPoint(boardOrigin.x+(index%boardColumns)*cellSize.x,boardOrigin.y+(index/boardColumns)*cellSize.y,boardOrigin.z)
    fun benchAnchor(index:Int):ArenaPoint = benchAnchors.getOrNull(index) ?: ArenaPoint(boardOrigin.x+index*.75f,boardOrigin.y+boardRows+.8f,boardOrigin.z)
    fun itemAnchor(index:Int):ArenaPoint = itemBenchAnchors.getOrNull(index) ?: ArenaPoint(boardOrigin.x+index*.6f,boardOrigin.y+boardRows+1.6f,boardOrigin.z)
    fun camera(role:ArenaCameraRole,fallback:SceneCameraPreset):SceneCameraPreset {
        cameraPresets[role]?.let { return it }
        val point=when(role){ArenaCameraRole.SCOUTING->cameras.scouting;ArenaCameraRole.CAROUSEL->cameras.carousel;else->cameras.spectator}
        val span=max(1f,arenaBounds.maxY-arenaBounds.minY);val height=(point.z/span).coerceIn(.5f,1.25f)
        return fallback.copy(id="${fallback.id}:${role.name.lowercase()}",tileScale=height,verticalScale=(point.y/span).coerceIn(.5f,1.25f),originBiasY=(point.y/(span+point.y.coerceAtLeast(0f))).coerceIn(0f,1f),pitch=(18f+point.z*2f).coerceIn(0f,75f),perspective=true,position=SceneVec3(point.x.toDouble(),point.y.toDouble(),point.z.coerceAtLeast(2f).toDouble()),target=SceneVec3(arenaBounds.center.x.toDouble(),arenaBounds.center.y.toDouble(),boardOrigin.z.toDouble()))
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
    @Volatile private var cache: Map<String, MinecraftArenaDefinition> = emptyMap()
    private val logger=org.slf4j.LoggerFactory.getLogger("SVHub/Arenas")

    fun definition(arenaId: String): MinecraftArenaDefinition? = cache[arenaId]

    fun clear() { cache=emptyMap(); MinecraftArenaRenderer.clearCompiledScenes() }

    /** Publish a whole validated generation; malformed resource packs retain the last valid one. */
    fun reload(resources: ResourceManager): Boolean {
        val next=linkedMapOf<String,MinecraftArenaDefinition>()
        val failures=mutableListOf<String>()
        resources.listResources("arenas") { it.namespace=="svhub" && it.path.endsWith(".json") }.forEach { (location,resource) ->
            val id=location.path.removePrefix("arenas/").removeSuffix(".json")
            try {
                require(id.matches(Regex("^[a-z0-9_.-]{1,64}$")))
                next[id]=resource.open().bufferedReader().use { parse(gson.fromJson(it,JsonObject::class.java)).copy(id=id) }
            } catch(failure:Exception) {
                failures+="$location: ${failure.message}"
            }
        }
        if(failures.isNotEmpty()) {
            logger.error("Arena reload rejected; keeping {} valid definitions: {}",cache.size,failures.joinToString("; "))
            // Definitions survive, but atlas UVs and baked provider models belong to
            // the new resource generation and must still be rebuilt.
            MinecraftArenaRenderer.clearCompiledScenes()
            return false
        }
        cache=next.toMap()
        MinecraftArenaRenderer.clearCompiledScenes()
        return true
    }

    fun parse(root: JsonObject): MinecraftArenaDefinition {
        val metadata = root.getAsJsonObject("metadata") ?: JsonObject()
        val camera = metadata.getAsJsonObject("camera") ?: JsonObject()
        val bounds = region(metadata, "arenaBounds", region(metadata, "tacticianRegion", ArenaRegion(-1f,-1f,7f,8f)))
        return MinecraftArenaDefinition(
            id = string(root,"id",string(root,"style","inline")),
            definitionRevision = java.security.MessageDigest.getInstance("SHA-256").digest(root.toString().toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) },
            geometry = root.getAsJsonArray("geometry")?.map { raw ->
                val obj=raw.asJsonObject
                fun vector(key:String,default:ArenaPoint)=point(obj,key,default).let { SceneVec3(it.x.toDouble(),it.y.toDouble(),it.z.toDouble()) }
                SceneMeshNode("structure:${string(obj,"id","")}",SceneTransform(vector("position",ArenaPoint(0f,0f)),vector("rotation",ArenaPoint(0f,0f)),vector("scale",ArenaPoint(1f,1f,1f))),vector("size",ArenaPoint(1f,1f,1f)),string(obj,"material","#52616b"))
            }.orEmpty().also { require(it.size <= 512 && it.map(SceneMeshNode::id).distinct().size == it.size) },
            cameraPresets = metadata.getAsJsonObject("cameraPresets")?.entrySet()?.associate { (name,raw) ->
                val obj=raw.asJsonObject
                val p=point(obj,"position",ArenaPoint(3f,15f,12f));val t=point(obj,"target",ArenaPoint(3f,3.5f))
                ArenaCameraRole.valueOf(name.uppercase()) to SceneCameraPreset(id="${string(root,"style","arena")}:$name",perspective=true,position=SceneVec3(p.x.toDouble(),p.y.toDouble(),p.z.toDouble()),target=SceneVec3(t.x.toDouble(),t.y.toDouble(),t.z.toDouble()),fov=obj.get("fov")?.asDouble?:48.0,near=obj.get("near")?.asDouble?:.1,far=obj.get("far")?.asDouble?:100.0,transitionMs=obj.get("transitionMs")?.asLong?:450L)
            }.orEmpty(),
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
                    ,z=runCatching{obj.get("z")?.asFloat?:0f}.getOrDefault(0f)
                    ,yaw=runCatching{obj.get("yaw")?.asFloat?:0f}.getOrDefault(0f)
                    ,pitch=runCatching{obj.get("pitch")?.asFloat?:0f}.getOrDefault(0f)
                    ,roll=runCatching{obj.get("roll")?.asFloat?:0f}.getOrDefault(0f)
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
            backgroundColor = color(root,"backgroundColor",0xFF162637.toInt()),
            depth = int(root, "depth", 2).coerceIn(0, 10),
            detailEvery = int(root, "detailEvery", 0).coerceIn(0, 32),
            pathDetailEvery = int(root, "pathDetailEvery", 0).coerceIn(0, 32),
            boardColumns = int(metadata, "boardColumns", 7).coerceIn(2, 64),
            boardRows = int(metadata, "boardRows", 8).coerceIn(2, 64),
            cellSize = point(metadata,"cellSize",ArenaPoint(1f,1f,1f)).also { require(it.x>0 && it.y>0 && it.z>0) },
            battlefieldBounds = metadata.get("battlefieldBounds")?.let { region(metadata,"battlefieldBounds",bounds) },
            environmentBounds = metadata.get("environmentBounds")?.let { region(metadata,"environmentBounds",bounds) },
            texturedBattlefield = root.get("texturedBattlefield")?.asBoolean ?: false,
            benchMaterial = string(root,"benchMaterial","minecraft:stone_bricks"),
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
        require(a.size() in 2..3) { "$key must contain two or three coordinates" }
        return ArenaPoint(a[0].asFloat,a[1].asFloat,if(a.size()==3)a[2].asFloat else fallback.z)
    }
    private fun points(root:JsonObject,key:String)=root.getAsJsonArray(key)?.map { raw ->
        val a=raw.asJsonArray
        require(a.size() in 2..3) { "$key must contain coordinate vectors" }
        ArenaPoint(a[0].asFloat,a[1].asFloat,if(a.size()==3)a[2].asFloat else 0f)
    }.orEmpty()
    private fun region(root:JsonObject,key:String,fallback:ArenaRegion):ArenaRegion {
        val a=root.getAsJsonArray(key)?:return fallback
        require(a.size()==4) { "$key must contain four bounds" }
        return ArenaRegion(a[0].asFloat,a[1].asFloat,a[2].asFloat,a[3].asFloat)
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
    data class CompiledArenaScene(val scene:SVHubScene)
    private data class SceneKey(val definition:MinecraftArenaDefinition,val resourceRevision:Long)
    private val compiledScenes=ConcurrentHashMap<SceneKey,CompiledArenaScene>()
    private val itemStacks=ConcurrentHashMap<String,ItemStack?>()
    private var resourceRevision=0L

    fun clearCompiledScenes(){compiledScenes.clear();itemStacks.clear();resourceRevision++;EmbeddedSceneRenderer.invalidate()}
    fun compiledScene(layout:PokemonSceneLayout,theme:MinecraftArenaDefinition):CompiledArenaScene{
        val key=SceneKey(theme,resourceRevision)
        return compiledScenes.computeIfAbsent(key){
            val thickness=theme.depth.coerceAtLeast(1)*.12
            val logical=theme.logicalBoardBounds
            // Textured battlefields are backed by real block-model terrain. Keeping the
            // legacy colored floor coplanar with it causes z-fighting and visually
            // reintroduces the old flat-board layer.
            val floor=if(theme.texturedBattlefield) null else SceneMeshNode(
                "floor",
                SceneTransform(SceneVec3(logical.center.x.toDouble(),logical.center.y.toDouble(),theme.boardOrigin.z-thickness/2)),
                SceneVec3((logical.maxX-logical.minX).toDouble(),(logical.maxY-logical.minY).toDouble(),thickness),
                theme.surface.ifBlank{"arena_floor"}
            )
            val props=theme.props.mapIndexed{index,prop->
                val transform=SceneTransform(SceneVec3(prop.x.toDouble(),prop.y.toDouble(),prop.z.toDouble()),SceneVec3(prop.pitch.toDouble(),prop.roll.toDouble(),prop.yaw.toDouble()),SceneVec3(prop.scale.toDouble(),prop.scale.toDouble(),prop.scale.toDouble()))
                val block=ResourceLocation.tryParse(prop.item)?.let(BuiltInRegistries.BLOCK::containsKey)==true
                if(block)SceneBlockModelNode("prop:$index",transform,prop.item) else SceneItemModelNode("prop:$index",transform,prop.item)
            }
            val benches=theme.benchAnchors.mapIndexed{index,p->
                if(theme.texturedBattlefield) SceneBlockModelNode("bench:$index",SceneTransform(SceneVec3(p.x.toDouble(),p.y.toDouble(),p.z-.18),scale=SceneVec3(.82,.76,.18)),theme.benchMaterial)
                else SceneMeshNode("bench:$index",SceneTransform(SceneVec3(p.x.toDouble(),p.y.toDouble(),p.z-.075)),SceneVec3(.7,.7,.15),"bench")
            }
            val interaction=SceneInteractionSurface("board",SceneVec3(logical.minX.toDouble(),logical.minY.toDouble(),theme.boardOrigin.z.toDouble()),(logical.maxX-logical.minX).toDouble(),(logical.maxY-logical.minY).toDouble(),theme.boardColumns,theme.boardRows)
            // Gameplay tiles are authored from the exact logical cell geometry.
            // Baked Minecraft blocks remain scenery and never define cell boundaries.
            val tiles=(0 until theme.boardColumns*theme.boardRows).map { index ->
                val p=theme.boardAnchor(index)
                val row=index/theme.boardColumns
                val alternate=((index%theme.boardColumns+row) and 1)==1
                val role=when {
                    theme.surfaceMode()==ArenaSurfaceMode.TACTICAL && row<theme.boardRows/2 -> ArenaTileRole.ENEMY
                    theme.surfaceMode()==ArenaSurfaceMode.TACTICAL -> ArenaTileRole.ALLY
                    else -> ArenaTileRole.FLOOR
                }
                val inset=if(theme.surfaceMode()==ArenaSurfaceMode.CHECKER).992f else .965f
                val color=theme.color(role,alternate)
                SceneMeshNode("cell:$index",
                    SceneTransform(SceneVec3(p.x.toDouble(),p.y.toDouble(),p.z+.006)),
                    SceneVec3((theme.cellSize.x*inset).coerceAtLeast(.05f).toDouble(),
                        (theme.cellSize.y*inset).coerceAtLeast(.05f).toDouble(),.012),
                    "#%06x".format(color and 0x00FFFFFF))
            }
            val terrain=if(theme.texturedBattlefield) {
                val bounds=theme.battlefieldBounds ?: logical
                val palette=theme.floor.ifEmpty { listOf("minecraft:stone_bricks") }
                val alternate=theme.floorAlt.ifEmpty { palette }
                val width=bounds.maxX-bounds.minX;val height=bounds.maxY-bounds.minY
                val columns=kotlin.math.ceil(width.toDouble()).toInt();val rows=kotlin.math.ceil(height.toDouble()).toInt()
                (0 until columns*rows).map { index ->
                    val x=index%columns;val y=index/columns
                    val dx=minOf(1f,width-x);val dy=minOf(1f,height-y)
                    val material=if(Math.floorMod(x*31+y*17,11)<2) alternate[(x+y)%alternate.size] else palette[(x*7+y*3)%palette.size]
                    SceneBlockModelNode("terrain:$index",SceneTransform(SceneVec3(bounds.minX+x+dx*.5,bounds.minY+y+dy*.5,theme.boardOrigin.z-1.0),scale=SceneVec3(dx.toDouble(),dy.toDouble(),1.0)),material)
                }
            } else emptyList()
            val structures=theme.geometry.flatMap { node ->
                if(!node.material.contains(':')) listOf(node) else {
                    val nx=kotlin.math.ceil(node.size.x).toInt();val ny=kotlin.math.ceil(node.size.y).toInt();val nz=kotlin.math.ceil(node.size.z).toInt()
                    require(nx.toLong()*ny*nz<=8192) { "Structure ${node.id} has too many blocks" }
                    (0 until nx*ny*nz).map { index ->
                        val x=index%nx;val y=index/nx%ny;val z=index/(nx*ny)
                        val dx=minOf(1.0,node.size.x-x);val dy=minOf(1.0,node.size.y-y);val dz=minOf(1.0,node.size.z-z)
                        val point=node.transform.apply(SceneVec3(-node.size.x*.5+x+dx*.5,-node.size.y*.5+y+dy*.5,-node.size.z*.5+z))
                        SceneBlockModelNode("${node.id}:$index",SceneTransform(point,node.transform.rotationDegrees,SceneVec3(dx*node.transform.scale.x,dy*node.transform.scale.y,dz*node.transform.scale.z)),node.material)
                    }
                }
            }
            CompiledArenaScene(SVHubScene("arena:${theme.id}:${theme.definitionRevision}",resourceRevision,listOfNotNull(floor)+terrain+structures+tiles+benches+props,listOf(interaction)))
        }
    }
    fun renderPresentation(gui:GuiGraphics,layout:PokemonSceneLayout,frame:ArenaPresentationFrame,phase:String){
        if (layout.perspective != null) return // Embedded lighting and effects belong to the scene pass.
        val lightingAlpha=when(frame.lighting.lowercase()){"dark","night"->56;"bright","day"->10;"dramatic"->34;else->18}
        if(lightingAlpha>0){
            val a=layout.project(-.7f,-.7f) ?: return;val b=layout.project(layout.columns-.3f,layout.rows-.3f) ?: return
            val left=minOf(a.x,b.x).roundToInt();val right=maxOf(a.x,b.x).roundToInt()
            val top=minOf(a.y,b.y).roundToInt();val bottom=maxOf(a.y,b.y).roundToInt()
            if(right>left&&bottom>top)gui.fill(left,top,right,bottom,(lightingAlpha shl 24))
        }
        if(frame.ambientVfx.isNotBlank()){
            val hash=frame.ambientVfx.hashCode()
            repeat(6){i->
                val x=Math.floorMod(hash+i*31,layout.columns.coerceAtLeast(1)).toFloat()
                val y=Math.floorMod(hash/31+i*17,layout.rows.coerceAtLeast(1)).toFloat()
                val p=layout.project(x,y) ?: return@repeat;gui.fill(p.x.roundToInt()-1,p.y.roundToInt()-1,p.x.roundToInt()+2,p.y.roundToInt()+2,0x88FFFFFF.toInt())
            }
        }
        if(frame.semanticVfx.isNotBlank()){
            val center=layout.project((layout.columns-1)/2f,(layout.rows-1)/2f) ?: return
            val pulse=if(phase.equals("combat",true))10 else 14
            drawDiamondOutline(gui,center.x.roundToInt(),center.y.roundToInt(),pulse*2,pulse,0xCCFFFFFF.toInt())
        }
        frame.lootAnchors.forEachIndexed{index,anchor->
            val p=layout.project(anchor.x,anchor.y,anchor.z) ?: return@forEachIndexed
            val size=if(index%2==0)5 else 4
            fillDiamond(gui,p.x.roundToInt(),p.y.roundToInt(),size*2,size,0xB8E2BE62.toInt())
        }
    }

    fun interactionRect(layout:PokemonSceneLayout,region:ArenaInteractionRegion):io.github.aristheg201.svhub.ui.UiRect?{
        val points=listOfNotNull(
            layout.project(region.bounds.minX,region.bounds.minY),layout.project(region.bounds.maxX,region.bounds.minY),
            layout.project(region.bounds.minX,region.bounds.maxY),layout.project(region.bounds.maxX,region.bounds.maxY)
        )
        if (points.size != 4) return null
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
        val p = layout.center(index) ?: return
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
        val p = layout.center(index) ?: return
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
        points.filterNotNull().forEach { p ->
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
        val p = layout.center(index) ?: return
        if(layout.perspective != null) {
            val surface=layout.boardSurface
            val x=(surface.origin.x+(index%layout.columns+.5)*surface.width/layout.columns).toFloat()
            val y=(surface.origin.y+(index/layout.columns+.5)*surface.height/layout.rows).toFloat()
            val margin=if(strong) .44f else .36f
            val dx=(margin*surface.width/layout.columns).toFloat();val dy=(margin*surface.height/layout.rows).toFloat()
            fillQuad(gui,quad(layout,x-dx,y-dy,x+dx,y+dy,surface.origin.z.toFloat()),withAlpha(color,if(strong)185 else 100))
            return
        }
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
        val point = layout.center(index) ?: return
        val size = min(11, max(6, min(layout.tileWidth, layout.tileHeight * 2) / 2))
        renderItem(gui, item, point.x.roundToInt(), point.y.roundToInt() - max(1, layout.tileHeight / 8), size, 10.0)
    }

    fun renderProps(
        gui: GuiGraphics,
        layout: PokemonSceneLayout,
        theme: MinecraftArenaDefinition,
        seed: String
    ) {
        if(layout.perspective != null) { EmbeddedSceneRenderer.render(gui,layout,theme); return }
        compiledScene(layout,theme).scene.nodes.asSequence().filter{it.visible}.sortedBy{it.transform.position.y}.forEach{node->
            if(node is SceneMeshNode){renderMesh(gui,layout,node);return@forEach}
            val point=layout.project(node.transform.position.x.toFloat(),node.transform.position.y.toFloat(),node.transform.position.z.toFloat()) ?: return@forEach
            val pixels=(min(28,max(12,layout.tileWidth))*node.transform.scale.x).roundToInt().coerceIn(8,38)
            val asset=when(node){is SceneBlockModelNode->node.blockId;is SceneItemModelNode->node.itemId;else->return@forEach}
            resolveStack(asset)?.let{renderStack(gui,it,point.x.roundToInt(),point.y.roundToInt()-pixels/3,pixels,30.0+point.y/8.0)}
        }
    }

    private fun renderMesh(gui:GuiGraphics,layout:PokemonSceneLayout,node:SceneMeshNode){
        val projected=node.corners().map { layout.project(it.x.toFloat(),it.y.toFloat(),it.z.toFloat()) }
        val bottom=projected.take(4)
        val top=projected.drop(4)
        val color=when(node.material){"bench"->0xFF263F3B.toInt();else->0xFF263532.toInt()}
        for(i in bottom.indices)fillQuad(gui,listOf(bottom[i],bottom[(i+1)%4],top[(i+1)%4],top[i]),darken(color,.68f))
        fillQuad(gui,top,color)
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
            val p = layout.center(index) ?: return@repeat
            drawDiamondOutline(gui, p.x.roundToInt(), p.y.roundToInt(), layout.tileWidth, layout.tileHeight, color)
        }
    }

    private fun quad(layout: PokemonSceneLayout, x0: Float, y0: Float, x1: Float, y1: Float, z: Float = 0f): List<ScenePoint> =
        listOfNotNull(layout.project(x0, y0,z), layout.project(x1, y0,z), layout.project(x1, y1,z), layout.project(x0, y1,z)).takeIf { it.size == 4 }.orEmpty()

    private fun fillQuad(gui: GuiGraphics, projected: List<ScenePoint?>, color: Int) {
        if (projected.size != 4 || projected.any { it == null }) return
        val points=projected.filterNotNull()
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

    private fun drawLine(gui: GuiGraphics, a: ScenePoint?, b: ScenePoint?, color: Int, thickness: Int) {
        if (a == null || b == null) return
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
